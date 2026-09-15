package com.pulsecast.app.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.local.toEntity
import com.pulsecast.app.data.parser.ItunesSearchApi
import com.pulsecast.app.data.parser.ItunesSearchResult
import com.pulsecast.app.data.parser.OpmlFeed
import com.pulsecast.app.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    data class Error(val message: String) : ImportState
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val results: List<ItunesSearchResult>) : SearchState
    data class Error(val message: String) : SearchState
}

data class OpmlImportProgress(
    val isActive: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentTitle: String = "",
    val errors: List<String> = emptyList()
)

class PodcastLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as PulseCastApplication).database
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()
    private val searchApi = ItunesSearchApi()

    val podcasts: StateFlow<List<PodcastEntity>> = podcastDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _subscribingIds = MutableStateFlow<Set<Long>>(emptySet())
    val subscribingIds: StateFlow<Set<Long>> = _subscribingIds.asStateFlow()

    private val _subscribeErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
    val subscribeErrors: StateFlow<Map<Long, String>> = _subscribeErrors.asStateFlow()

    private val _opmlProgress = MutableStateFlow(OpmlImportProgress())
    val opmlProgress: StateFlow<OpmlImportProgress> = _opmlProgress.asStateFlow()

    fun addPodcast(feedUrl: String) {
        val trimmedUrl = feedUrl.trim()
        if (trimmedUrl.isEmpty()) return

        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                val alreadySubscribed = withContext(Dispatchers.IO) {
                    podcastDao.findByFeedUrl(trimmedUrl)
                }
                if (alreadySubscribed != null) {
                    _importState.value = ImportState.Error("Déjà abonné à ce podcast.")
                    return@launch
                }

                val parsedFeed = withContext(Dispatchers.IO) {
                    openFeedStream(trimmedUrl).use { input -> RssParser().parse(input) }
                }

                withContext(Dispatchers.IO) {
                    val podcastId = podcastDao.insert(
                        PodcastEntity(
                            feedUrl = trimmedUrl,
                            title = parsedFeed.title,
                            imageUrl = parsedFeed.imageUrl,
                            description = parsedFeed.description
                        )
                    )
                    if (podcastId > 0) {
                        episodeDao.insertAll(parsedFeed.episodes.map { it.toEntity(podcastId) })
                    }
                }
                _importState.value = ImportState.Idle
            } catch (e: Exception) {
                _importState.value = ImportState.Error(
                    "Impossible de charger ce flux : ${e.message ?: "erreur inconnue"}"
                )
            }
        }
    }

    fun dismissError() {
        _importState.value = ImportState.Idle
    }

    fun searchPodcasts(query: String) {
        if (query.isBlank()) {
            _searchState.value = SearchState.Idle
            return
        }
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            try {
                val results = searchApi.search(query)
                _searchState.value = SearchState.Results(results)
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(
                    "Recherche impossible : ${e.message ?: "erreur inconnue"}"
                )
            }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState.Idle
    }

    fun subscribeFromSearch(result: ItunesSearchResult) {
        val trackId = result.trackId
        val feedUrl = result.feedUrl ?: return
        viewModelScope.launch {
            _subscribingIds.value = _subscribingIds.value + trackId
            try {
                val existing = withContext(Dispatchers.IO) {
                    podcastDao.findByFeedUrl(feedUrl)
                }
                if (existing != null) return@launch

                val parsedFeed = withContext(Dispatchers.IO) {
                    openFeedStream(feedUrl).use { input -> RssParser().parse(input) }
                }
                withContext(Dispatchers.IO) {
                    val podcastId = podcastDao.insert(
                        PodcastEntity(
                            feedUrl = feedUrl,
                            title = parsedFeed.title.ifBlank { result.trackName },
                            imageUrl = parsedFeed.imageUrl ?: result.artworkUrl100,
                            description = parsedFeed.description
                        )
                    )
                    if (podcastId > 0) {
                        episodeDao.insertAll(parsedFeed.episodes.map { it.toEntity(podcastId) })
                    }
                }
            } catch (e: Exception) {
                _subscribeErrors.value = _subscribeErrors.value + (trackId to
                    "Échec de l'abonnement : ${e.message ?: "erreur inconnue"}")
            } finally {
                _subscribingIds.value = _subscribingIds.value - trackId
            }
        }
    }

    fun importOpmlFeeds(feeds: List<OpmlFeed>) {
        if (feeds.isEmpty()) {
            _opmlProgress.value = OpmlImportProgress(
                isActive = false, errors = listOf("Aucun flux trouvé dans ce fichier OPML.")
            )
            return
        }
        viewModelScope.launch {
            _opmlProgress.value = OpmlImportProgress(isActive = true, total = feeds.size)
            val errors = mutableListOf<String>()
            for ((index, feed) in feeds.withIndex()) {
                _opmlProgress.value = _opmlProgress.value.copy(
                    completed = index, currentTitle = feed.title
                )
                try {
                    val existing = withContext(Dispatchers.IO) {
                        podcastDao.findByFeedUrl(feed.feedUrl)
                    }
                    if (existing == null) {
                        withContext(Dispatchers.IO) {
                            openFeedStream(feed.feedUrl).use { input ->
                                val parsed = RssParser().parse(input)
                                val title = parsed.title.ifBlank { feed.title.ifBlank { "Podcast" } }
                                val podcastId = podcastDao.insert(
                                    PodcastEntity(
                                        feedUrl = feed.feedUrl,
                                        title = title,
                                        imageUrl = parsed.imageUrl,
                                        description = parsed.description
                                    )
                                )
                                if (podcastId > 0) {
                                    episodeDao.insertAll(parsed.episodes.map { it.toEntity(podcastId) })
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors += "${feed.title} : ${e.message ?: "erreur inconnue"}"
                }
            }
            _opmlProgress.value = OpmlImportProgress(
                isActive = false,
                total = feeds.size,
                completed = feeds.size,
                errors = errors
            )
        }
    }

    fun dismissOpmlProgress() {
        _opmlProgress.value = OpmlImportProgress()
    }

    private fun openFeedStream(feedUrl: String) =
        (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PulseCast/1.0 (Android)")
        }.inputStream
}