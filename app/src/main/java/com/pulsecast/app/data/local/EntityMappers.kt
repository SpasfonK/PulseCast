package com.pulsecast.app.data.local

import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.data.parser.ParsedEpisode

/** Convertit un épisode fraîchement parsé depuis un flux RSS en entité persistable. */
fun ParsedEpisode.toEntity(podcastId: Long): EpisodeEntity = EpisodeEntity(
    podcastId = podcastId,
    title = title,
    audioUrl = audioUrl,
    durationMs = durationMs,
    pubDate = pubDate,
    description = description
)
