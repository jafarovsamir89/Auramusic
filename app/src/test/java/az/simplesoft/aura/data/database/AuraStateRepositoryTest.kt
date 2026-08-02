package az.simplesoft.aura.data.database

import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraStateRepositoryTest {
    @Test
    fun legacyImportIsIdempotent() = runBlocking {
        val dao = FakeAuraStateDao()
        val repository = AuraStateRepository(dao) { 1_000L }

        repository.importLegacy(setOf("favorite"), listOf("history"), 2)
        repository.importLegacy(setOf("other"), listOf("other"), 9)

        assertEquals(setOf("favorite"), dao.favorites.keys)
        assertEquals(listOf("history"), dao.history.values.map(PlayHistoryEntity::trackId))
        assertEquals("1", dao.preferences[AuraStateDao.LEGACY_MIGRATION_KEY]?.value)
        assertEquals("2", dao.preferences["legacy_current_index"]?.value)
    }

    @Test
    fun roundTripRestoresQueueWithoutPersistingTemporaryOnlineSource() = runBlocking {
        val dao = FakeAuraStateDao()
        val repository = AuraStateRepository(dao) { 2_000L }
        val online = track(
            id = "youtube:one",
            sourceId = "youtube",
            page = "https://www.youtube.com/watch?v=abcdefghijk",
            type = PlaybackType.DIRECT_STREAM,
            stream = "https://temporary.googlevideo.example/audio",
            headers = mapOf("Cookie" to "secret")
        )
        val local = track(
            id = "local:two",
            sourceId = "local",
            page = "content://media/external/audio/2",
            type = PlaybackType.LOCAL,
            stream = "content://media/external/audio/2"
        )

        repository.save(
            AuraPlaybackSnapshot(
                queue = listOf(online, local),
                currentIndex = 1,
                positionMs = 42_000L,
                shuffleEnabled = true,
                repeatEnabled = false,
                likedIds = setOf(online.id),
                historyIds = listOf(local.id, online.id),
                recentSearches = listOf("Linkin Park Numb")
            )
        )
        val restored = repository.load()

        assertEquals(2, restored.queue.size)
        assertEquals(1, restored.currentIndex)
        assertEquals(42_000L, restored.positionMs)
        assertTrue(restored.shuffleEnabled)
        assertNull(restored.queue[0].streamUrl)
        assertTrue(restored.queue[0].requestHeaders.isEmpty())
        assertEquals(local.streamUrl, restored.queue[1].streamUrl)
        assertEquals(setOf(online.id), restored.likedIds)
        assertEquals(listOf("Linkin Park Numb"), restored.recentSearches)
        assertTrue(dao.tracks.values.none { entity ->
            entity.sourcePageUrl.contains("googlevideo") || entity.sourcePageUrl.contains("secret")
        })
    }

    @Test
    fun latestRecommendationSignalControlsSkippedTracks() = runBlocking {
        val dao = FakeAuraStateDao()
        var clock = 3_000L
        val repository = AuraStateRepository(dao) { clock++ }
        val track = track(
            id = "youtube:one",
            sourceId = "youtube",
            page = "https://www.youtube.com/watch?v=abcdefghijk",
            type = PlaybackType.DIRECT_STREAM,
            stream = "aura-youtube://video/abcdefghijk"
        )

        repository.recordRecommendationEvent(track, RecommendationEventType.SKIP)
        assertEquals(setOf(track.id), repository.skippedTrackIds())

        repository.recordRecommendationEvent(track, RecommendationEventType.LIKE)
        assertTrue(repository.skippedTrackIds().isEmpty())
    }

    private fun track(
        id: String,
        sourceId: String,
        page: String,
        type: PlaybackType,
        stream: String?,
        headers: Map<String, String> = emptyMap()
    ) = Track(
        id = id,
        title = "Track",
        artist = "Artist",
        durationMs = 180_000L,
        sourceId = sourceId,
        sourcePageUrl = page,
        playbackType = type,
        streamUrl = stream,
        requestHeaders = headers
    )
}

private class FakeAuraStateDao : AuraStateDao {
    val tracks = linkedMapOf<String, TrackEntity>()
    var queue: QueueEntity? = null
    val queueItems = mutableListOf<QueueItemEntity>()
    val favorites = linkedMapOf<String, FavoriteEntity>()
    val history = linkedMapOf<String, PlayHistoryEntity>()
    val searches = linkedMapOf<String, SearchHistoryEntity>()
    val preferences = linkedMapOf<String, UserPreferenceEntity>()
    val recommendationEvents = linkedMapOf<String, RecommendationEventEntity>()

    override suspend fun upsertTracks(values: List<TrackEntity>) = values.forEach { tracks[it.id] = it }
    override suspend fun upsertQueue(value: QueueEntity) { queue = value }
    override suspend fun insertQueueItems(values: List<QueueItemEntity>) { queueItems += values }
    override suspend fun insertFavorites(values: List<FavoriteEntity>) = values.forEach { favorites[it.trackId] = it }
    override suspend fun insertHistory(values: List<PlayHistoryEntity>) = values.forEach { history[it.id] = it }
    override suspend fun insertSearchHistory(values: List<SearchHistoryEntity>) = values.forEach { searches[it.normalizedQuery] = it }
    override suspend fun upsertPreference(value: UserPreferenceEntity) { preferences[value.key] = value }
    override suspend fun insertRecommendationEvent(value: RecommendationEventEntity) {
        recommendationEvents[value.id] = value
    }
    override suspend fun deleteQueueItems(queueId: String) { queueItems.removeAll { it.queueId == queueId } }
    override suspend fun deleteFavorites() = favorites.clear()
    override suspend fun deleteHistory() = history.clear()
    override suspend fun loadQueue(queueId: String): QueueEntity? = queue?.takeIf { it.id == queueId }
    override suspend fun loadQueueItems(queueId: String): List<QueueItemEntity> =
        queueItems.filter { it.queueId == queueId }.sortedBy(QueueItemEntity::position)
    override suspend fun loadTracks(ids: List<String>): List<TrackEntity> = ids.mapNotNull(tracks::get)
    override suspend fun loadFavoriteIds(): List<String> = favorites.values.sortedByDescending(FavoriteEntity::addedAt).map(FavoriteEntity::trackId)
    override suspend fun loadHistoryIds(limit: Int): List<String> =
        history.values.sortedByDescending(PlayHistoryEntity::playedAt).take(limit).map(PlayHistoryEntity::trackId)
    override suspend fun loadSearchHistory(limit: Int): List<SearchHistoryEntity> =
        searches.values.sortedByDescending(SearchHistoryEntity::searchedAt).take(limit)
    override suspend fun preference(key: String): String? = preferences[key]?.value
    override suspend fun loadRecommendationEvents(limit: Int): List<RecommendationEventEntity> =
        recommendationEvents.values.sortedByDescending(RecommendationEventEntity::createdAt).take(limit)
}
