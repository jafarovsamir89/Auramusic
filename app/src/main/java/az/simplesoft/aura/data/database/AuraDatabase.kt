package az.simplesoft.aura.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        QueueSnapshotEntity::class,
        QueueSnapshotItemEntity::class,
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
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queues ADD COLUMN repeatMode TEXT NOT NULL DEFAULT 'OFF'")
                db.execSQL("ALTER TABLE queues ADD COLUMN autoContinueEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE queues SET repeatMode = 'ONE' WHERE repeatEnabled = 1")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS queue_snapshots (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        currentIndex INTEGER NOT NULL,
                        currentPositionMs INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_snapshots_createdAt ON queue_snapshots(createdAt)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS queue_snapshot_items (
                        snapshotId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        trackId TEXT NOT NULL,
                        PRIMARY KEY(snapshotId, position)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_snapshot_items_trackId ON queue_snapshot_items(trackId)")
            }
        }
    }
}
