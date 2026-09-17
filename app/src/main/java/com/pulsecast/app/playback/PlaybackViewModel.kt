package com.pulsecast.app.playback

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.theme.PaletteAccentExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pont entre l'UI Compose et [PlaybackService], via un
 * [androidx.media3.session.MediaController]. Expose l'état de lecture sous
 * forme de [StateFlow] et centralise toutes les actions de contrôle
 * (lecture/pause, sauts temporels, vitesse, défilement).
 *
 * Détient aussi l'extraction de couleur dynamique ([dynamicAccent]) : elle
 * est calculée à chaque changement de pochette, indépendamment du thème
 * actif — c'est à l'appelant (racine de l'app) de décider si elle doit
 * réellement être appliquée (seul le thème OLED Deep Minimal l'utilise).
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _dynamicAccent = MutableStateFlow<Color?>(null)
    val dynamicAccent: StateFlow<Color?> = _dynamicAccent.asStateFlow()

    private var progressJob: Job? = null
    private var accentJob: Job? = null

    init {
        connect()
    }

    private fun connect() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        Futures.addCallback(
            future,
            object : FutureCallback<MediaController> {
                override fun onSuccess(result: MediaController) {
                    mediaController = result
                    result.addListener(playerListener)
                    syncStateFromController(result)
                }

                override fun onFailure(t: Throwable) {
                    _uiState.value = _uiState.value.copy(isConnected = false)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun syncStateFromController(controller: MediaController) {
        _uiState.value = _uiState.value.copy(
            isConnected = true,
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.takeIf { it > 0 } ?: 0L,
            speed = controller.playbackParameters.speed
        )
        controller.currentMediaItem?.let { updateFromMediaItem(it.mediaId, it.mediaMetadata) }
        if (controller.isPlaying) startProgressTicker()
    }

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            if (isPlaying) startProgressTicker() else stopProgressTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.value = _uiState.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateFromMediaItem(mediaController?.currentMediaItem?.mediaId, mediaMetadata)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _uiState.value = _uiState.value.copy(speed = playbackParameters.speed)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Lecture interrompue : ${error.errorCodeName}"
            )
        }
    }

    private fun updateFromMediaItem(mediaId: String?, metadata: MediaMetadata) {
        val artworkUri = metadata.artworkUri?.toString()
        _uiState.value = _uiState.value.copy(
            currentEpisodeId = mediaId?.toLongOrNull(),
            title = metadata.title?.toString() ?: "",
            podcastTitle = metadata.artist?.toString() ?: "",
            artworkUri = artworkUri,
            durationMs = mediaController?.duration?.takeIf { it > 0 } ?: _uiState.value.durationMs,
            errorMessage = null
        )
        updateDynamicAccent(artworkUri)
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _uiState.value = _uiState.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = controller.duration.takeIf { it > 0 } ?: _uiState.value.durationMs
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    // --- Actions exposées à l'UI ---

    fun playEpisode(episode: EpisodeEntity, podcastTitle: String?, artworkUri: String?) {
        val controller = mediaController ?: return
        val mediaItem = episode.toMediaItem(podcastTitle, artworkUri)
        // Reprend à la position sauvegardée (Phase 2) plutôt que de repartir de zéro.
        controller.setMediaItem(mediaItem, episode.playbackPositionMs.coerceAtLeast(0L))
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun sendSeekCommand(customCommandAction: String) {
        mediaController?.sendCustomCommand(SessionCommand(customCommandAction, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun setSpeed(speed: Float) {
        val args = Bundle().apply { putFloat(PlaybackConstants.EXTRA_SPEED, speed) }
        mediaController?.sendCustomCommand(
            SessionCommand(PlaybackConstants.CUSTOM_COMMAND_SET_SPEED, Bundle.EMPTY),
            args
        )
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    // --- Accent dynamique (Palette API sur la pochette en cours) ---

    private fun updateDynamicAccent(artworkUri: String?) {
        accentJob?.cancel()
        if (artworkUri.isNullOrBlank()) {
            _dynamicAccent.value = null
            return
        }
        accentJob = viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(artworkUri)
            _dynamicAccent.value = bitmap?.let { PaletteAccentExtractor.extractAccent(it) }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        val context = getApplication<Application>()
        val request = ImageRequest.Builder(context)
            .data(url)
            // Palette a besoin de lire les pixels : un bitmap matériel
            // (par défaut sur les appareils récents) ne le permet pas.
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val drawable = (result as? SuccessResult)?.drawable ?: return null
        return (drawable as? BitmapDrawable)?.bitmap
    }

    override fun onCleared() {
        stopProgressTicker()
        accentJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onCleared()
    }
}
