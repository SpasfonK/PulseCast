package com.pulsecast.app.data.parser

import com.pulsecast.app.data.local.entity.PodcastEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Génère un document OPML à partir des podcasts abonnés — l'inverse
 * d'[OpmlParser]. Le format produit (`text`/`title`/`type="rss"`/`xmlUrl`)
 * est directement ré-importable par [OpmlParser], dans PulseCast comme dans
 * n'importe quel lecteur de podcasts respectant le standard OPML.
 */
object OpmlExporter {

    fun export(podcasts: List<PodcastEntity>): String {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        val outlines = podcasts.joinToString(separator = "\n") { podcast ->
            "        <outline text=\"${escape(podcast.title)}\" title=\"${escape(podcast.title)}\" " +
                "type=\"rss\" xmlUrl=\"${escape(podcast.feedUrl)}\" />"
        }

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<opml version="2.0">
            |    <head>
            |        <title>Abonnements PulseCast</title>
            |        <dateCreated>${dateFormat.format(Date())}</dateCreated>
            |    </head>
            |    <body>
            |$outlines
            |    </body>
            |</opml>
        """.trimMargin()
    }

    /** Échappe les caractères XML sensibles dans les attributs (titre, URL). */
    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
