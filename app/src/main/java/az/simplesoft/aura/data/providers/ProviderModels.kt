package az.simplesoft.aura.data.providers

import android.net.Uri
import az.simplesoft.aura.data.Track
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared bounded network policy for catalog providers. */
object AuraHttpClient {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

enum class SearchMediaKind {
    TRACK,
    RADIO
}

data class MusicSearchRequest(
    val rawQuery: String,
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val preferredProviderId: String? = null,
    // Search screens start with a 20-result page; callers can request up to the
    // provider-supported maximum when the user taps "Загрузить ещё".
    val limit: Int = 20,
    val autoPlay: Boolean = true,
    val mediaKind: SearchMediaKind = SearchMediaKind.TRACK,
    /** Optional provider-facing query produced by the music-search brain. */
    val providerQuery: String? = null,
    /** Positive semantic hints used by candidate ranking (for example, lullaby or bedtime). */
    val semanticTags: Set<String> = emptySet(),
    /** Variants that should be down-ranked unless explicitly requested. */
    val excludedTerms: Set<String> = emptySet(),
    /** True only for a resolved artist command; provider results are filtered before playback. */
    val artistStrict: Boolean = false,
    /** Permit a result with generic/missing artist metadata only when its title contains the requested artist. */
    val allowUnattributedArtistMetadata: Boolean = false
) {
    val query: String get() = providerQuery?.takeIf(String::isNotBlank)
        ?: listOfNotNull(artist, title).joinToString(" ").ifBlank { rawQuery }
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
    val confidence: Double = 0.0,
    val album: String? = null,
    val year: Int? = null,
    val isExplicit: Boolean? = null,
    val popularity: Long? = null,
    val channel: String? = null,
    val isOfficial: Boolean = false,
    val isrc: String? = null,
    val musicBrainzId: String? = null
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
