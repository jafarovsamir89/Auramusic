package az.simplesoft.aura.data.plugins.muzofond

internal enum class MuzofondFailureReason {
    NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    ACCESS_RESTRICTED,
    PARSE_CHANGED,
    NOT_FOUND
}

internal class MuzofondPluginException(
    val reason: MuzofondFailureReason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

internal data class MuzofondSearchItem(
    val id: String,
    val title: String,
    val artist: String,
    val detailUrl: String,
    val streamUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val bitrateKbps: Int? = null
)
