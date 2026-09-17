package com.pulsecast.app.ui.screens.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsecast.app.playback.PlaybackConstants
import com.pulsecast.app.playback.PlaybackUiState

/**
 * Grand lecteur plein écran : illustration haute résolution, titre en
 * texte défilant (marquee) si trop long, scrubber, 5 sauts temporels dédiés,
 * lecture/pause et réglage fin de la vitesse.
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCollapse) {
                Text("Réduire", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = uiState.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = uiState.title.ifBlank { "Aucun épisode" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
            )

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

            Spacer(Modifier.height(24.dp))

            PlayerSeekBar(
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                onSeekTo = onSeekTo
            )

            Spacer(Modifier.height(16.dp))

            SkipButtonsRow(onSeekCommand = onSeekCommand)

            Spacer(Modifier.height(16.dp))

            PlayPauseButton(
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                onClick = onPlayPause
            )

            Spacer(Modifier.height(20.dp))

            SpeedSelector(currentSpeed = uiState.speed, onSpeedChange = onSpeedChange)
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        )
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
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lecture",
                modifier = Modifier.size(36.dp)
            )
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
