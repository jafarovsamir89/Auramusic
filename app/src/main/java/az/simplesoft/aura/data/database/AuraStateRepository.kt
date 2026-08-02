package az.simplesoft.aura.data.database

import android.content.Context
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.domain.music.AuraRepeatMode
import java.util.UUID

enum class RecommendationEventType { PLAY, SKIP, LIKE, UNLIKE }

data class AuraPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
    val createdAt: Long,
    val updatedAt: Long
)

data class AuraQueueSnapshot(
    val id: String,
    val title: String,
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val createdAt: Long
)

data class AuraPlaybackSnapshot(
    val queue: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: AuraRepeatMode,
    val autoContinueEnabled: Boolean = true,
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
            repeatMode = queue?.repeatMode?.let {
                runCatching { AuraRepeatMode.valueOf(it) }.getOrNull()
            } ?: if (queue?.repeatEnabled == true) AuraRepeatMode.ONE else AuraRepeatMode.OFF,
            autoContinueEnabled = queue?.autoContinueEnabled ?: true,
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
                repeatEnabled = snapshot.repeatMode != AuraRepeatMode.OFF,
                repeatMode = snapshot.repeatMode.name,
                autoContinueEnabled = snapshot.autoContinueEnabled,
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

    suspend fun loadQueueHistory(): List<AuraQueueSnapshot> = dao.loadQueueSnapshots(HISTORY_LIMIT).map { entity ->
        val items = dao.loadQueueSnapshotItems(entity.id)
        val tracks = if (items.isEmpty()) emptyMap() else {
            dao.loadTracks(items.map(QueueSnapshotItemEntity::trackId)).associateBy(TrackEntity::id)
        }
        AuraQueueSnapshot(
            id = entity.id,
            title = entity.title,
            tracks = items.mapNotNull { tracks[it.trackId]?.toTrack() },
            currentIndex = entity.currentIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            positionMs = entity.currentPositionMs.coerceAtLeast(0L),
            createdAt = entity.createdAt
        )
    }

    suspend fun archiveQueue(snapshot: AuraPlaybackSnapshot, title: String): AuraQueueSnapshot? {
        val tracks = snapshot.queue.filterNot { it.id == "aura-placeholder" }.distinctBy(Track::id)
        if (tracks.size < 2) return null
        val previous = loadQueueHistory().firstOrNull()
        if (previous?.tracks?.map(Track::id) == tracks.map(Track::id)) return previous
        val timestamp = now()
        val id = UUID.randomUUID().toString()
        val currentIndex = snapshot.currentIndex.coerceIn(0, tracks.lastIndex)
        dao.replaceQueueSnapshot(
            snapshot = QueueSnapshotEntity(id, title.take(80), currentIndex, snapshot.positionMs, timestamp),
            tracks = tracks.map { it.toEntity(timestamp) },
            items = tracks.mapIndexed { index, track -> QueueSnapshotItemEntity(id, index, track.id) }
        )
        dao.loadQueueSnapshots(HISTORY_LIMIT + 20).drop(HISTORY_LIMIT).forEach {
            dao.deleteQueueSnapshot(it.id)
        }
        return AuraQueueSnapshot(id, title.take(80), tracks, currentIndex, snapshot.positionMs, timestamp)
    }

    suspend fun deleteQueueSnapshot(snapshotId: String) = dao.deleteQueueSnapshot(snapshotId)

    suspend fun loadPlaylists(): List<AuraPlaylist> = dao.loadPlaylists().map { entity ->
        val items = dao.loadPlaylistItems(entity.id)
        val tracks = if (items.isEmpty()) emptyMap() else {
            dao.loadTracks(items.map(PlaylistItemEntity::trackId)).associateBy(TrackEntity::id)
        }
        AuraPlaylist(
            id = entity.id,
            name = entity.name,
            tracks = items.mapNotNull { tracks[it.trackId]?.toTrack() },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList()): AuraPlaylist {
        val timestamp = now()
        return savePlaylist(
            AuraPlaylist(
                id = UUID.randomUUID().toString(),
                name = normalizedPlaylistName(name),
                tracks = tracks.playlistTracks(),
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
    }

    suspend fun renamePlaylist(playlistId: String, name: String): AuraPlaylist? {
        val playlist = loadPlaylists().firstOrNull { it.id == playlistId } ?: return null
        return savePlaylist(playlist.copy(name = normalizedPlaylistName(name), updatedAt = now()))
    }

    suspend fun deletePlaylist(playlistId: String) = dao.deletePlaylist(playlistId)

    suspend fun addToPlaylist(playlistId: String, tracks: List<Track>): AuraPlaylist? {
        val playlist = loadPlaylists().firstOrNull { it.id == playlistId } ?: return null
        return savePlaylist(
            playlist.copy(
                tracks = (playlist.tracks + tracks).playlistTracks(),
                updatedAt = now()
            )
        )
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String): AuraPlaylist? {
        val playlist = loadPlaylists().firstOrNull { it.id == playlistId } ?: return null
        return savePlaylist(
            playlist.copy(
                tracks = playlist.tracks.filterNot { it.id == trackId },
                updatedAt = now()
            )
        )
    }

    suspend fun movePlaylistTrack(playlistId: String, from: Int, to: Int): AuraPlaylist? {
        val playlist = loadPlaylists().firstOrNull { it.id == playlistId } ?: return null
        if (from !in playlist.tracks.indices || to !in playlist.tracks.indices || from == to) return playlist
        val reordered = playlist.tracks.toMutableList().apply { add(to, removeAt(from)) }
        return savePlaylist(playlist.copy(tracks = reordered, updatedAt = now()))
    }

    suspend fun shufflePlaylist(playlistId: String): AuraPlaylist? {
        val playlist = loadPlaylists().firstOrNull { it.id == playlistId } ?: return null
        return savePlaylist(playlist.copy(tracks = playlist.tracks.shuffled(), updatedAt = now()))
    }

    private suspend fun savePlaylist(playlist: AuraPlaylist): AuraPlaylist {
        val tracks = playlist.tracks.playlistTracks()
        dao.replacePlaylist(
            playlist = PlaylistEntity(playlist.id, playlist.name, playlist.createdAt, playlist.updatedAt),
            tracks = tracks.map { it.toEntity(playlist.updatedAt) },
            items = tracks.mapIndexed { index, track ->
                PlaylistItemEntity(playlist.id, index, track.id, playlist.updatedAt)
            }
        )
        return playlist.copy(tracks = tracks)
    }

    private fun normalizedPlaylistName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").take(60).ifBlank { "Новый плейлист" }

    private fun List<Track>.playlistTracks(): List<Track> =
        filterNot { it.id == "aura-placeholder" }.distinctBy(Track::id).take(500)

    private fun String.normalizedQuery(): String = lowercase().trim().replace(Regex("\\s+"), " ")

    companion object {
        const val LAST_QUEUE_ID = "last_session"
        const val HISTORY_LIMIT = 10
        val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
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
        streamUrl = when {
            type == PlaybackType.LOCAL && sourcePageUrl.startsWith("content://") -> sourcePageUrl
            sourceId == "youtube" -> id.removePrefix("youtube:").takeIf(AuraStateRepository.YOUTUBE_VIDEO_ID::matches)
                ?.let { "aura-youtube://play/$it" }
            else -> null
        },
        requestHeaders = emptyMap(),
        isPlayable = true,
        popularity = popularity,
        year = year,
        genre = genre
    )
}
