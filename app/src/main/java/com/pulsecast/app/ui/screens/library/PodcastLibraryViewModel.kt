package com.pulsecast.app.ui.screens.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecast.app.PulseCastApplication
import com.pulsecast.app.data.FeedRepository
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.parser.OpmlExporter
import com.pulsecast.app.data.parser.OpmlFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    data class Success(val message: String) : ImportState
    data class Error(val message: String) : ImportState
}

data class OpmlImportProgress(
    val isActive: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentTitle: String = "",
    val importedEpisodes: Int = 0,
    val errors: List<String> = emptyList()
)

/**
 * Bibliothèque : liste les podcasts abonnés et gère les deux modes d'ajout —
 * URL de flux RSS saisie à la main, ou import global d'un fichier OPML
 * (export Podcast Addict, AntennaPod, Pocket Casts…).
 *
 * Les deux chemins passent par [FeedRepository] : un podcast déjà présent
 * mais sans épisodes voit son flux re-téléchargé au lieu d'être ignoré, ce
 * qui répare les imports OPML incomplets.
 *
 * La découverte du catalogue et la recherche vivent dans `DiscoverViewModel`
 * (onglet Accueil).
 */
class PodcastLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as PulseCastApplication).database
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.episodeDao()
    private val feedRepository = FeedRepository(podcastDao, episodeDao)

    val podcasts: StateFlow<List<PodcastEntity>> = podcastDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _opmlProgress = MutableStateFlow(OpmlImportProgress())
    val opmlProgress: StateFlow<OpmlImportProgress> = _opmlProgress.asStateFlow()

    fun addPodcast(feedUrl: String) {
        val trimmedUrl = feedUrl.trim()
        if (trimmedUrl.isEmpty()) return

        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                val result = feedRepository.fetchAndStoreFeed(trimmedUrl)
                _importState.value = if (result.alreadySubscribed) {
                    ImportState.Success(
                        "Déjà abonné : flux actualisé (${result.newEpisodeCount} nouvel(s) épisode(s))."
                    )
                } else {
                    ImportState.Success(
                        "Abonné : ${result.newEpisodeCount} épisode(s) importé(s)."
                    )
                }
            } catch (e: Exception) {
                _importState.value = ImportState.Error(
                    "Impossible de charger ce flux : ${e.message ?: "erreur inconnue"}"
                )
            }
        }
    }

    fun dismissMessage() {
        _importState.value = ImportState.Idle
    }

    private val _exportState = MutableStateFlow<ImportState>(ImportState.Idle)
    val exportState: StateFlow<ImportState> = _exportState.asStateFlow()

    /**
     * Exporte tous les podcasts abonnés vers le fichier OPML désigné par
     * [uri] (obtenu via `ActivityResultContracts.CreateDocument` côté UI —
     * l'utilisateur choisit l'emplacement, aucune permission de stockage
     * n'est nécessaire). Le fichier produit est directement ré-importable
     * par [importOpmlFeeds] ou par une autre application de podcasts.
     */
    fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            _exportState.value = ImportState.Loading
            try {
                val currentPodcasts = podcasts.value
                if (currentPodcasts.isEmpty()) {
                    _exportState.value = ImportState.Error("Aucun podcast à exporter.")
                    return@launch
                }

                val opmlContent = OpmlExporter.export(currentPodcasts)
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("Impossible d'ouvrir l'emplacement choisi.")
                    output.use { it.write(opmlContent.toByteArray(Charsets.UTF_8)) }
                }

                _exportState.value = ImportState.Success(
                    "${currentPodcasts.size} podcast(s) exporté(s)."
                )
            } catch (e: Exception) {
                _exportState.value = ImportState.Error(
                    "Échec de l'export : ${e.message ?: "erreur inconnue"}"
                )
            }
        }
    }

    fun dismissExportMessage() {
        _exportState.value = ImportState.Idle
    }

    /**
     * Importe une liste de flux issus d'un fichier OPML.
     *
     * Les flux dont les épisodes sont déjà en base sont ignorés (réimport
     * rapide), mais tout flux présent sans épisodes est re-téléchargé : c'est
     * ce qui rattrape un import OPML interrompu. Les échecs unitaires sont
     * collectés sans interrompre le reste de l'import.
     */
    fun importOpmlFeeds(feeds: List<OpmlFeed>) {
        if (feeds.isEmpty()) {
            _opmlProgress.value = OpmlImportProgress(
                isActive = false,
                errors = listOf("Aucun flux trouvé dans ce fichier OPML.")
            )
            return
        }
        if (_opmlProgress.value.isActive) return

        viewModelScope.launch {
            _opmlProgress.value = OpmlImportProgress(isActive = true, total = feeds.size)
            val errors = mutableListOf<String>()
            var importedEpisodes = 0

            for ((index, feed) in feeds.withIndex()) {
                _opmlProgress.value = _opmlProgress.value.copy(
                    completed = index,
                    currentTitle = feed.title
                )
                try {
                    val existing = podcastDao.findByFeedUrl(feed.feedUrl)
                    val alreadyComplete = existing != null &&
                        episodeDao.countForPodcast(existing.id) > 0
                    if (alreadyComplete) continue

                    val result = feedRepository.fetchAndStoreFeed(
                        feedUrl = feed.feedUrl,
                        fallbackTitle = feed.title
                    )
                    importedEpisodes += result.newEpisodeCount
                    if (result.episodeCount == 0) {
                        errors += "${feed.title} : aucun épisode dans le flux"
                    }
                } catch (e: Exception) {
                    errors += "${feed.title} : ${e.message ?: "erreur inconnue"}"
                }
            }

            _opmlProgress.value = OpmlImportProgress(
                isActive = false,
                total = feeds.size,
                completed = feeds.size,
                importedEpisodes = importedEpisodes,
                errors = errors
            )
        }
    }

    fun dismissOpmlProgress() {
        _opmlProgress.value = OpmlImportProgress()
    }
}
