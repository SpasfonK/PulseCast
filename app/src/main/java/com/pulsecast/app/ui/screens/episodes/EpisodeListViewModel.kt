package com.pulsecast.app.ui.screens.episodes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.FeedRepository
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.data.local.entity.PodcastEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Épisodes d'un podcast donné, séparés en deux flux réactifs (Non-lus/Lus)
 * qui appliquent automatiquement le `titleFilterPattern` du podcast dès
 * qu'il change — pas besoin de relancer une requête manuellement, le
 * `flatMapLatest` s'en charge.
 *
 * Ce ViewModel gère en plus l'actualisation du flux RSS :
 * - automatiquement au premier affichage si le podcast n'a **aucun** épisode
 *   en base (cas typique d'un import OPML interrompu ou d'un flux
 *   momentanément indisponible) ;
 * - manuellement via [refresh], pour récupérer les derniers épisodes publiés.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeListViewModel(
    application: Application,
    private val podcastId: Long
) : AndroidViewModel(application) {

    private val database = (application as PulseCastApplication).database
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()
    private val feedRepository = FeedRepository(podcastDao, episodeDao)

    val podcast: StateFlow<PodcastEntity?> = podcastDao.observeById(podcastId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val filterPattern: StateFlow<String?> = podcast
        .map { it?.titleFilterPattern }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unplayedEpisodes: StateFlow<List<EpisodeEntity>> = filterPattern
        .flatMapLatest { pattern -> episodeDao.observeUnplayedForPodcast(podcastId, pattern) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playedEpisodes: StateFlow<List<EpisodeEntity>> = filterPattern
        .flatMapLatest { pattern -> episodeDao.observePlayedForPodcast(podcastId, pattern) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

    init {
        // Auto-réparation : un abonnement sans le moindre épisode est presque
        // toujours le signe d'un import incomplet. On retente une fois le
        // téléchargement du flux dès l'ouverture de l'écran.
        viewModelScope.launch {
            podcast.filterNotNull().first()
            if (episodeDao.countForPodcast(podcastId) == 0) {
                refresh()
            }
        }
    }

    fun refresh() {
        val feedUrl = podcast.value?.feedUrl
        if (feedUrl.isNullOrBlank()) {
            _refreshMessage.value = "Flux introuvable pour ce podcast."
            return
        }
        if (_isRefreshing.value) return

        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshMessage.value = null
            try {
                val result = feedRepository.fetchAndStoreFeed(feedUrl)
                _refreshMessage.value = when {
                    result.episodeCount == 0 ->
                        "Aucun épisode trouvé dans ce flux."
                    result.newEpisodeCount == 0 ->
                        "Déjà à jour (${result.episodeCount} épisodes)."
                    else ->
                        "${result.newEpisodeCount} nouveaux épisodes importés."
                }
            } catch (e: Exception) {
                _refreshMessage.value =
                    "Impossible d'actualiser ce flux : ${e.message ?: "erreur inconnue"}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun togglePlayed(episode: EpisodeEntity) {
        viewModelScope.launch {
            episodeDao.setPlayed(episode.id, !episode.isPlayed)
        }
    }

    fun dismissRefreshMessage() {
        _refreshMessage.value = null
    }

    fun updateFilter(pattern: String?) {
        viewModelScope.launch {
            podcastDao.updateTitleFilter(podcastId, pattern?.takeIf { it.isNotBlank() })
        }
    }
}

/**
 * [EpisodeListViewModel] a besoin de [podcastId] en plus du contexte
 * applicatif : `viewModel()` ne sait pas l'instancier seul (contrairement à
 * un simple `AndroidViewModel`), d'où cette Factory classique.
 */
class EpisodeListViewModelFactory(
    private val application: Application,
    private val podcastId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EpisodeListViewModel(application, podcastId) as T
    }
}