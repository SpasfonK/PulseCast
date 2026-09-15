package com.pulsecast.app.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class OpmlFeed(
    val title: String,
    val feedUrl: String
)

/**
 * Parseur OPML pour importer des abonnements exportés depuis d'autres
 * applications (Podcast Addict, AntennaPod, Pocket Casts, etc.).
 *
 * Le format OPML organise souvent les flux dans des <outline> imbriqués
 * représentant des dossiers/catégories. Comme le parcours est fait au fil
 * de l'eau avec XmlPullParser, chaque <outline> est visité quelle que soit
 * sa profondeur d'imbrication : seuls ceux qui portent un attribut
 * `xmlUrl` sont retenus comme flux à importer, les <outline> de simple
 * regroupement sont traversés sans générer d'entrée. Les doublons d'URL
 * sont dédupliqués.
 */
class OpmlParser {

    fun parse(input: InputStream): List<OpmlFeed> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

        val feeds = mutableListOf<OpmlFeed>()
        val seenUrls = mutableSetOf<String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG &&
                parser.name.equals("outline", ignoreCase = true)
            ) {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                if (!xmlUrl.isNullOrBlank() && seenUrls.add(xmlUrl)) {
                    val title = parser.getAttributeValue(null, "title")
                        ?: parser.getAttributeValue(null, "text")
                        ?: xmlUrl
                    feeds += OpmlFeed(title = title, feedUrl = xmlUrl)
                }
            }

            eventType = try {
                parser.next()
            } catch (malformed: Exception) {
                XmlPullParser.END_DOCUMENT
            }
        }

        return feeds
    }
}
