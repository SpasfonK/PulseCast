package com.pulsecast.app.ui.screens.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsecast.app.playback.PlaybackConstants
import com.pulsecast.app.playback.PlaybackUiState
import com.pulsecast.app.theme.PulseCastTheme
import com.pulsecast.app.ui.components.PulseCastSurface
import com.pulsecast.app.ui.foldable.PlayerLayoutMode
import com.pulsecast.app.ui.foldable.rememberFoldingFeature
import com.pulsecast.app.ui.foldable.toPlayerLayoutMode

private val MAX_CONTENT_WIDTH = 480.dp

/**
 * Grand lecteur plein écran : illustration haute résolution encadrée par
 * [PulseCastSurface] (donc habillée selon le thème actif — ombre dure en
 * Néo-Brutaliste, halo néon en Synthwave, halo de l'accent dynamique en
 * OLED), fond flouté issu de la pochette en Verre Dépoli, titre en texte
 * défilant (marquee), scrubber, 5 sauts temporels dédiés, lecture/pause et
 * réglage fin de la vitesse.
 *
 * S'adapte aussi aux pliables book-style comme le Honor Magic V2 : en
 * posture "tabletop" (à moitié ouvert, charnière horizontale — l'appareil
 * posé comme un petit chevalet), l'illustration se place au-dessus de la
 * charnière et les contrôles en-dessous, sans rien dessiner sur la
 * charnière elle-même. Sur un appareil non pliable, ce mode n'est jamais
 * déclenché (voir `ui/foldable/FoldingState.kt`).
 *
 * N'est pas un écran de la pile de navigation : il est rendu par
 * PulseCastApp comme contenu "déplié" du bottom sheet racine (voir
 * PHASE4_NOTES.md, Gauntlet Check 4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    uiState: PlaybackUiState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekCommand: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = PulseCastTheme.extras
    val foldingFeature = rememberFoldingFeature()
    val layoutMode = foldingFeature.toPlayerLayoutMode()

    Box(modifier = modifier.fillMaxSize()) {
        if (extras.useGlassBackground && !uiState.artworkUri.isNullOrBlank()) {
            GlassBackdrop(artworkUri = uiState.artworkUri, blurRadius = extras.glassBlurRadius)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        when (layoutMode) {
            is PlayerLayoutMode.Tabletop -> {
                val density = LocalDensity.current
                val hingeTopDp = with(density) { layoutMode.hingeTopPx.toDp() }
                val hingeGapDp = with(density) {
                    (layoutMode.hingeBottomPx - layoutMode.hingeTopPx).coerceAtLeast(0).toDp()
                }
                Column(Modifier.fillMaxSize()) {
                    PlayerTopPane(
                        uiState = uiState,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hingeTopDp)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.fillMaxWidth().height(hingeGapDp))
                    PlayerBottomPane(
                        uiState = uiState,
                        onSeekCommand = onSeekCommand,
                        onPlayPause = onPlayPause,
                        onSpeedChange = onSpeedChange,
                        onSeekTo = onSeekTo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }

            PlayerLayoutMode.Normal -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = MAX_CONTENT_WIDTH)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlayerTopPane(uiState = uiState, compact = false, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(24.dp))
                            PlayerBottomPane(
                                uiState = uiState,
                                onSeekCommand = onSeekCommand,
                                onPlayPause = onPlayPause,
                                onSpeedChange = onSpeedChange,
                                onSeekTo = onSeekTo,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }
        }

        TextButton(
            onClick = onCollapse,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
        ) {
            Text("Réduire", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/**
 * Fond du grand lecteur pour le thème Verre Dépoli : la pochette en cours,
 * floutée et assombrie, occupe tout l'écran derrière le contenu — c'est ce
 * que le thème promettait depuis la Phase 3 sans jamais l'afficher
 * réellement (les surfaces translucides n'avaient rien de flouté derrière
 * elles). Coil met en cache l'image : ce second chargement de la même URL
 * ne déclenche pas de second appel réseau.
 */
@Composable
private fun GlassBackdrop(artworkUri: String?, blurRadius: Dp) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(blurRadius),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerTopPane(uiState: PlaybackUiState, compact: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ArtworkFrame(
            artworkUri = uiState.artworkUri,
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.55f else 1f)
                .aspectRatio(1f)
        )

        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

        Text(
            text = uiState.title.ifBlank { "Aucun épisode" },
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee()
        )

        if (!compact) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = uiState.podcastTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (uiState.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Illustration encadrée par [PulseCastSurface] (ombre dure / halo néon /
 * micro-bordure selon le thème), avec en plus un halo dans la couleur
 * d'accent — pour le thème OLED, cet accent est justement celui extrait
 * dynamiquement de cette même pochette (Palette API, Phase 3/4) : l'effet
 * boucle visuellement sur sa propre source.
 */
@Composable
private fun ArtworkFrame(artworkUri: String?, modifier: Modifier = Modifier) {
    val extras = PulseCastTheme.extras
    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        if (extras.useDynamicAccentFromArtwork) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(24.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(colorScheme.primary.copy(alpha = 0.35f))
            )
        }
        PulseCastSurface(
            modifier = Modifier.matchParentSize(),
            shape = MaterialTheme.shapes.large
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun PlayerBottomPane(
    uiState: PlaybackUiState,
    onSeekCommand: (String) -> Unit,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PlayerSeekBar(positionMs = uiState.positionMs, durationMs = uiState.durationMs, onSeekTo = onSeekTo)
        Spacer(Modifier.height(16.dp))
        SkipButtonsRow(onSeekCommand = onSeekCommand)
        Spacer(Modifier.height(16.dp))
        PlayPauseButton(isPlaying = uiState.isPlaying, isBuffering = uiState.isBuffering, onClick = onPlayPause)
        Spacer(Modifier.height(20.dp))
        SpeedSelector(currentSpeed = uiState.speed, onSpeedChange = onSpeedChange)
    }
}

@Composable
private fun PlayerSeekBar(positionMs: Long, durationMs: Long, onSeekTo: (Long) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }

    val displayedPosition = if (isDragging) dragPositionMs else positionMs
    val fraction = if (durationMs > 0) {
        (displayedPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { newFraction ->
                isDragging = true
                dragPositionMs = (newFraction * durationMs).toLong()
            },
            onValueChangeFinished = {
                onSeekTo(dragPositionMs)
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTime(displayedPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SkipButtonsRow(onSeekCommand: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkipButton("-30s") { onSeekCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_BACK_30) }
        SkipButton("-10s") { onSeekCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_BACK_10) }
        SkipButton("+20s") { onSeekCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_20) }
        SkipButton("+40s") { onSeekCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_40) }
        SkipButton("+60s") { onSeekCommand(PlaybackConstants.CUSTOM_COMMAND_SEEK_FORWARD_60) }
    }
}

@Composable
private fun SkipButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, isBuffering: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Crossfade(targetState = isBuffering to isPlaying, label = "fullPlayerPlayPause") { (buffering, playing) ->
            if (buffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Lecture",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedSelector(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    var dragSpeed by remember { mutableStateOf(currentSpeed) }
    val displayedSpeed = if (isDragging) dragSpeed else currentSpeed

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Vitesse : %.1fx".format(displayedSpeed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Slider(
            value = displayedSpeed,
            onValueChange = {
                isDragging = true
                dragSpeed = it
            },
            onValueChangeFinished = {
                onSpeedChange(dragSpeed)
                isDragging = false
            },
            valueRange = PlaybackConstants.MIN_SPEED..PlaybackConstants.MAX_SPEED,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
