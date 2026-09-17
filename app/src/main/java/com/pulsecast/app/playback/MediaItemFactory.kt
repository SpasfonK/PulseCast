package com.pulsecast.app.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.pulsecast.app.data.local.entity.EpisodeEntity

/**
 * Construit un [MediaItem] Media3 à partir d'un épisode persistant.
 *
 * Le `mediaId` est l'identifiant Room de l'épisode (converti en String) :
 * c'est ce que [PlaybackService] relit via `player.currentMediaItem?.mediaId`
 * pour savoir quel épisode sauvegarder en base à chaque tick de progression.
 *
 * Les métadonnées (titre, nom du podcast, illustration) alimentent
 * directement l'affichage système : écran de verrouillage, notification,
 * Bluetooth, Android Auto.
 */
fun EpisodeEntity.toMediaItem(
    podcastTitle: String?,
    artworkUri: String?
): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(podcastTitle)
        .apply {
            if (!artworkUri.isNullOrBlank()) {
                setArtworkUri(artworkUri.toUri())
            }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(audioUrl)
        .setMediaMetadata(metadata)
        .build()
}
