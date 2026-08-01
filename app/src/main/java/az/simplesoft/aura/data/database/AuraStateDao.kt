package az.simplesoft.aura.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    @Query("DELETE FROM queue_items WHERE queueId = :queueId")
    suspend fun deleteQueueItems(queueId: String)

    @Query("DELETE FROM favorites")
    suspend fun deleteFavorites()

    @Query("DELETE FROM play_history")
    suspend fun deleteHistory()

    @Query("SELECT * FROM queues WHERE id = :queueId LIMIT 1")
    suspend fun loadQueue(queueId: String): QueueEntity?

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
