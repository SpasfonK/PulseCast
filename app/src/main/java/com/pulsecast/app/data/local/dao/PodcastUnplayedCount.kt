package com.pulsecast.app.data.local.dao

/**
 * Projection Room : nombre d'épisodes non lus par podcast, utilisée pour
 * afficher les compteurs de la page d'accueil.
 */
data class PodcastUnplayedCount(
    val podcastId: Long,
    val unplayedCount: Int
)