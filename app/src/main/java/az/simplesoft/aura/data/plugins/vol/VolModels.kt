package az.simplesoft.aura.data.plugins.vol

internal enum class VolFailureReason {
    NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    ACCESS_RESTRICTED,
    PARSE_CHANGED,
    NOT_FOUND,
    NOT_PLAYABLE
}

internal class VolPluginException(
    val reason: VolFailureReason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

internal data class VolSearchItem(
    val id: String,
    val title: String,
    val artist: String,
    val detailUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null
)

internal data class VolPlaybackData(
    val streamUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val expiresAt: Long? = null
)
