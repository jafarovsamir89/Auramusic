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
        AssistantConversationStateEntity::class,
        AssistantPendingCommandEntity::class,
        AssistantUserMemoryEntity::class,
        AssistantLearnedPhraseEntity::class,
        AssistantUnknownUtteranceEntity::class,
        AssistantResponseStatEntity::class
    ],
    version = 5,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
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
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_conversation_state (id TEXT NOT NULL PRIMARY KEY, nodeId TEXT, topic TEXT, emotion TEXT, expectedIntent TEXT, failureCount INTEGER NOT NULL DEFAULT 0, lastBranch TEXT, updatedAt INTEGER NOT NULL)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assistant_conversation_state ADD COLUMN expectedIntent TEXT")
                db.execSQL("ALTER TABLE assistant_conversation_state ADD COLUMN failureCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE assistant_conversation_state ADD COLUMN lastBranch TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_pending_commands (commandId TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, fingerprint TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, requiresUi INTEGER NOT NULL DEFAULT 1)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_pending_commands_status ON assistant_pending_commands(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_pending_commands_fingerprint ON assistant_pending_commands(fingerprint)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_pending_commands_createdAt ON assistant_pending_commands(createdAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_user_memory (key TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, value TEXT NOT NULL, confirmed INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_user_memory_category ON assistant_user_memory(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_user_memory_updatedAt ON assistant_user_memory(updatedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_learned_phrases (id TEXT NOT NULL PRIMARY KEY, phrase TEXT NOT NULL, normalizedPhrase TEXT NOT NULL, intent TEXT NOT NULL, slotsJson TEXT NOT NULL, confirmed INTEGER NOT NULL, errorCount INTEGER NOT NULL, confidence REAL NOT NULL, lastUsedAt INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_assistant_learned_phrases_normalizedPhrase ON assistant_learned_phrases(normalizedPhrase)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_learned_phrases_intent ON assistant_learned_phrases(intent)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_unknown_utterances (id TEXT NOT NULL PRIMARY KEY, normalizedText TEXT NOT NULL, language TEXT NOT NULL, topic TEXT, result TEXT NOT NULL, frequency INTEGER NOT NULL, firstSeenAt INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_assistant_unknown_utterances_normalizedText ON assistant_unknown_utterances(normalizedText)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assistant_unknown_utterances_lastSeenAt ON assistant_unknown_utterances(lastSeenAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS assistant_response_stats (variantId TEXT NOT NULL PRIMARY KEY, usedCount INTEGER NOT NULL, lastUsedAt INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_assistant_response_stats_variantId ON assistant_response_stats(variantId)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assistant_intent_patterns ADD COLUMN nodeId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
