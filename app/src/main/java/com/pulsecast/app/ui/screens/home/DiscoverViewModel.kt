package com.pulsecast.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.catalog.CatalogPodcast
import com.pulsecast.app.data.catalog.PodcastCatalogApi
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.local.toEntity
import com.pulsecast.app.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Une rangée thématique de la page d'accueil, chargée indépendamment. */
data class DiscoverSection(
    val title: String,
    val podcasts: List<CatalogPodcast> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed interface CatalogSearchState {
    data object Idle : CatalogSearchState
    data object Loading : CatalogSearchState
    data class Results(val podcasts: List<CatalogPodcast>) : CatalogSearchState
    data class Error(val message: String) : CatalogSearchState
}

/**
 * Alimente la page d'accueil : classement "À la une", rangées thématiques,
 * abonnements existants, recherche et abonnement en un tap.
 *
 * Chaque rangée se charge indépendamment : un thème qui échoue (réseau
 * capricieux) n'empêche pas les autres de s'afficher.
 */
class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as PulseCastApplication).database
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()
    private val catalog = PodcastCatalogApi()

    private val sectionThemes = listOf(
        "Actualités" to "actualité",
        "Technologie" to "technologie",
        "Humour" to "humour",
        "Culture & société" to "société",
        "Histoire" to "histoire",
        "Science" to "science",
        "Sport" to "sport"
    )

    private val _featured = MutableStateFlow(DiscoverSection(title = "À la une"))
    val featured: StateFlow<DiscoverSection> = _featured.asStateFlow()

    private val _sections = MutableStateFlow(sectionThemes.map { DiscoverSection(title = it.first) })
    val sections: StateFlow<List<DiscoverSection>> = _sections.asStateFlow()

    val subscriptions: StateFlow<List<PodcastEntity>> = podcastDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _subscribedIds = MutableStateFlow<Set<Long>>(emptySet())
    val subscribedIds: StateFlow<Set<Long>> = _subscribedIds.asStateFlow()

    private val _subscribingIds = MutableStateFlow<Set<Long>>(emptySet())
    val subscribingIds: StateFlow<Set<Long>> = _subscribingIds.asStateFlow()

    private val _subscribeMessage = MutableStateFlow<String?>(null)
    val subscribeMessage: StateFlow<String?> = _subscribeMessage.asStateFlow()

    private val _searchState = MutableStateFlow<CatalogSearchState>(CatalogSearchState.Idle)
    val searchState: StateFlow<CatalogSearchState> = _searchState.asStateFlow()

    private var hasLoadedOnce = false

    fun loadIfNeeded() {
        if (hasLoadedOnce) return
        hasLoadedOnce = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _featured.value = DiscoverSection(title = "À la une", isLoading = true)
            _featured.value = try {
                DiscoverSection(
                    title = "À la une",
                    podcasts = catalog.topPodcasts(limit = 20),
                    isLoading = false
                )
            } catch (e: Exception) {
                DiscoverSection(
                    title = "À la une",
                    isLoading = false,
                    error = e.message ?: "Connexion impossible"
                )
            }
        }

        viewModelScope.launch {
            sectionThemes.forEachIndexed { index, theme ->
                val result = try {
                    DiscoverSection(
                        title = theme.first,
                        podcasts = catalog.search(theme.second, limit = 15),
                        isLoading = false
                    )
                } catch (e: Exception) {
                    DiscoverSection(
                        title = theme.first,
                        isLoading = false,
                        error = e.message ?: "Connexion impossible"
                    )
                }
                replaceSection(index, result)
            }
        }
    }

    private fun replaceSection(index: Int, section: DiscoverSection) {
        val updated = _sections.value.toMutableList()
        if (index in updated.indices) {
            updated[index] = section
        }
        _sections.value = updated
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchState.value = CatalogSearchState.Idle
            return
        }
        viewModelScope.launch {
            _searchState.value = CatalogSearchState.Loading
            _searchState.value = try {
                CatalogSearchState.Results(catalog.search(query, limit = 25))
            } catch (e: Exception) {
                CatalogSearchState.Error("Recherche impossible : ${e.message ?: "erreur inconnue"}")
            }
        }
    }

    fun clearSearch() {
        _searchState.value = CatalogSearchState.Idle
    }

    fun clearMessage() {
        _subscribeMessage.value = null
    }

    /**
     * Abonne l'utilisateur : résout le flux RSS si nécessaire, le parse puis
     * persiste le podcast et ses épisodes. Les messages d'état sont exposés
     * via [subscribeMessage] pour être affichés dans la fiche du podcast.
     */
    fun subscribe(podcast: CatalogPodcast) {
        viewModelScope.launch {
            _subscribingIds.value = _subscribingIds.value + podcast.id
            _subscribeMessage.value = null
            try {
                val feedUrl = podcast.feedUrl ?: catalog.resolveFeedUrl(podcast.id)
                    ?: error("Flux RSS introuvable pour ce podcast")

                val alreadySubscribed = withContext(Dispatchers.IO) {
                    podcastDao.findByFeedUrl(feedUrl)
                }
                if (alreadySubscribed != null) {
                    _subscribedIds.value = _subscribedIds.value + podcast.id
                    _subscribeMessage.value = "Tu es déjà abonné à ce podcast."
                    return@launch
                }

                val parsedFeed = withContext(Dispatchers.IO) {
                    openFeedStream(feedUrl).use { input -> RssParser().parse(input) }
                }

                withContext(Dispatchers.IO) {
                    val podcastId = podcastDao.insert(
                        PodcastEntity(
                            feedUrl = feedUrl,
                            title = parsedFeed.title.ifBlank { podcast.title },
                            imageUrl = parsedFeed.imageUrl ?: podcast.artworkUrl,
                            description = parsedFeed.description
                        )
                    )
                    if (podcastId > 0) {
                        episodeDao.insertAll(parsedFeed.episodes.map { it.toEntity(podcastId) })
                    }
                }

                _subscribedIds.value = _subscribedIds.value + podcast.id
                _subscribeMessage.value = "Abonné ! ${parsedFeed.episodes.size} épisodes importés."
            } catch (e: Exception) {
                _subscribeMessage.value = "Échec de l'abonnement : ${e.message ?: "erreur inconnue"}"
            } finally {
                _subscribingIds.value = _subscribingIds.value - podcast.id
            }
        }
    }

    private fun openFeedStream(feedUrl: String) =
        (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PulseCast/1.0 (Android)")
        }.inputStream
}