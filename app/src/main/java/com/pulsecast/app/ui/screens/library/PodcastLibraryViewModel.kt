package com.pulsecast.app.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.local.toEntity
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

/**
 * Liste les podcasts déjà abonnés et gère l'ajout d'un nouveau flux RSS par
 * URL. Aucune UI dédiée à l'import OPML n'a été demandée pour cette phase :
 * le parseur `OpmlParser` de la Phase 1 reste disponible mais sans point
 * d'entrée UI pour l'instant.
 */
class PodcastLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as PulseCastApplication).database
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()

    val podcasts: StateFlow<List<PodcastEntity>> = podcastDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

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

    private fun openFeedStream(feedUrl: String) =
        (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PulseCast/1.0 (Android)")
        }.inputStream
}
