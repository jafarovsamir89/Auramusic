package az.simplesoft.aura.data.database

import android.content.Context
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import java.util.UUID

enum class RecommendationEventType { PLAY, SKIP, LIKE, UNLIKE }

data class AuraPlaybackSnapshot(
    val queue: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatEnabled: Boolean,
    val likedIds: Set<String>,
    val historyIds: List<String>,
    val recentSearches: List<String>,
    val memoryTracks: List<Track> = emptyList()
)

class AuraStateRepository(
    private val dao: AuraStateDao,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(AuraDatabase.get(context).stateDao())

    suspend fun importLegacy(likedIds: Set<String>, historyIds: List<String>, currentIndex: Int) {
        val timestamp = now()
        dao.importLegacy(
            favorites = likedIds.mapIndexed { index, id -> FavoriteEntity(id, timestamp - index) },
            history = historyIds.distinct().mapIndexed { index, id ->
                PlayHistoryEntity("legacy:$index:$id", id, timestamp - index, 0L)
            },
            currentIndex = currentIndex,
            migratedAt = timestamp
        )
    }

    suspend fun load(): AuraPlaybackSnapshot {
        val queue = dao.loadQueue(LAST_QUEUE_ID)
        val items = dao.loadQueueItems(LAST_QUEUE_ID)
        val favoriteIds = dao.loadFavoriteIds()
        val historyIds = dao.loadHistoryIds().distinct()
        val tracks = if (items.isEmpty()) emptyMap() else {
            dao.loadTracks(items.map(QueueItemEntity::trackId)).associateBy(TrackEntity::id)
        }
        val memoryIds = (favoriteIds + historyIds).distinct()
        val memoryTracks = if (memoryIds.isEmpty()) emptyList() else {
            dao.loadTracks(memoryIds).map(TrackEntity::toTrack)
        }
        val restoredQueue = items.mapNotNull { tracks[it.trackId]?.toTrack() }
        return AuraPlaybackSnapshot(
            queue = restoredQueue,
            currentIndex = queue?.currentIndex?.coerceIn(0, restoredQueue.lastIndex.coerceAtLeast(0)) ?: 0,
            positionMs = queue?.currentPositionMs?.coerceAtLeast(0L) ?: 0L,
            shuffleEnabled = queue?.shuffleEnabled ?: false,
            repeatEnabled = queue?.repeatEnabled ?: false,
            likedIds = favoriteIds.toSet(),
            historyIds = historyIds,
            recentSearches = dao.loadSearchHistory().map(SearchHistoryEntity::query),
            memoryTracks = memoryTracks
        )
    }

    suspend fun save(snapshot: AuraPlaybackSnapshot) {
        val timestamp = now()
        val persistedTracks = snapshot.queue
            .filterNot { it.id == "aura-placeholder" }
            .distinctBy(Track::id)
            .map { it.toEntity(timestamp) }
        val persistedIds = persistedTracks.map(TrackEntity::id).toSet()
        val queueItems = snapshot.queue.mapIndexedNotNull { position, track ->
            track.takeIf { it.id in persistedIds }?.let {
                QueueItemEntity(LAST_QUEUE_ID, position, it.id, it.sourceId)
            }
        }
        dao.replacePlaybackState(
            tracks = persistedTracks,
            queue = QueueEntity(
                id = LAST_QUEUE_ID,
                currentIndex = snapshot.currentIndex.coerceAtLeast(0),
                currentPositionMs = snapshot.positionMs.coerceAtLeast(0L),
                shuffleEnabled = snapshot.shuffleEnabled,
                repeatEnabled = snapshot.repeatEnabled,
                updatedAt = timestamp
            ),
            queueItems = queueItems,
            favorites = snapshot.likedIds.mapIndexed { index, id -> FavoriteEntity(id, timestamp - index) },
            history = snapshot.historyIds.distinct().take(30).mapIndexed { index, id ->
                PlayHistoryEntity("current:$index:$id", id, timestamp - index, if (index == 0) snapshot.positionMs else 0L)
            },
            searches = snapshot.recentSearches.distinct().take(20).mapIndexed { index, query ->
                SearchHistoryEntity(query.normalizedQuery(), query, timestamp - index)
            }
        )
    }

    suspend fun recordRecommendationEvent(
        track: Track,
        type: RecommendationEventType,
        context: String? = null
    ) {
        if (track.id == "aura-placeholder") return
        val timestamp = now()
        dao.upsertTracks(listOf(track.toEntity(timestamp)))
        dao.insertRecommendationEvent(
            RecommendationEventEntity(
                id = "${timestamp}:${UUID.randomUUID()}",
                trackId = track.id,
                eventType = type.name,
                context = context,
                createdAt = timestamp
            )
        )
    }

    suspend fun skippedTrackIds(): Set<String> {
        val latestByTrack = dao.loadRecommendationEvents()
            .filter { !it.trackId.isNullOrBlank() }
            .distinctBy(RecommendationEventEntity::trackId)
        return latestByTrack
            .filter { it.eventType == RecommendationEventType.SKIP.name }
            .mapNotNull(RecommendationEventEntity::trackId)
            .toSet()
    }

    private fun String.normalizedQuery(): String = lowercase().trim().replace(Regex("\\s+"), " ")

    companion object {
        const val LAST_QUEUE_ID = "last_session"
    }
}

internal fun Track.toEntity(timestamp: Long): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    sourceId = sourceId,
    sourcePageUrl = sourcePageUrl,
    playbackType = playbackType.name,
    popularity = popularity,
    year = year,
    genre = genre,
    updatedAt = timestamp
)

internal fun TrackEntity.toTrack(): Track {
    val type = runCatching { PlaybackType.valueOf(playbackType) }.getOrDefault(PlaybackType.DIRECT_STREAM)
    return Track(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        sourceId = sourceId,
        sourcePageUrl = sourcePageUrl,
        playbackType = type,
        streamUrl = sourcePageUrl.takeIf { type == PlaybackType.LOCAL && it.startsWith("content://") },
        requestHeaders = emptyMap(),
        isPlayable = true,
        popularity = popularity,
        year = year,
        genre = genre
    )
}
