package com.pulsecast.app.data

import com.pulsecast.app.data.local.dao.EpisodeDao
import com.pulsecast.app.data.local.dao.PodcastDao
import com.pulsecast.app.data.local.entity.PodcastEntity
import com.pulsecast.app.data.local.toEntity
import com.pulsecast.app.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Résultat d'un import de flux, utilisé pour informer l'utilisateur du
 * nombre d'épisodes réellement découverts.
 */
data class FeedImportResult(
    val podcastId: Long,
    val title: String,
    val episodeCount: Int,
    val newEpisodeCount: Int,
    val alreadySubscribed: Boolean
)

/**
 * Point d'entrée unique pour enregistrer un flux RSS : téléchargement,
 * parsing, puis écriture du podcast et de ses épisodes.
 *
 * Garantie importante : l'opération est **idempotente et réparatrice**. Si le
 * podcast est déjà en base mais que ses épisodes manquent (import OPML
 * interrompu, flux momentanément indisponible, première version de l'app),
 * un nouvel import complète les épisodes manquants au lieu d'ignorer le flux.
 * C'est ce qui évite qu'un abonnement reste définitivement vide.
 */
class FeedRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) {

    suspend fun fetchAndStoreFeed(
        feedUrl: String,
        fallbackTitle: String? = null,
        fallbackImageUrl: String? = null
    ): FeedImportResult = withContext(Dispatchers.IO) {
        val parsedFeed = openFeedStream(feedUrl).use { input -> RssParser().parse(input) }
        val parsedTitle = parsedFeed.title.trim()
        val resolvedTitle = parsedTitle.ifBlank {
            fallbackTitle?.takeIf { it.isNotBlank() } ?: "Podcast sans titre"
        }

        val existing = podcastDao.findByFeedUrl(feedUrl)
        var podcastId = if (existing != null) {
            podcastDao.update(
                existing.copy(
                    // Un flux momentanément illisible (XML tronqué) ne doit pas
                    // renommer un abonnement existant en "Podcast sans titre".
                    title = parsedTitle.ifBlank { existing.title },
                    imageUrl = parsedFeed.imageUrl ?: existing.imageUrl,
                    description = parsedFeed.description ?: existing.description
                )
            )
            existing.id
        } else {
            podcastDao.insert(
                PodcastEntity(
                    feedUrl = feedUrl,
                    title = resolvedTitle,
                    imageUrl = parsedFeed.imageUrl ?: fallbackImageUrl,
                    description = parsedFeed.description
                )
            )
        }

        // Conflit concurrent éventuel sur l'unicité du feed_url : on relit
        // l'entité plutôt que d'échouer.
        if (podcastId <= 0L) {
            podcastId = podcastDao.findByFeedUrl(feedUrl)?.id ?: -1L
        }
        if (podcastId <= 0L) {
            error("Impossible d'enregistrer ce podcast dans la base locale")
        }

        val insertedIds = if (parsedFeed.episodes.isEmpty()) {
            emptyList()
        } else {
            episodeDao.insertAll(parsedFeed.episodes.map { it.toEntity(podcastId) })
        }

        FeedImportResult(
            podcastId = podcastId,
            title = resolvedTitle,
            episodeCount = parsedFeed.episodes.size,
            newEpisodeCount = insertedIds.count { it > 0L },
            alreadySubscribed = existing != null
        )
    }

    private fun openFeedStream(feedUrl: String) =
        (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PulseCast/1.0 (Android)")
        }.inputStream
}