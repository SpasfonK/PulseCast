package com.pulsecast.app.ui.screens.episodes

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.ui.components.KeywordFilterDialog
import com.pulsecast.app.ui.components.PulseCastSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val episodeDateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRENCH)

/**
 * Liste des épisodes d'un podcast : pochette et compteurs en en-tête, onglets
 * Non-lus/Lus, filtrage par mot-clé et actualisation du flux RSS. Si le flux
 * n'a jamais été téléchargé (import incomplet), l'écran propose — et tente
 * automatiquement — une récupération des épisodes.
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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshMessage by viewModel.refreshMessage.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            AsyncImage(
                model = podcast?.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = podcast?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${unplayed.size} non-lu(s) · ${played.size} lu(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !isRefreshing) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualiser le flux",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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

        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }

        refreshMessage?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::dismissRefreshMessage) {
                    Text("OK")
                }
            }
        }

        val episodesToShow = if (selectedTabIndex == 0) unplayed else played

        if (episodesToShow.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (selectedTabIndex == 0) {
                        "Aucun épisode non-lu pour l'instant."
                    } else {
                        "Aucun épisode lu pour l'instant."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (unplayed.isEmpty() && played.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Le flux n'a peut-être pas encore été téléchargé. " +
                            "Tente une actualisation pour récupérer les épisodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::refresh, enabled = !isRefreshing) {
                        Text("Actualiser le flux")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodesToShow, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onClick = { onPlayEpisode(episode, podcast?.title, podcast?.imageUrl) },
                        onTogglePlayed = { viewModel.togglePlayed(episode) }
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
private fun EpisodeRow(
    episode: EpisodeEntity,
    onClick: () -> Unit,
    onTogglePlayed: () -> Unit
) {
    PulseCastSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Lire",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = episodeMeta(episode)
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (episode.playbackPositionMs > 0 && episode.durationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    val fraction =
                        (episode.playbackPositionMs.toFloat() / episode.durationMs.toFloat())
                            .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            IconButton(onClick = onTogglePlayed) {
                Icon(
                    imageVector = if (episode.isPlayed) {
                        Icons.Filled.Refresh
                    } else {
                        Icons.Filled.Check
                    },
                    contentDescription = if (episode.isPlayed) {
                        "Marquer comme non lu"
                    } else {
                        "Marquer comme lu"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun episodeMeta(episode: EpisodeEntity): String {
    val parts = mutableListOf<String>()
    if (episode.pubDate > 0L) {
        parts += episodeDateFormat.format(Date(episode.pubDate))
    }
    if (episode.durationMs > 0L) {
        parts += formatDuration(episode.durationMs)
    }
    if (episode.isFavorite) {
        parts += "\u2605"
    }
    return parts.joinToString(" · ")
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "Durée inconnue"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "%dh%02d".format(hours, minutes) else "%d min".format(minutes)
}