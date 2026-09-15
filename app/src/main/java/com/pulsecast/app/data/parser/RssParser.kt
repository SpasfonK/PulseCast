package com.pulsecast.app.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedEpisode(
    val title: String,
    val audioUrl: String,
    val durationMs: Long,
    val pubDate: Long,
    val description: String?
)

data class ParsedFeed(
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val episodes: List<ParsedEpisode>
)

/**
 * Parseur RSS résilient basé sur [XmlPullParser].
 *
 * Principes de robustesse :
 * - Toute balise inconnue ou propre à un espace de noms non géré (Podcasting
 *   2.0 : podcast:transcript, podcast:chapters ; itunes:* ; media:content ;
 *   atom:link...) est silencieusement ignorée sans interrompre le parsing.
 * - Les attributs manquants ou non numériques sur <enclosure> (ex. `length`
 *   absent) n'ont aucun impact : seuls `url` et `type` sont exploités.
 * - Plusieurs formats de date RFC 822 / ISO 8601 sont testés successivement ;
 *   à défaut de correspondance, la date retombe à 0L plutôt que de lever
 *   une exception qui ferait échouer tout l'import du flux.
 * - Un item sans URL audio exploitable (pas d'<enclosure> valide) est exclu
 *   du résultat : il ne peut de toute façon pas être lu.
 * - Si le flux est tronqué ou mal formé en cours de lecture, le parsing
 *   s'arrête proprement et renvoie les épisodes déjà collectés plutôt que
 *   de tout perdre.
 */
class RssParser {

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

        var channelTitle = ""
        var channelImage: String? = null
        var channelDescription: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        var insideChannel = false
        var insideItem = false

        var itemTitle: String? = null
        var itemAudioUrl: String? = null
        var itemDurationMs = 0L
        var itemPubDate = 0L
        var itemDescription: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (localName(parser.name)) {
                    "channel" -> insideChannel = true

                    "item" -> {
                        insideItem = true
                        itemTitle = null
                        itemAudioUrl = null
                        itemDurationMs = 0L
                        itemPubDate = 0L
                        itemDescription = null
                    }

                    "title" -> {
                        val text = safeNextText(parser)
                        if (insideItem) {
                            itemTitle = text
                        } else if (insideChannel && channelTitle.isEmpty()) {
                            channelTitle = text.orEmpty()
                        }
                    }

                    "enclosure" -> if (insideItem) {
                        val url = parser.getAttributeValue(null, "url")
                        val type = parser.getAttributeValue(null, "type")
                        if (!url.isNullOrBlank() && isPlayableMediaType(type)) {
                            itemAudioUrl = url
                        }
                    }

                    // Media RSS / Podcasting 2.0 : certains flux référencent
                    // le fichier dans <media:content> au lieu d'un
                    // <enclosure>. `medium` évite de confondre une vignette
                    // image avec le média à lire.
                    "content" -> if (insideItem && itemAudioUrl == null) {
                        val url = parser.getAttributeValue(null, "url")
                        val medium = parser.getAttributeValue(null, "medium")
                        val type = parser.getAttributeValue(null, "type") ?: medium
                        if (!url.isNullOrBlank() && isPlayableMediaType(type)) {
                            itemAudioUrl = url
                        }
                    }

                    "url" -> if (insideChannel && !insideItem && channelImage == null) {
                        channelImage = safeNextText(parser)
                    }

                    "image" -> {
                        // itunes:image se présente en balise auto-fermante
                        // avec un attribut href (pas de texte imbriqué).
                        if (rawTagIs(parser.name, "itunes:image") &&
                            !insideItem && channelImage == null
                        ) {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank()) channelImage = href
                        }
                        // Le <image> RSS standard n'est qu'un conteneur ;
                        // son <url> imbriqué est capturé par la branche "url".
                    }

                    "duration" -> if (insideItem) {
                        itemDurationMs = parseDurationToMillis(safeNextText(parser))
                    }

                    "pubdate" -> if (insideItem) {
                        itemPubDate = parseRfc822Date(safeNextText(parser))
                    }

                    "description", "summary" -> {
                        val text = safeNextText(parser)
                        if (insideItem) {
                            if (itemDescription == null) itemDescription = text
                        } else if (insideChannel && channelDescription == null) {
                            channelDescription = text
                        }
                    }

                    else -> {
                        // Podcasting 2.0 / itunes / media / atom : ignoré
                        // volontairement pour la résilience du parseur.
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                when (localName(parser.name)) {
                    "item" -> {
                        insideItem = false
                        val audioUrl = itemAudioUrl
                        val title = itemTitle
                        if (!audioUrl.isNullOrBlank() && !title.isNullOrBlank()) {
                            episodes += ParsedEpisode(
                                title = title,
                                audioUrl = audioUrl,
                                durationMs = itemDurationMs,
                                pubDate = itemPubDate,
                                description = itemDescription
                            )
                        }
                    }

                    "channel" -> insideChannel = false
                }
            }

            eventType = try {
                parser.next()
            } catch (malformed: Exception) {
                XmlPullParser.END_DOCUMENT
            }
        }

        return ParsedFeed(
            title = channelTitle.ifBlank { "Podcast sans titre" },
            imageUrl = channelImage,
            description = channelDescription,
            episodes = episodes
        )
    }

    private fun safeNextText(parser: XmlPullParser): String? = try {
        parser.nextText()?.trim()
    } catch (e: Exception) {
        null
    }

    /**
     * Accepte l'audio comme la vidéo : un fichier vidéo lu sans surface
     * (lecture en arrière-plan) ne restitue que sa piste audio, ce qui reste
     * préférable à un épisode invisible. Les vignettes (image/*) et documents
     * (application/*) sont bien écartés.
     */
    private fun isPlayableMediaType(type: String?): Boolean =
        type == null ||
            type.startsWith("audio", ignoreCase = true) ||
            type.startsWith("video", ignoreCase = true)

    private fun rawTagIs(rawName: String?, expected: String): Boolean =
        rawName?.equals(expected, ignoreCase = true) == true

    private fun localName(rawName: String?): String {
        if (rawName == null) return ""
        val colonIndex = rawName.indexOf(':')
        return if (colonIndex >= 0) {
            rawName.substring(colonIndex + 1).lowercase(Locale.US)
        } else {
            rawName.lowercase(Locale.US)
        }
    }

    /**
     * Convertit une durée iTunes exprimée en secondes ("125"), MM:SS
     * ("12:34") ou HH:MM:SS ("01:12:34") en millisecondes.
     */
    private fun parseDurationToMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        return try {
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":").map { it.toLong() }
                val seconds = when (parts.size) {
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    2 -> parts[0] * 60 + parts[1]
                    1 -> parts[0]
                    else -> 0L
                }
                seconds * 1000L
            } else {
                trimmed.toLong() * 1000L
            }
        } catch (e: NumberFormatException) {
            0L
        }
    }

    private val dateFormats: List<SimpleDateFormat> by lazy {
        listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        ).map { pattern ->
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = true
                if (pattern.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }

    private fun parseRfc822Date(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val cleaned = raw.trim()
        for (format in dateFormats) {
            try {
                format.parse(cleaned)?.let { return it.time }
            } catch (e: Exception) {
                // On tente le format suivant.
            }
        }
        return 0L
    }
}
