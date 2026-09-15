package com.pulsecast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pulsecast.app.data.local.entity.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(podcast: PodcastEntity): Long

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Delete
    suspend fun delete(podcast: PodcastEntity)

    @Query("SELECT * FROM podcasts ORDER BY added_at DESC")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun observeById(podcastId: Long): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcasts WHERE feed_url = :feedUrl LIMIT 1")
    suspend fun findByFeedUrl(feedUrl: String): PodcastEntity?

    @Query("UPDATE podcasts SET title_filter_pattern = :pattern WHERE id = :podcastId")
    suspend fun updateTitleFilter(podcastId: Long, pattern: String?)
}
