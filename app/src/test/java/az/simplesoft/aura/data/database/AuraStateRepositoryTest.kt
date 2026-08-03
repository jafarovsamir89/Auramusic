package az.simplesoft.aura.data.database

import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.domain.music.AuraRepeatMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
                repeatMode = AuraRepeatMode.ALL,
                autoContinueEnabled = false,
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
        assertEquals(AuraRepeatMode.ALL, restored.repeatMode)
        assertEquals(false, restored.autoContinueEnabled)
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

    @Test
    fun playlistCrudPreservesOrderAndDeduplicatesTracks() = runBlocking {
        val dao = FakeAuraStateDao()
        var clock = 5_000L
        val repository = AuraStateRepository(dao) { clock++ }
        val one = track("youtube:one", "youtube", "https://youtube.test/one", PlaybackType.DIRECT_STREAM, null)
        val two = track("youtube:two", "youtube", "https://youtube.test/two", PlaybackType.DIRECT_STREAM, null)
        val three = track("youtube:three", "youtube", "https://youtube.test/three", PlaybackType.DIRECT_STREAM, null)

        val created = repository.createPlaylist("  Дорога  ", listOf(one, two, one))
        assertEquals("Дорога", created.name)
        assertEquals(listOf(one.id, two.id), created.tracks.map(Track::id))

        repository.addToPlaylist(created.id, listOf(two, three))
        repository.movePlaylistTrack(created.id, from = 0, to = 2)
        repository.renamePlaylist(created.id, "В машину")
        val updated = repository.loadPlaylists().single()
        assertEquals("В машину", updated.name)
        assertEquals(listOf(two.id, three.id, one.id), updated.tracks.map(Track::id))

        repository.removeFromPlaylist(created.id, three.id)
        assertEquals(listOf(two.id, one.id), repository.loadPlaylists().single().tracks.map(Track::id))

        repository.deletePlaylist(created.id)
        assertTrue(repository.loadPlaylists().isEmpty())
        assertTrue(dao.playlistItems.isEmpty())
    }

    @Test
    fun radioStreamSurvivesPlaylistRoundTrip() = runBlocking {
        val dao = FakeAuraStateDao()
        val repository = AuraStateRepository(dao) { 7_000L }
        val station = track(
            id = "radio-station",
            sourceId = "radio_browser",
            page = "https://radio.example/live.mp3",
            type = PlaybackType.DIRECT_STREAM,
            stream = "https://radio.example/live.mp3"
        )

        repository.createPlaylist("Радио", listOf(station))
        val restored = repository.loadPlaylists().single().tracks.single()

        assertEquals(station.streamUrl, restored.streamUrl)
        assertEquals("radio_browser", restored.sourceId)
    }

    @Test
    fun queueHistoryCanBeArchivedRestoredAndDeleted() = runBlocking {
        val dao = FakeAuraStateDao()
        var clock = 9_000L
        val repository = AuraStateRepository(dao) { clock++ }
        val one = track("youtube:abcdefghijk", "youtube", "https://youtube.test/one", PlaybackType.DIRECT_STREAM, null)
        val two = track("youtube:lmnopqrstuv", "youtube", "https://youtube.test/two", PlaybackType.DIRECT_STREAM, null)
        val snapshot = AuraPlaybackSnapshot(
            queue = listOf(one, two),
            currentIndex = 1,
            positionMs = 12_000L,
            shuffleEnabled = false,
            repeatMode = AuraRepeatMode.OFF,
            likedIds = emptySet(),
            historyIds = emptyList(),
            recentSearches = emptyList()
        )

        val archived = repository.archiveQueue(snapshot, "Вечер")!!
        val restored = repository.loadQueueHistory().single()
        assertEquals(archived.id, restored.id)
        assertEquals(listOf(one.id, two.id), restored.tracks.map(Track::id))
        assertEquals("aura-youtube://play/abcdefghijk", restored.tracks.first().streamUrl)
        assertEquals(1, restored.currentIndex)
        assertEquals(12_000L, restored.positionMs)

        repository.deleteQueueSnapshot(restored.id)
        assertTrue(repository.loadQueueHistory().isEmpty())
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
    val playlists = linkedMapOf<String, PlaylistEntity>()
    val playlistItems = mutableListOf<PlaylistItemEntity>()
    val queueSnapshots = linkedMapOf<String, QueueSnapshotEntity>()
    val queueSnapshotItems = mutableListOf<QueueSnapshotItemEntity>()

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
    override suspend fun upsertDialogueNodes(values: List<AssistantDialogueNodeEntity>) = Unit
    override suspend fun upsertDialogueVariants(values: List<AssistantDialogueVariantEntity>) = Unit
    override suspend fun upsertIntentPatterns(values: List<AssistantIntentPatternEntity>) = Unit
    override suspend fun upsertConversationState(value: AssistantConversationStateEntity) = Unit
    override suspend fun upsertPendingCommand(value: AssistantPendingCommandEntity) = Unit
    override suspend fun upsertUserMemory(value: AssistantUserMemoryEntity) = Unit
    override suspend fun upsertLearnedPhrase(value: AssistantLearnedPhraseEntity) = Unit
    override suspend fun upsertUnknownUtterance(value: AssistantUnknownUtteranceEntity) = Unit
    override suspend fun upsertResponseStat(value: AssistantResponseStatEntity) = Unit
    override suspend fun upsertPlaylist(value: PlaylistEntity) { playlists[value.id] = value }
    override suspend fun insertPlaylistItems(values: List<PlaylistItemEntity>) { playlistItems += values }
    override suspend fun upsertQueueSnapshot(value: QueueSnapshotEntity) { queueSnapshots[value.id] = value }
    override suspend fun insertQueueSnapshotItems(values: List<QueueSnapshotItemEntity>) { queueSnapshotItems += values }
    override suspend fun deleteQueueItems(queueId: String) { queueItems.removeAll { it.queueId == queueId } }
    override suspend fun deleteFavorites() = favorites.clear()
    override suspend fun deleteHistory() = history.clear()
    override suspend fun deletePlaylistItems(playlistId: String) {
        playlistItems.removeAll { it.playlistId == playlistId }
    }
    override suspend fun deletePlaylistEntity(playlistId: String) { playlists.remove(playlistId) }
    override suspend fun deleteQueueSnapshotItems(snapshotId: String) {
        queueSnapshotItems.removeAll { it.snapshotId == snapshotId }
    }
    override suspend fun deleteQueueSnapshotEntity(snapshotId: String) { queueSnapshots.remove(snapshotId) }
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
    override suspend fun dialogueVariants(nodeId: String, language: String): List<AssistantDialogueVariantEntity> = emptyList()
    override suspend fun conversationState(): AssistantConversationStateEntity? = null
    override suspend fun intentPatterns(language: String): List<AssistantIntentPatternEntity> = emptyList()
    override suspend fun dialogueNodes(language: String): List<AssistantDialogueNodeEntity> = emptyList()
    override suspend fun dialogueNodeForPattern(patternId: String): AssistantDialogueNodeEntity? = null
    override suspend fun pendingUiCommands(limit: Int): List<AssistantPendingCommandEntity> = emptyList()
    override suspend fun pendingCommand(commandId: String): AssistantPendingCommandEntity? = null
    override fun observePendingUiCommands(): Flow<List<AssistantPendingCommandEntity>> = emptyFlow()
    override suspend fun recentCommand(fingerprint: String, after: Long): AssistantPendingCommandEntity? = null
    override suspend fun updateCommandStatus(commandId: String, expectedStatus: String, status: String, updatedAt: Long): Int = 0
    override suspend fun unknownUtterance(normalized: String): AssistantUnknownUtteranceEntity? = null
    override suspend fun userMemory(): List<AssistantUserMemoryEntity> = emptyList()
    override suspend fun deleteUserMemory(key: String) = Unit
    override suspend fun clearUserMemory() = Unit
    override suspend fun responseStats(variantIds: List<String>): List<AssistantResponseStatEntity> = emptyList()
    override suspend fun loadRecommendationEvents(limit: Int): List<RecommendationEventEntity> =
        recommendationEvents.values.sortedByDescending(RecommendationEventEntity::createdAt).take(limit)
    override suspend fun loadPlaylists(): List<PlaylistEntity> = playlists.values.sortedByDescending(PlaylistEntity::updatedAt)
    override suspend fun loadPlaylistItems(playlistId: String): List<PlaylistItemEntity> =
        playlistItems.filter { it.playlistId == playlistId }.sortedBy(PlaylistItemEntity::position)
    override suspend fun loadQueueSnapshots(limit: Int): List<QueueSnapshotEntity> =
        queueSnapshots.values.sortedByDescending(QueueSnapshotEntity::createdAt).take(limit)
    override suspend fun loadQueueSnapshotItems(snapshotId: String): List<QueueSnapshotItemEntity> =
        queueSnapshotItems.filter { it.snapshotId == snapshotId }.sortedBy(QueueSnapshotItemEntity::position)
}
