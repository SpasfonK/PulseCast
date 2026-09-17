package com.pulsecast.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcast_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcast_id"]),
        Index(value = ["audio_url"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "podcast_id")
    val podcastId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "audio_url")
    val audioUrl: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "playback_position_ms")
    val playbackPositionMs: Long = 0L,

    @ColumnInfo(name = "is_played")
    val isPlayed: Boolean = false,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "pub_date")
    val pubDate: Long = 0L,

    @ColumnInfo(name = "description")
    val description: String? = null
)
