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

data class YouTubeAudioFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long? = null,
    val approximateDurationMs: Long? = null,
    val audioQuality: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    val directUrl: String? = null,
    val signatureCipher: String? = null
) {
    val codec: String?
        get() = Regex("codecs=\\\"([^\\\"]+)\\\"").find(mimeType)?.groupValues?.getOrNull(1)

    val containerMimeType: String
        get() = mimeType.substringBefore(';').trim()
}

data class YouTubePlayerData(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val popularity: Long? = null,
    val year: Int? = null,
    val expiresInSeconds: Long? = null,
    val formats: List<YouTubeAudioFormat>
)

data class YouTubeResolvedFormat(
    val format: YouTubeAudioFormat,
    val url: String,
    val expiresAtEpochMs: Long?
)
