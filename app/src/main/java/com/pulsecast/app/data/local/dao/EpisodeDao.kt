package com.pulsecast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pulsecast.app.data.local.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>

    @Update
    suspend fun update(episode: EpisodeEntity)

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun findById(episodeId: Long): EpisodeEntity?

    /**
     * Liste réactive des épisodes d'un podcast. Le paramètre [filterPattern]
     * applique un filtre SQL LIKE sur le titre lorsqu'il est non-null
     * (correspond au `titleFilterPattern` défini sur le podcast) ; passer
     * `null` désactive le filtre et renvoie tous les épisodes.
     */
    @Query(
        """
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId
        AND (:filterPattern IS NULL OR title LIKE '%' || :filterPattern || '%')
        ORDER BY pub_date DESC
        """
    )
    fun observeEpisodesForPodcast(
        podcastId: Long,
        filterPattern: String?
    ): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId AND is_played = 0
        AND (:filterPattern IS NULL OR title LIKE '%' || :filterPattern || '%')
        ORDER BY pub_date DESC
        """
    )
    fun observeUnplayedForPodcast(
        podcastId: Long,
        filterPattern: String?
    ): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId AND is_played = 1
        AND (:filterPattern IS NULL OR title LIKE '%' || :filterPattern || '%')
        ORDER BY pub_date DESC
        """
    )
    fun observePlayedForPodcast(
        podcastId: Long,
        filterPattern: String?
    ): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE is_favorite = 1 ORDER BY pub_date DESC")
    fun observeFavorites(): Flow<List<EpisodeEntity>>

    /**
     * Met à jour la position de lecture et applique automatiquement la
     * règle de complétion : dès que la progression atteint 95 % de la
     * durée totale, `is_played` bascule à 1. Le calcul est fait ici, au
     * niveau SQL, pour garantir l'atomicité avec l'écriture périodique de
     * la position (toutes les 5 secondes côté PlaybackService en Phase 2).
     */
    @Query(
        """
        UPDATE episodes
        SET playback_position_ms = :positionMs,
            is_played = CASE
                WHEN :durationMs > 0 AND (:positionMs * 100 / :durationMs) >= 95 THEN 1
                ELSE is_played
            END
        WHERE id = :episodeId
        """
    )
    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long, durationMs: Long)

    @Query("UPDATE episodes SET is_played = :isPlayed WHERE id = :episodeId")
    suspend fun setPlayed(episodeId: Long, isPlayed: Boolean)

    @Query("UPDATE episodes SET is_favorite = :isFavorite WHERE id = :episodeId")
    suspend fun setFavorite(episodeId: Long, isFavorite: Boolean)

    @Query("SELECT audio_url FROM episodes WHERE podcast_id = :podcastId")
    suspend fun existingAudioUrls(podcastId: Long): List<String>
}
