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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.parser.OpmlParser
import com.pulsecast.app.ui.components.PulseCastSurface

/**
 * Bibliothèque : abonnements existants, ajout d'un flux RSS par URL et import
 * d'un fichier OPML exporté depuis une autre application de podcasts.
 * Tapoter un podcast ouvre sa liste d'épisodes.
 */
@Composable
fun PodcastLibraryScreen(
    onPodcastClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: PodcastLibraryViewModel = viewModel()
    val podcasts by viewModel.podcasts.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val opmlProgress by viewModel.opmlProgress.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var feedUrlInput by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    val opmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                viewModel.importOpmlFeeds(OpmlParser().parse(stream))
            }
        }
    }

    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        uri?.let { viewModel.exportOpml(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Bibliothèque",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)
        )
        Text(
            text = "Tes abonnements et tes imports",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp)
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))

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
            when (currentImportState) {
                is ImportState.Error -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentImportState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is ImportState.Success -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentImportState.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> Unit
            }

            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = { opmlLauncher.launch(arrayOf("*/*")) },
                enabled = !opmlProgress.isActive
            ) {
                Text("Importer OPML (Podcast Addict…)")
            }

            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { opmlExportLauncher.launch("pulsecast-abonnements.opml") },
                enabled = podcasts.isNotEmpty() && exportState !is ImportState.Loading
            ) {
                if (exportState is ImportState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Text("Exporter OPML (${podcasts.size})")
                }
            }

            val currentExportState = exportState
            when (currentExportState) {
                is ImportState.Error -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentExportState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is ImportState.Success -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentExportState.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> Unit
            }

            if (opmlProgress.isActive || opmlProgress.errors.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OpmlProgressCard(
                    progress = opmlProgress,
                    onDismiss = { viewModel.dismissOpmlProgress() }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (podcasts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aucun abonnement pour l'instant.\n" +
                            "Abonne-toi depuis l'accueil, colle une URL RSS ci-dessus,\n" +
                            "ou importe un fichier OPML.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
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
                    text = if (progress.isActive) {
                        "Importation OPML en cours…"
                    } else {
                        "Importation OPML terminée"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!progress.isActive) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (progress.total > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.completed.toFloat() / progress.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${progress.completed}/${progress.total} — ${progress.currentTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (!progress.isActive && progress.importedEpisodes > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${progress.importedEpisodes} épisode(s) importé(s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (progress.errors.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                progress.errors.forEach { error ->
                    Text(
                        text = "⚠ $error",
                        style = MaterialTheme.typography.labelSmall,
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
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (!podcast.titleFilterPattern.isNullOrBlank()) {
                    Text(
                        text = "Filtre : \u201c${podcast.titleFilterPattern}\u201d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
