package com.pulsecast.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pulsecast.app.MainActivity
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.local.dao.EpisodeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service de lecture audio en arrière-plan, basé sur Media3.
 *
 * Encapsuler un [ExoPlayer] dans une [MediaSession] fournit automatiquement,
 * sans code supplémentaire de notre part :
 * - la notification de lecture en cours (via le `DefaultMediaNotificationProvider`
 *   fourni par Media3) et son cycle de vie en Foreground Service ;
 * - les contrôles sur écran de verrouillage, Bluetooth et Android Auto, via
 *   le protocole standard MediaSession/MediaController.
 *
 * `onTaskRemoved` n'est volontairement pas surchargé : le comportement par
 * défaut de `MediaSessionService` arrête déjà le service si la lecture
 * n'est pas active au moment où l'utilisateur retire l'app des tâches
 * récentes, et le laisse tourner en Foreground Service sinon — exactement
 * le comportement attendu d'un lecteur de podcasts (à l'image de
 * Spotify/Pocket Casts), et ce qui le protège du tueur de tâches Android.
 * Dans les deux cas, `onDestroy()` ci-dessous garantit la sauvegarde finale
 * de la position de lecture.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var episodeDao: EpisodeDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        episodeDao = (application as PulseCastApplication).database.episodeDao()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            // handleAudioFocus = true : ExoPlayer gère seul la perte de
            // focus audio (pause sur appel entrant, "ducking" - baisse de
            // volume temporaire - sur une alerte de guidage GPS), sans
            // AudioFocusRequest manuel de notre part.
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Valeurs par défaut exposées aux intégrations système standard
            // (Android Auto, Assistant, Bluetooth, Wear OS). Les 5 boutons
            // de saut dédiés (-10s/-30s/+20s/+40s/+60s) demandés dans le
            // cahier des charges sont eux exposés en SessionCommand
            // personnalisées ci-dessous, pour l'UI de l'application.
            .setSeekBackIncrementMs(PlaybackConstants.SEEK_BACK_10_MS)
            .setSeekForwardIncrementMs(PlaybackConstants.SEEK_FORWARD_20_MS)
            .build()

        exoPlayer.addListener(playerListener)

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setCallback(PlaybackSessionCallback())
            .setSessionActivity(sessionActivityIntent)
            .build()
        // Tapoter la notification ou l'écran de verrouillage ouvre
        // désormais MainActivity (résolu ici : point laissé en suspens en
        // Phase 2, faute d'Activity existante à l'époque).
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        stopProgressTracking()
        persistCurrentPosition()
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // --- Sauts temporels et vitesse ---

    private fun seekBy(deltaMs: Long) {
        val player = mediaSession?.player ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
    }

    private fun applyPlaybackSpeed(requestedSpeed: Float) {
        val player = mediaSession?.player ?: return
        val clamped = requestedSpeed.coerceIn(PlaybackConstants.MIN_SPEED, PlaybackConstants.MAX_SPEED)
        // Pitch fixé à 1.0 quelle que soit la vitesse demandée : c'est ce
        // qui garantit la préservation de la hauteur de la voix (pas de
        // voix "suraiguë" en accéléré, ni caverneuse en ralenti).
        player.playbackParameters = PlaybackParameters(clamped, 1.0f)
    }

    // --- Sauvegarde périodique de la position (toutes les 5 s en lecture) ---

    private fun currentEpisodeId(): Long? =
        mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()

    private fun persistCurrentPosition() {
        val player = mediaSession?.player ?: return
        val episodeId = currentEpisodeId() ?: return
        val position = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        serviceScope.launch(Dispatchers.IO) {
            episodeDao.updatePlaybackPosition(episodeId, position, duration)
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(PlaybackConstants.POSITION_SAVE_INTERVAL_MS)
                persistCurrentPosition()
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startProgressTracking()
            } else {
                stopProgressTracking()
                // On fige la position exacte au moment de la pause plutôt
                // que d'attendre le prochain tick (qui n'aura pas lieu).
                persistCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val episodeId = currentEpisodeId()
                val duration = mediaSession?.player?.duration
                    ?.takeIf { it != C.TIME_UNSET && it > 0 }
                if (episodeId != null && duration != null) {
                    // Fin de piste : on force position = durée pour
                    // garantir isPlayed = true côté DAO, sans dépendre du
                    // timing du prochain tick périodique.
                    serviceScope.launch(Dispatchers.IO) {
                        episodeDao.updatePlaybackPosition(episodeId, duration, duration)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Pas de UI à ce stade (Phase 2) pour remonter l'erreur à
            // l'utilisateur : elle sera exposée via le MediaController en
            // Phase 4. On journalise pour ne pas échouer silencieusement.
            Log.e(TAG, "Erreur de lecture : ${error.errorCodeName}", error)
        }
    }

    // --- Callback de session : commandes personnalisées ---

    private inner class PlaybackSessionCallback : MediaSession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_BACK_10, Bundle.EMPTY))
                .add(SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_BACK_30, Bundle.EMPTY))
                .add(SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_20, Bundle.EMPTY))
                .add(SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_40, Bundle.EMPTY))
                .add(SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_60, Bundle.EMPTY))
                .add(SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SET_SPEED, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackConstants.CUSTOM_COMMAND_SEEK_BACK_10 ->
                    seekBy(-PlaybackConstants.SEEK_BACK_10_MS)
                PlaybackConstants.CUSTOM_COMMAND_SEEK_BACK_30 ->
                    seekBy(-PlaybackConstants.SEEK_BACK_30_MS)
                PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_20 ->
                    seekBy(PlaybackConstants.SEEK_FORWARD_20_MS)
                PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_40 ->
                    seekBy(PlaybackConstants.SEEK_FORWARD_40_MS)
                PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_60 ->
                    seekBy(PlaybackConstants.SEEK_FORWARD_60_MS)
                PlaybackConstants.CUSTOM_COMMAND_SET_SPEED ->
                    applyPlaybackSpeed(args.getFloat(PlaybackConstants.EXTRA_SPEED, 1.0f))
                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
    }
}
