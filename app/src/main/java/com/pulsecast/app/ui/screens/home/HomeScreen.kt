package com.pulsecast.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pulsecast.app.data.catalog.CatalogPodcast
import com.pulsecast.app.theme.AppTheme
import com.pulsecast.app.theme.ThemeViewModel
import com.pulsecast.app.ui.components.PodcastArtworkCard
import com.pulsecast.app.ui.components.PulseCastSurface
import com.pulsecast.app.ui.components.ThemeSelector

/**
 * Page d'accueil : découverte du catalogue (classement "À la une", rangées
 * thématiques), recherche et accès rapide aux abonnements.
 *
 * Toutes les cartes sont construites à partir des jetons du thème actif
 * (formes, couleurs, bordures) : changer de style visuel via l'icône palette
 * transforme immédiatement l'ensemble de la page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenEpisodes: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DiscoverViewModel = viewModel()
    val themeViewModel: ThemeViewModel = viewModel()

    val featured by viewModel.featured.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val subscriptionItems by viewModel.subscriptionItems.collectAsState()
    val subscribedIds by viewModel.subscribedIds.collectAsState()
    val subscribingIds by viewModel.subscribingIds.collectAsState()
    val subscribeMessage by viewModel.subscribeMessage.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()

    val subscribedFeedUrls = remember(subscriptionItems) {
        subscriptionItems.map { it.podcast.feedUrl }.toSet()
    }

    var query by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var showStylePicker by rememberSaveable { mutableStateOf(false) }
    var selectedPodcast by remember { mutableStateOf<CatalogPodcast?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Column(modifier = modifier.fillMaxSize()) {
        HomeHeader(
            onToggleStylePicker = { showStylePicker = !showStylePicker }
        )

        if (showStylePicker) {
            StylePickerSection(
                selectedTheme = selectedTheme,
                onThemeSelected = themeViewModel::selectTheme
            )
        }

        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.search(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Rechercher un podcast…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                IconButton(onClick = {
                    isSearching = false
                    query = ""
                    viewModel.clearSearch()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Fermer la recherche")
                }
            }
        } else {
            SearchEntry(onClick = { isSearching = true })
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isSearching) {
                SearchResults(
                    state = searchState,
                    subscribedFeedUrls = subscribedFeedUrls,
                    subscribedIds = subscribedIds,
                    onSelect = { selectedPodcast = it }
                )
            } else {
                DiscoveryFeed(
                    featured = featured,
                    sections = sections,
                    subscriptionItems = subscriptionItems,
                    onOpenEpisodes = onOpenEpisodes,
                    onOpenLibrary = onOpenLibrary,
                    onRetry = viewModel::refresh,
                    onSelect = { selectedPodcast = it }
                )
            }
        }
    }

    selectedPodcast?.let { podcast ->
        PodcastDetailSheet(
            podcast = podcast,
            isSubscribed = isSubscribed(podcast, subscribedIds, subscribedFeedUrls),
            isSubscribing = podcast.id in subscribingIds,
            message = subscribeMessage,
            onSubscribe = { viewModel.subscribe(podcast) },
            onDismiss = {
                selectedPodcast = null
                viewModel.clearMessage()
            }
        )
    }
}

private fun isSubscribed(
    podcast: CatalogPodcast,
    subscribedIds: Set<Long>,
    subscribedFeedUrls: Set<String>
): Boolean {
    if (podcast.id in subscribedIds) return true
    val feedUrl = podcast.feedUrl
    return feedUrl != null && feedUrl in subscribedFeedUrls
}

@Composable
private fun HomeHeader(onToggleStylePicker: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PulseCast",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tes podcasts, sans publicité",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleStylePicker) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = "Changer de style visuel",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Sélecteur de style intégré directement dans la page (et non dans une
 * feuille modale) : le choix s'affiche et s'applique dans la composition
 * normale, sans dépendre d'une fenêtre popup.
 */
