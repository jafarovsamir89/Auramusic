package az.simplesoft.aura.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        TrackSourceEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        SearchHistoryEntity::class,
        QueueEntity::class,
        QueueItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        ProviderHealthEntity::class,
        ProviderFailureEntity::class,
        RecommendationEventEntity::class,
        BlockedTrackEntity::class,
        BlockedArtistEntity::class,
        CachedSearchResultEntity::class,
        CachedPlayableSourceEntity::class,
        UserPreferenceEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun stateDao(): AuraStateDao

    companion object {
        @Volatile private var instance: AuraDatabase? = null

        fun get(context: Context): AuraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AuraDatabase::class.java,
                "aura_music.db"
            ).build().also { instance = it }
        }
    }
}
