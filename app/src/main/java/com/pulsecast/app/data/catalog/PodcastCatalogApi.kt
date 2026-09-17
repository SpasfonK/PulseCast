package com.pulsecast.app.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Podcast proposé par le catalogue Apple (recherche iTunes ou classement
 * "Top Podcasts"). [feedUrl] est le flux RSS à parser pour s'abonner ; il est
 * directement fourni par la recherche iTunes, mais absent du classement
 * (voir [PodcastCatalogApi.resolveFeedUrl]).
 */
data class CatalogPodcast(
    val id: Long,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String?,
    val genre: String?
)

/**
 * Accès au catalogue public Apple, sans clé d'API :
 * - `itunes.apple.com/search` : recherche par mot-clé, renvoie déjà `feedUrl` ;
 * - `itunes.apple.com/lookup` : résolution d'un flux RSS à partir d'un
 *   identifiant (utilisé pour les entrées du classement) ;
 * - `rss.applemarketingtools.com` : classement des podcasts les plus écoutés.
 *
 * Le pays est paramétrable : `FR` par défaut, pour un catalogue francophone.
 */
class PodcastCatalogApi(private val country: String = "FR") {

    suspend fun search(query: String, limit: Int = 25): List<CatalogPodcast> =
        withContext(Dispatchers.IO) {
            val term = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$SEARCH_ENDPOINT?term=$term&media=podcast&entity=podcast" +
                "&limit=$limit&country=$country"
            parseSearchResults(getJson(url))
        }

    suspend fun topPodcasts(limit: Int = 25): List<CatalogPodcast> =
        withContext(Dispatchers.IO) {
            parseChart(getJson("$CHART_ENDPOINT/$country/podcasts/top/$limit/podcasts.json"))
        }

    suspend fun resolveFeedUrl(collectionId: Long): String? =
        withContext(Dispatchers.IO) {
            val json = getJson("$LOOKUP_ENDPOINT?id=$collectionId&entity=podcast&country=$country")
            val results = JSONObject(json).optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            results.getJSONObject(0).optString("feedUrl").takeIf { it.isNotBlank() }
        }

    private fun parseSearchResults(json: String): List<CatalogPodcast> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val podcasts = mutableListOf<CatalogPodcast>()
        for (index in 0 until results.length()) {
            val item = results.getJSONObject(index)
            val feedUrl = item.optString("feedUrl").takeIf { it.isNotBlank() } ?: continue
            podcasts += CatalogPodcast(
                id = item.optLong("trackId"),
                title = item.optString("trackName"),
                author = item.optString("artistName"),
                artworkUrl = item.optString("artworkUrl600").takeIf { it.isNotBlank() }
                    ?: item.optString("artworkUrl100").takeIf { it.isNotBlank() },
                feedUrl = feedUrl,
                genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() }
            )
        }
        return podcasts
    }

    private fun parseChart(json: String): List<CatalogPodcast> {
        val feed = JSONObject(json).optJSONObject("feed") ?: return emptyList()
        val results = feed.optJSONArray("results") ?: return emptyList()
        val podcasts = mutableListOf<CatalogPodcast>()
        for (index in 0 until results.length()) {
            val item = results.getJSONObject(index)
            val genres = item.optJSONArray("genres")
            val genreName = if (genres != null && genres.length() > 0) {
                genres.getJSONObject(0).optString("name").takeIf { it.isNotBlank() }
            } else {
                null
            }
            podcasts += CatalogPodcast(
                id = item.optLong("id"),
                title = item.optString("name"),
                author = item.optString("artistName"),
                artworkUrl = upscaleArtwork(item.optString("artworkUrl100")),
                // Le classement ne fournit pas le flux RSS : il est résolu
                // à la demande via resolveFeedUrl() au moment de s'abonner.
                feedUrl = null,
                genre = genreName
            )
        }
        return podcasts
    }

    /**
     * Le classement renvoie des pochettes 100x100 : on demande la même image
     * en 600x600 pour un rendu net dans les grandes cartes.
     */
    private fun upscaleArtwork(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val base = url.substringBeforeLast('/')
        return "$base/600x600bb.jpg"
    }

    private fun getJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PulseCast/1.0 (Android)")
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SEARCH_ENDPOINT = "https://itunes.apple.com/search"
        const val LOOKUP_ENDPOINT = "https://itunes.apple.com/lookup"
        const val CHART_ENDPOINT = "https://rss.applemarketingtools.com/api/v2"
    }
}