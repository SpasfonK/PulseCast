package com.pulsecast.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pulsecast.app.data.local.dao.EpisodeDao
import com.pulsecast.app.data.local.dao.PodcastDao
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.data.local.entity.PodcastEntity

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = true
)
abstract class PulseCastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        private const val DATABASE_NAME = "pulsecast.db"

        @Volatile
        private var instance: PulseCastDatabase? = null

        fun getInstance(context: Context): PulseCastDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): PulseCastDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PulseCastDatabase::class.java,
                DATABASE_NAME
            )
                // Phase 1 = schéma initial, aucune Migration nécessaire.
                // À partir de la première évolution de schéma, fournir des
                // Migration explicites plutôt qu'une stratégie destructive,
                // pour ne jamais perdre la progression de lecture des
                // utilisateurs.
                .build()
        }
    }
}
