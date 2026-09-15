package com.pulsecast.app.data.parser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ItunesSearchResult(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val artworkUrl100: String?,
    val feedUrl: String?,
    val primaryGenreName: String?
)

class ItunesSearchApi {

    suspend fun search(query: String): List<ItunesSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL("https://itunes.apple.com/search?term=$encoded&media=podcast&entity=podcast&limit=25")
        val json = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "PulseCast/1.0 (Android)")
        }.inputStream.bufferedReader().readText()
        parseSearchResults(json)
    }

    private fun parseSearchResults(json: String): List<ItunesSearchResult> {
        val obj = JSONObject(json)
        val arr = obj.getJSONArray("results")
        val results = mutableListOf<ItunesSearchResult>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val feedUrl = item.optString("feedUrl", null)
            if (feedUrl != null) {
                results += ItunesSearchResult(
                    trackId = item.optLong("trackId"),
                    trackName = item.optString("trackName", ""),
                    artistName = item.optString("artistName", ""),
                    artworkUrl100 = item.optString("artworkUrl100", null),
                    feedUrl = feedUrl,
                    primaryGenreName = item.optString("primaryGenreName", null)
                )
            }
        }
        results
    }
}