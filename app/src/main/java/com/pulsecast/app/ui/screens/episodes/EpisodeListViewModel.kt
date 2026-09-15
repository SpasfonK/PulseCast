package com.pulsecast.app.ui.screens.episodes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.data.local.entity.PodcastEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Épisodes d'un podcast donné, séparés en deux flux réactifs (Non-lus/Lus)
 * qui appliquent automatiquement le `titleFilterPattern` du podcast dès
 * qu'il change — pas besoin de relancer une requête manuellement, le
 * `flatMapLatest` s'en charge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeListViewModel(
    application: Application,
    private val podcastId: Long
) : AndroidViewModel(application) {

    private val database = (application as PulseCastApplication).database
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()

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
