package com.pulsecast.app.ui.screens.library

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.ui.components.PulseCastSurface

/**
 * Écran d'accueil de l'application : abonnements existants + ajout d'un
 * nouveau flux RSS par URL. Tapoter un podcast ouvre sa liste d'épisodes.
 */
@Composable
fun PodcastLibraryScreen(
    onPodcastClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: PodcastLibraryViewModel = viewModel()
    val podcasts by viewModel.podcasts.collectAsState()
    val importState by viewModel.importState.collectAsState()
    var feedUrlInput by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Podcasts",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))

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
            Spacer(Modifier.height(8.dp))
            Text(
                text = currentImportState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        if (podcasts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aucun podcast pour l'instant.\nColle l'URL d'un flux RSS ci-dessus pour t'abonner.",
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
