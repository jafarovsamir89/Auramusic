package az.simplesoft.aura.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "tracks", primaryKeys = ["id"], indices = [Index("artist"), Index("sourceId")])
data class TrackEntity(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long?,
    val sourceId: String,
    val sourcePageUrl: String,
    val playbackType: String,
    val popularity: Int?,
    val year: Int?,
    val genre: String?,
    val updatedAt: Long
)

@Entity(tableName = "track_sources", primaryKeys = ["sourceKey"], indices = [Index("trackId"), Index("providerId")])
data class TrackSourceEntity(
    val sourceKey: String,
    val trackId: String,
    val providerId: String,
    val candidateId: String,
    val detailUrl: String,
    val mimeType: String?,
    val bitrateKbps: Int?,
    val durationMs: Long?,
    val lastResolvedAt: Long?
)

@Entity(tableName = "artists", primaryKeys = ["id"], indices = [Index("normalizedName", unique = true)])
data class ArtistEntity(val id: String, val name: String, val normalizedName: String, val artworkUrl: String?)

@Entity(tableName = "albums", primaryKeys = ["id"], indices = [Index("artistId")])
data class AlbumEntity(val id: String, val title: String, val artistId: String?, val year: Int?, val artworkUrl: String?)

@Entity(tableName = "favorites", primaryKeys = ["trackId"])
data class FavoriteEntity(val trackId: String, val addedAt: Long)

@Entity(tableName = "play_history", primaryKeys = ["id"], indices = [Index("trackId"), Index("playedAt")])
data class PlayHistoryEntity(val id: String, val trackId: String, val playedAt: Long, val lastPositionMs: Long)

@Entity(tableName = "search_history", primaryKeys = ["normalizedQuery"])
data class SearchHistoryEntity(val normalizedQuery: String, val query: String, val searchedAt: Long)

@Entity(tableName = "queues", primaryKeys = ["id"])
data class QueueEntity(
    val id: String,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatEnabled: Boolean,
    @ColumnInfo(defaultValue = "'OFF'") val repeatMode: String = "OFF",
    @ColumnInfo(defaultValue = "1") val autoContinueEnabled: Boolean = true,
    val updatedAt: Long
)

@Entity(tableName = "queue_items", primaryKeys = ["queueId", "position"], indices = [Index("trackId")])
data class QueueItemEntity(
    val queueId: String,
    val position: Int,
    val trackId: String,
    val activeSourceId: String,
    val alternativesJson: String = "[]"
)

@Entity(tableName = "queue_snapshots", indices = [Index("createdAt")])
data class QueueSnapshotEntity(
    @PrimaryKey val id: String,
    val title: String,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val createdAt: Long
)

@Entity(tableName = "queue_snapshot_items", primaryKeys = ["snapshotId", "position"], indices = [Index("trackId")])
data class QueueSnapshotItemEntity(
    val snapshotId: String,
    val position: Int,
    val trackId: String
)

@Entity(tableName = "playlists", primaryKeys = ["id"])
data class PlaylistEntity(val id: String, val name: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "position"], indices = [Index("trackId")])
data class PlaylistItemEntity(val playlistId: String, val position: Int, val trackId: String, val addedAt: Long)

@Entity(tableName = "provider_health", primaryKeys = ["providerId"])
data class ProviderHealthEntity(
    val providerId: String,
    val status: String,
    val successRate: Double,
    val averageLatencyMs: Long,
    val consecutiveFailures: Int,
    val checkedAt: Long
)

@Entity(tableName = "provider_failures", primaryKeys = ["id"], indices = [Index("providerId"), Index("occurredAt")])
data class ProviderFailureEntity(val id: String, val providerId: String, val reason: String, val occurredAt: Long)

@Entity(tableName = "recommendation_events", primaryKeys = ["id"], indices = [Index("trackId"), Index("createdAt")])
data class RecommendationEventEntity(val id: String, val trackId: String?, val eventType: String, val context: String?, val createdAt: Long)

@Entity(tableName = "blocked_tracks", primaryKeys = ["trackId"])
data class BlockedTrackEntity(val trackId: String, val blockedAt: Long)

@Entity(tableName = "blocked_artists", primaryKeys = ["normalizedArtist"])
data class BlockedArtistEntity(val normalizedArtist: String, val displayName: String, val blockedAt: Long)

@Entity(tableName = "cached_search_results", primaryKeys = ["cacheKey"], indices = [Index("expiresAt")])
data class CachedSearchResultEntity(
    val cacheKey: String,
    val normalizedQuery: String,
    val candidatesJson: String,
    val createdAt: Long,
    val expiresAt: Long
)

/** Stores reusable descriptors only. Direct playback URLs, cookies, and request headers are forbidden here. */
@Entity(tableName = "cached_playable_sources", primaryKeys = ["sourceKey"], indices = [Index("expiresAt")])
data class CachedPlayableSourceEntity(
    val sourceKey: String,
    val providerId: String,
    val candidateId: String,
    val mimeType: String?,
    val bitrateKbps: Int?,
    val expiresAt: Long?,
    val lastValidatedAt: Long?
)

@Entity(tableName = "user_preferences", primaryKeys = ["key"])
data class UserPreferenceEntity(val key: String, val value: String, val updatedAt: Long)
