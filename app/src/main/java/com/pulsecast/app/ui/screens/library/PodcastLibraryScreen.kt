package com.pulsecast.app.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.parser.ItunesSearchResult
import com.pulsecast.app.data.parser.OpmlParser
import com.pulsecast.app.ui.components.PulseCastSurface

@Composable
fun PodcastLibraryScreen(
    onPodcastClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: PodcastLibraryViewModel = viewModel()
    val podcasts by viewModel.podcasts.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val subscribingIds by viewModel.subscribingIds.collectAsState()
    val subscribeErrors by viewModel.subscribeErrors.collectAsState()
    val opmlProgress by viewModel.opmlProgress.collectAsState()

    var feedUrlInput by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchMode by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current

    val opmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val feeds = OpmlParser().parse(stream)
                viewModel.importOpmlFeeds(feeds)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isSearchMode) {
            SearchTopBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it; viewModel.searchPodcasts(it) },
                onClose = { isSearchMode = false; searchQuery = ""; viewModel.clearSearch() }
            )
        } else {
            LibraryTopBar(
                onSearchClick = { isSearchMode = true }
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (!isSearchMode) {
                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = feedUrlInput,
                        onValueChange = { feedUrlInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("URL du flux RSS") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.addPodcast(feedUrlInput)
                            feedUrlInput = ""
                        },
                        enabled = importState !is ImportState.Loading && feedUrlInput.isNotBlank()
                    ) {
                        if (importState is ImportState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Ajouter")
                        }
                    }
                }

                val currentImportState = importState
                if (currentImportState is ImportState.Error) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = currentImportState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = typography.bodySmall
                    )
                }

                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = { opmlLauncher.launch(arrayOf("*/*")) },
                    enabled = !opmlProgress.isActive
                ) {
                    Text("Importer OPML")
                }

                if (opmlProgress.isActive || opmlProgress.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OpmlProgressCard(
                        progress = opmlProgress,
                        onDismiss = { viewModel.dismissOpmlProgress() }
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (podcasts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aucun podcast pour l'instant.\n" +
                                    "Utilise la recherche 🔍 ou colle une URL RSS ci-dessus,\n" +
                                    "ou importe un fichier OPML depuis Podcast Addict.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(podcasts, key = { it.id }) { podcast ->
                            PodcastRow(podcast = podcast, onClick = { onPodcastClick(podcast.id) })
                        }
                    }
                }
            } else {
                when (val state = searchState) {
                    is SearchState.Idle -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Tape le nom d'un podcast pour le trouver.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = typography.bodyMedium
                            )
                        }
                    }
                    is SearchState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is SearchState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is SearchState.Results -> {
                        if (state.results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Aucun résultat trouvé.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                            ) {
                                items(state.results, key = { it.trackId }) { result ->
                                    SearchResultRow(
                                        result = result,
                                        isSubscribing = result.trackId in subscribingIds,
                                        error = subscribeErrors[result.trackId],
                                        onSubscribe = { viewModel.subscribeFromSearch(result) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTopBar(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Podcasts",
            style = typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Filled.Search, contentDescription = "Rechercher")
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Rechercher un podcast…") },
            singleLine = true
        )
    }
}

@Composable
private fun SearchResultRow(
    result: ItunesSearchResult,
    isSubscribing: Boolean,
    error: String?,
    onSubscribe: () -> Unit
) {
    PulseCastSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = result.artworkUrl100,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = result.trackName,
                    style = typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.artistName,
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (result.primaryGenreName != null) {
                    Text(
                        text = result.primaryGenreName,
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2
                    )
                }
            }
            Button(
                onClick = onSubscribe,
                enabled = !isSubscribing
            ) {
                if (isSubscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("S'abonner")
                }
            }
        }
    }
}

@Composable
private fun OpmlProgressCard(
    progress: OpmlImportProgress,
    onDismiss: () -> Unit
) {
    PulseCastSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progress.isActive) "Importation OPML en cours…" else "Importation OPML terminée",
                    style = typography.titleSmall
                )
                if (!progress.isActive) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.completed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${progress.completed}/${progress.total} — ${progress.currentTitle}",
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress.errors.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                progress.errors.forEach { err ->
                    Text(
                        text = "⚠ $err",
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastRow(podcast: PodcastEntity, onClick: () -> Unit) {
    PulseCastSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = podcast.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (!podcast.titleFilterPattern.isNullOrBlank()) {
                    Text(
                        text = "Filtre : \"${podcast.titleFilterPattern}\"",
                        style = typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}