@Composable
private fun StylePickerSection(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    PulseCastSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Style visuel",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Appliqué instantanément à toute l'application.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ThemeSelector(
                selectedTheme = selectedTheme,
                onThemeSelected = onThemeSelected
            )
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    PulseCastSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Rechercher un podcast…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoveryFeed(
    featured: DiscoverSection,
    sections: List<DiscoverSection>,
    subscriptionItems: List<SubscriptionItem>,
    onOpenEpisodes: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (CatalogPodcast) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (subscriptionItems.isNotEmpty()) {
            item(key = "subscriptions") {
                SubscriptionsSection(
                    items = subscriptionItems,
                    onOpenEpisodes = onOpenEpisodes,
                    onOpenLibrary = onOpenLibrary
                )
            }
        } else {
            item(key = "subscriptions_empty") {
                EmptySubscriptionsCard(onOpenLibrary = onOpenLibrary)
            }
        }

        item(key = "featured") {
            Column {
                SectionTitle("À la une")
                Spacer(Modifier.height(10.dp))
                val featuredError = featured.error
                when {
                    featured.isLoading -> LoadingRow()
                    featuredError != null -> ErrorRow(featuredError, onRetry)
                    else -> LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        itemsIndexed(featured.podcasts, key = { _, item -> item.id }) { index, item ->
                            PodcastArtworkCard(
                                title = item.title,
                                author = item.author,
                                artworkUrl = item.artworkUrl,
                                rank = index + 1,
                                cardWidth = 168.dp,
                                onClick = { onSelect(item) }
                            )
                        }
                    }
                }
            }
        }

        sections.forEach { section ->
            item(key = "section_${section.title}") {
                Column {
                    SectionTitle(section.title)
                    Spacer(Modifier.height(10.dp))
                    val sectionError = section.error
                    when {
                        section.isLoading -> LoadingRow()
                        sectionError != null -> ErrorRow(sectionError, onRetry)
                        else -> LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp)
                        ) {
                            items(section.podcasts, key = { it.id }) { item ->
                                PodcastArtworkCard(
                                    title = item.title,
                                    author = item.author,
                                    artworkUrl = item.artworkUrl,
                                    cardWidth = 148.dp,
                                    onClick = { onSelect(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(148.dp, 190.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text("Réessayer") }
    }
}

@Composable
private fun SubscriptionsSection(
    items: List<SubscriptionItem>,
    onOpenEpisodes: (Long) -> Unit,
    onOpenLibrary: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mes abonnements",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${items.size} podcast(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenLibrary) { Text("Tout voir") }
        }
        Spacer(Modifier.height(8.dp))

        // Grille construite à la main (rangées de 3) plutôt qu'un
        // LazyVerticalGrid imbriqué : un défilement vertical dans un
        // défilement vertical est interdit par Compose.
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    SubscriptionGridCard(
                        item = item,
                        onClick = { onOpenEpisodes(item.podcast.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SubscriptionGridCard(
    item: SubscriptionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = item.podcast.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            if (item.unplayedCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${item.unplayedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.podcast.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptySubscriptionsCard(onOpenLibrary: () -> Unit) {
    PulseCastSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Aucun abonnement pour l'instant",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Abonne-toi depuis les rangées ci-dessous, ou importe tes abonnements " +
                    "Podcast Addict (fichier OPML) depuis la bibliothèque.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpenLibrary) { Text("Ouvrir la bibliothèque") }
        }
    }
}

@Composable
private fun SearchResults(
    state: CatalogSearchState,
    subscribedFeedUrls: Set<String>,
    subscribedIds: Set<Long>,
    onSelect: (CatalogPodcast) -> Unit
) {
    when (state) {
        is CatalogSearchState.Idle -> CenteredHint("Tape le nom d'un podcast pour le trouver.")
        is CatalogSearchState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is CatalogSearchState.Error -> CenteredHint(state.message, isError = true)
        is CatalogSearchState.Results -> {
            if (state.podcasts.isEmpty()) {
                CenteredHint("Aucun résultat pour cette recherche.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.podcasts, key = { it.id }) { podcast ->
                        SearchResultRow(
                            podcast = podcast,
                            isSubscribed = isSubscribed(podcast, subscribedIds, subscribedFeedUrls),
                            onClick = { onSelect(podcast) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(message: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun SearchResultRow(
    podcast: CatalogPodcast,
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    PulseCastSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = podcast.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (podcast.genre != null && !podcast.genre.isNullOrBlank()) {
                    Text(
                        text = podcast.genre.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = if (isSubscribed) "Abonné" else "Voir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastDetailSheet(
    podcast: CatalogPodcast,
    isSubscribed: Boolean,
    isSubscribing: Boolean,
    message: String?,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(190.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = podcast.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            podcast.genre?.let { genre ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onSubscribe,
                enabled = !isSubscribed && !isSubscribing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isSubscribed) "Déjà abonné" else "S'abonner")
                }
            }
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}