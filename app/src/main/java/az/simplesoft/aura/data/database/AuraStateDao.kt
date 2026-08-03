package az.simplesoft.aura.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(values: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueue(value: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(values: List<QueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(values: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(values: List<PlayHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(values: List<SearchHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(value: UserPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendationEvent(value: RecommendationEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDialogueNodes(values: List<AssistantDialogueNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDialogueVariants(values: List<AssistantDialogueVariantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntentPatterns(values: List<AssistantIntentPatternEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversationState(value: AssistantConversationStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingCommand(value: AssistantPendingCommandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserMemory(value: AssistantUserMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearnedPhrase(value: AssistantLearnedPhraseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUnknownUtterance(value: AssistantUnknownUtteranceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResponseStat(value: AssistantResponseStatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(value: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(values: List<PlaylistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueueSnapshot(value: QueueSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSnapshotItems(values: List<QueueSnapshotItemEntity>)

    @Query("DELETE FROM queue_items WHERE queueId = :queueId")
    suspend fun deleteQueueItems(queueId: String)

    @Query("DELETE FROM favorites")
    suspend fun deleteFavorites()

    @Query("DELETE FROM play_history")
    suspend fun deleteHistory()

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deletePlaylistItems(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistEntity(playlistId: String)

    @Query("DELETE FROM queue_snapshot_items WHERE snapshotId = :snapshotId")
    suspend fun deleteQueueSnapshotItems(snapshotId: String)

    @Query("DELETE FROM queue_snapshots WHERE id = :snapshotId")
    suspend fun deleteQueueSnapshotEntity(snapshotId: String)

    @Query("SELECT * FROM queues WHERE id = :queueId LIMIT 1")
    suspend fun loadQueue(queueId: String): QueueEntity?

    @Query("SELECT * FROM assistant_dialogue_variants WHERE nodeId = :nodeId AND language = :language ORDER BY weight DESC")
    suspend fun dialogueVariants(nodeId: String, language: String): List<AssistantDialogueVariantEntity>

    @Query("SELECT * FROM assistant_conversation_state WHERE id = 'active' LIMIT 1")
    suspend fun conversationState(): AssistantConversationStateEntity?

    @Query("SELECT * FROM assistant_intent_patterns WHERE language = :language OR language = 'all' ORDER BY priority DESC")
    suspend fun intentPatterns(language: String): List<AssistantIntentPatternEntity>

    @Query("SELECT * FROM assistant_dialogue_nodes WHERE language = :language OR language = 'all' ORDER BY priority DESC")
    suspend fun dialogueNodes(language: String): List<AssistantDialogueNodeEntity>

    @Query("SELECT n.* FROM assistant_dialogue_nodes n INNER JOIN assistant_intent_patterns p ON p.nodeId = n.id AND p.id = :patternId LIMIT 1")
    suspend fun dialogueNodeForPattern(patternId: String): AssistantDialogueNodeEntity?

    @Query("SELECT * FROM assistant_pending_commands WHERE status = 'PENDING' AND requiresUi = 1 ORDER BY createdAt LIMIT :limit")
    suspend fun pendingUiCommands(limit: Int = 20): List<AssistantPendingCommandEntity>

    @Query("SELECT * FROM assistant_pending_commands WHERE commandId = :commandId LIMIT 1")
    suspend fun pendingCommand(commandId: String): AssistantPendingCommandEntity?

    @Query("SELECT * FROM assistant_pending_commands WHERE fingerprint = :fingerprint AND createdAt > :after AND status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun recentCommand(fingerprint: String, after: Long): AssistantPendingCommandEntity?

    @Query("SELECT * FROM assistant_pending_commands WHERE status = 'PENDING' AND requiresUi = 1 ORDER BY createdAt")
    fun observePendingUiCommands(): Flow<List<AssistantPendingCommandEntity>>

    @Query("UPDATE assistant_pending_commands SET status = :status, updatedAt = :updatedAt, attempts = CASE WHEN :status = 'IN_PROGRESS' THEN attempts + 1 ELSE attempts END WHERE commandId = :commandId AND status = :expectedStatus")
    suspend fun updateCommandStatus(commandId: String, expectedStatus: String, status: String, updatedAt: Long): Int

    @Query("UPDATE assistant_pending_commands SET status = 'PENDING', updatedAt = :now WHERE commandId = :commandId AND status = 'IN_PROGRESS' AND requiresUi = 1")
    suspend fun requeueUiCommand(commandId: String, now: Long): Int

    @Query("UPDATE assistant_pending_commands SET status = 'PENDING', updatedAt = :now WHERE status = 'IN_PROGRESS' AND requiresUi = 1 AND updatedAt < :staleBefore AND attempts < :maxAttempts")
    suspend fun recoverStaleUiCommands(staleBefore: Long, now: Long, maxAttempts: Int): Int

    @Query("UPDATE assistant_pending_commands SET status = 'FAILED', updatedAt = :now WHERE status = 'IN_PROGRESS' AND requiresUi = 1 AND updatedAt < :staleBefore AND attempts >= :maxAttempts")
    suspend fun failExhaustedUiCommands(staleBefore: Long, now: Long, maxAttempts: Int): Int

    @Query("UPDATE assistant_pending_commands SET status = 'FAILED', updatedAt = :now WHERE status = 'IN_PROGRESS' AND requiresUi = 0 AND updatedAt < :staleBefore")
    suspend fun failStaleBackgroundCommands(staleBefore: Long, now: Long): Int

    @Query("SELECT * FROM assistant_unknown_utterances WHERE normalizedText = :normalized LIMIT 1")
    suspend fun unknownUtterance(normalized: String): AssistantUnknownUtteranceEntity?

    @Query("SELECT * FROM assistant_user_memory ORDER BY updatedAt DESC")
    suspend fun userMemory(): List<AssistantUserMemoryEntity>

    @Query("DELETE FROM assistant_user_memory WHERE key = :key")
    suspend fun deleteUserMemory(key: String)

    @Query("DELETE FROM assistant_user_memory")
    suspend fun clearUserMemory()

    @Query("SELECT * FROM assistant_response_stats WHERE variantId IN (:variantIds)")
    suspend fun responseStats(variantIds: List<String>): List<AssistantResponseStatEntity>

    @Query("SELECT * FROM queue_items WHERE queueId = :queueId ORDER BY position")
    suspend fun loadQueueItems(queueId: String): List<QueueItemEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun loadTracks(ids: List<String>): List<TrackEntity>

    @Query("SELECT trackId FROM favorites ORDER BY addedAt DESC")
    suspend fun loadFavoriteIds(): List<String>

    @Query("SELECT trackId FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun loadHistoryIds(limit: Int = 30): List<String>

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun loadSearchHistory(limit: Int = 20): List<SearchHistoryEntity>

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun preference(key: String): String?

    @Query("SELECT * FROM recommendation_events ORDER BY createdAt DESC LIMIT :limit")
    suspend fun loadRecommendationEvents(limit: Int = 500): List<RecommendationEventEntity>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    suspend fun loadPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    suspend fun loadPlaylistItems(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT * FROM queue_snapshots ORDER BY createdAt DESC LIMIT :limit")
    suspend fun loadQueueSnapshots(limit: Int = 12): List<QueueSnapshotEntity>

    @Query("SELECT * FROM queue_snapshot_items WHERE snapshotId = :snapshotId ORDER BY position")
    suspend fun loadQueueSnapshotItems(snapshotId: String): List<QueueSnapshotItemEntity>

    @Transaction
    suspend fun replacePlaylist(
        playlist: PlaylistEntity,
        tracks: List<TrackEntity>,
        items: List<PlaylistItemEntity>
    ) {
        if (tracks.isNotEmpty()) upsertTracks(tracks)
        upsertPlaylist(playlist)
        deletePlaylistItems(playlist.id)
        if (items.isNotEmpty()) insertPlaylistItems(items)
    }

    @Transaction
    suspend fun deletePlaylist(playlistId: String) {
        deletePlaylistItems(playlistId)
        deletePlaylistEntity(playlistId)
    }

    @Transaction
    suspend fun replaceQueueSnapshot(
        snapshot: QueueSnapshotEntity,
        tracks: List<TrackEntity>,
        items: List<QueueSnapshotItemEntity>
    ) {
        if (tracks.isNotEmpty()) upsertTracks(tracks)
        upsertQueueSnapshot(snapshot)
        deleteQueueSnapshotItems(snapshot.id)
        if (items.isNotEmpty()) insertQueueSnapshotItems(items)
    }

    @Transaction
    suspend fun deleteQueueSnapshot(snapshotId: String) {
        deleteQueueSnapshotItems(snapshotId)
        deleteQueueSnapshotEntity(snapshotId)
    }

    @Transaction
    suspend fun importLegacy(
        favorites: List<FavoriteEntity>,
        history: List<PlayHistoryEntity>,
        currentIndex: Int,
        migratedAt: Long
    ) {
        if (preference(LEGACY_MIGRATION_KEY) == "1") return
        if (favorites.isNotEmpty()) insertFavorites(favorites)
        if (history.isNotEmpty()) insertHistory(history)
        upsertPreference(UserPreferenceEntity("legacy_current_index", currentIndex.toString(), migratedAt))
        upsertPreference(UserPreferenceEntity(LEGACY_MIGRATION_KEY, "1", migratedAt))
    }

    @Transaction
    suspend fun importDialoguePackage(
        nodes: List<AssistantDialogueNodeEntity>,
        variants: List<AssistantDialogueVariantEntity>,
        patterns: List<AssistantIntentPatternEntity>,
        version: String,
        importedAt: Long
    ) {
        upsertDialogueNodes(nodes)
        upsertDialogueVariants(variants)
        upsertIntentPatterns(patterns)
        upsertPreference(UserPreferenceEntity("assistant_dialogue_version", version, importedAt))
    }

    @Transaction
    suspend fun replacePlaybackState(
        tracks: List<TrackEntity>,
        queue: QueueEntity,
        queueItems: List<QueueItemEntity>,
        favorites: List<FavoriteEntity>,
        history: List<PlayHistoryEntity>,
        searches: List<SearchHistoryEntity>
    ) {
        if (tracks.isNotEmpty()) upsertTracks(tracks)
        upsertQueue(queue)
        deleteQueueItems(queue.id)
        if (queueItems.isNotEmpty()) insertQueueItems(queueItems)
        deleteFavorites()
        if (favorites.isNotEmpty()) insertFavorites(favorites)
        deleteHistory()
        if (history.isNotEmpty()) insertHistory(history)
        if (searches.isNotEmpty()) insertSearchHistory(searches)
    }

    companion object {
        const val LEGACY_MIGRATION_KEY = "legacy_shared_preferences_migrated"
    }
}
