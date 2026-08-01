package az.simplesoft.aura.data.plugins.youtube

enum class YouTubeFailureReason {
    NETWORK,
    TIMEOUT,
    PARSE_CHANGED,
    NOT_FOUND,
    NO_AUDIO_FORMAT,
    SIGNATURE_UNSUPPORTED,
    ACCESS_RESTRICTED,
    LOGIN_REQUIRED,
    AGE_RESTRICTED,
    PAID_CONTENT,
    DRM,
    REGION_BLOCKED,
    RATE_LIMITED
}

class YouTubePluginException(
    val reason: YouTubeFailureReason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

enum class YouTubeResultType {
    SONG,
    VIDEO,
    ALBUM,
    ARTIST,
    PLAYLIST
}

data class YouTubeSearchItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val channel: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val isExplicit: Boolean? = null,
    val popularity: Long? = null,
    val isOfficial: Boolean = false,
    val playlistId: String? = null,
    val type: YouTubeResultType = YouTubeResultType.VIDEO
)
