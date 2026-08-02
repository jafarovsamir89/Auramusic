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
        UserPreferenceEntity::class,
        AssistantDialogueNodeEntity::class,
        AssistantDialogueVariantEntity::class,
        AssistantIntentPatternEntity::class,
        AssistantConversationStateEntity::class
    ],
    version = 3,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
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

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_dialogue_nodes (id TEXT NOT NULL PRIMARY KEY, topic TEXT NOT NULL, language TEXT NOT NULL, nextNodeId TEXT, priority INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_dialogue_nodes_topic ON assistant_dialogue_nodes(topic)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_dialogue_nodes_language ON assistant_dialogue_nodes(language)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_dialogue_variants (id TEXT NOT NULL PRIMARY KEY, nodeId TEXT NOT NULL, language TEXT NOT NULL, tone TEXT NOT NULL, text TEXT NOT NULL, weight INTEGER NOT NULL, cooldownKey TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_dialogue_variants_nodeId ON assistant_dialogue_variants(nodeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_dialogue_variants_language ON assistant_dialogue_variants(language)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_intent_patterns (id TEXT NOT NULL PRIMARY KEY, intent TEXT NOT NULL, language TEXT NOT NULL, pattern TEXT NOT NULL, emotion TEXT, priority INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_intent_patterns_intent ON assistant_intent_patterns(intent)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_intent_patterns_language ON assistant_intent_patterns(language)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_conversation_state (id TEXT NOT NULL PRIMARY KEY, nodeId TEXT, topic TEXT, emotion TEXT, updatedAt INTEGER NOT NULL)")
            }
        }
    }
}
