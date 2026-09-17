package com.pulsecast.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsecast.app.playback.PlaybackUiState

private val MAX_CONTENT_WIDTH = 680.dp

/**
 * Mini-lecteur persistant : contenu affiché dans l'état replié (peek) du
 * bottom sheet racine. Tapoter n'importe où (hors bouton lecture/pause)
 * déclenche [onExpand] pour ouvrir le grand lecteur immersif.
 *
 * Une fine ligne de progression court en haut de la barre (comme la
 * notification système Android) : un repère visuel discret mais toujours
 * visible, même sans ouvrir le grand lecteur.
 *
 * [barHeight] et [navBarInset] sont fournis par l'appelant (qui connaît déjà
 * la hauteur de peek totale du bottom sheet) plutôt que recalculés ici,
 * pour que la somme des deux corresponde exactement à l'espace réellement
 * réservé — voir PulseCastApp.kt.
 */
@Composable
fun MiniPlayer(
    uiState: PlaybackUiState,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    barHeight: Dp,
    navBarInset: Dp,
    modifier: Modifier = Modifier
) {
    val progressFraction = if (uiState.durationMs > 0) {
        (uiState.positionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onExpand)
    ) {
        LinearProgressIndicator(
            progress = progressFraction,
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH)
                .align(Alignment.CenterHorizontally)
                .height(barHeight - 2.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = uiState.artworkUri,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = uiState.title.ifBlank { "Aucune lecture en cours" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (uiState.podcastTitle.isNotBlank()) {
                    Text(
                        text = uiState.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onPlayPause) {
                Crossfade(targetState = uiState.isBuffering to uiState.isPlaying, label = "miniPlayerPlayPause") { (buffering, playing) ->
                    if (buffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Lecture",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        // Espace réservé pour la barre de navigation système (edge-to-edge) :
        // vide intentionnellement, pas de contenu interactif ici.
        Spacer(modifier = Modifier.fillMaxWidth().height(navBarInset))
    }
}
