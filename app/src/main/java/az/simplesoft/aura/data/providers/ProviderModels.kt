package az.simplesoft.aura.data.providers

import android.net.Uri
import az.simplesoft.aura.data.Track

data class MusicSearchRequest(
    val rawQuery: String,
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val preferredProviderId: String? = null,
    val limit: Int = 10,
    val autoPlay: Boolean = true
) {
    val query: String get() = listOfNotNull(artist, title).joinToString(" ").ifBlank { rawQuery }
}

data class TrackCandidate(
    val providerId: String,
    val id: String,
    val title: String,
    val artist: String,
    val detailUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val bitrateKbps: Int? = null,
    val playbackToken: String? = null,
    val confidence: Double = 0.0
)

data class PlayableSource(
    val providerId: String,
    val track: Track,
    val playbackUri: Uri,
    val requestHeaders: Map<String, String> = emptyMap(),
    val cookies: String? = null,
    val mimeType: String? = null,
    val expiresAt: Long? = null,
    val sourcePageUrl: String,
    val resolvedAt: Long = System.currentTimeMillis()
) {
    val uri: Uri get() = playbackUri
    val headers: Map<String, String> get() = requestHeaders
    val expiresAtEpochMs: Long? get() = expiresAt
}

enum class ProviderFailureReason {
    NETWORK,
    PARSE,
    NOT_FOUND,
    NOT_PLAYABLE,
    ACCESS_RESTRICTED,
    TIMEOUT,
    UNKNOWN
}

sealed interface ProviderResult<out T> {
    data class Success<T>(
        val value: T,
        val diagnostics: Map<String, String> = emptyMap()
    ) : ProviderResult<T>

    data class Failure(
        val reason: ProviderFailureReason,
        val message: String,
        val cause: Throwable? = null,
        val diagnostics: Map<String, String> = emptyMap()
    ) : ProviderResult<Nothing>
}

interface MusicProvider {
    val id: String
    val displayName: String
    val priority: Int
    suspend fun search(request: MusicSearchRequest): ProviderResult<List<TrackCandidate>>
    suspend fun resolve(candidate: TrackCandidate): ProviderResult<PlayableSource>
    suspend fun validate(source: PlayableSource): Boolean
}
