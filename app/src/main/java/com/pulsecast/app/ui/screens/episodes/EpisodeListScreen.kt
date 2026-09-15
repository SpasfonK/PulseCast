package com.pulsecast.app.ui.screens.episodes

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.ui.components.KeywordFilterDialog
import com.pulsecast.app.ui.components.PulseCastSurface

/**
 * Liste des épisodes d'un podcast, avec onglets Non-lus/Lus et un dialogue
 * de filtrage par mot-clé (bouton en haut à droite).
 */
@Composable
fun EpisodeListScreen(
    podcastId: Long,
    onPlayEpisode: (EpisodeEntity, String?, String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: EpisodeListViewModel = viewModel(
        factory = remember(podcastId) {
            EpisodeListViewModelFactory(context.applicationContext as Application, podcastId)
        }
    )

    val podcast by viewModel.podcast.collectAsState()
    val unplayed by viewModel.unplayedEpisodes.collectAsState()
    val played by viewModel.playedEpisodes.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = podcast?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            TextButton(onClick = { showFilterDialog = true }) {
                Text("Filtre")
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Non-lus (${unplayed.size})") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Lus (${played.size})") }
            )
        }

        val episodesToShow = if (selectedTabIndex == 0) unplayed else played

        if (episodesToShow.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedTabIndex == 0) "Aucun épisode non-lu." else "Aucun épisode lu pour l'instant.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodesToShow, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onClick = { onPlayEpisode(episode, podcast?.title, podcast?.imageUrl) }
                    )
                }
            }
        }
    }

    if (showFilterDialog) {
        KeywordFilterDialog(
            currentPattern = podcast?.titleFilterPattern,
            onDismiss = { showFilterDialog = false },
            onConfirm = { pattern ->
                viewModel.updateFilter(pattern)
                showFilterDialog = false
            }
        )
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeEntity, onClick: () -> Unit) {
    PulseCastSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatDuration(episode.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (episode.isFavorite) {
                    Text(
                        text = "\u2605",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (episode.playbackPositionMs > 0 && episode.durationMs > 0) {
                Spacer(Modifier.height(6.dp))
                val fraction = (episode.playbackPositionMs.toFloat() / episode.durationMs.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "Durée inconnue"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "%dh%02d".format(hours, minutes) else "%d min".format(minutes)
}
