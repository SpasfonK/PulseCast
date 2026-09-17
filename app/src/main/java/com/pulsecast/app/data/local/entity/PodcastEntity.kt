package com.pulsecast.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcasts",
    indices = [Index(value = ["feed_url"], unique = true)]
)
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "feed_url")
    val feedUrl: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /**
     * Filtre optionnel appliqué au titre des épisodes de ce podcast.
     * Exemple : "Intégrale" pour ne conserver que les épisodes dont le
     * titre contient ce mot-clé — utile pour un flux quotidien saturé
     * d'extraits courts. `null` désactive le filtre.
     */
    @ColumnInfo(name = "title_filter_pattern")
    val titleFilterPattern: String? = null,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
