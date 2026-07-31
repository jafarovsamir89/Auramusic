package az.simplesoft.aura.data.plugins.core

enum class PluginCapability {
    SEARCH,
    STREAM,
    TRENDING,
    RELATED,
    ARTIST,
    ALBUM,
    PLAYLIST,
    RADIO,
    LOCAL,
    LYRICS
}

data class MusicContext(
    val localeTag: String = "ru-RU",
    val countryCode: String? = null,
    val limit: Int = 20,
    val seedTrackId: String? = null
)

enum class PluginHealthStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
}

data class PluginHealth(
    val pluginId: String,
    val status: PluginHealthStatus,
    val checkedAt: Long = System.currentTimeMillis(),
    val latencyMs: Long? = null,
    val message: String? = null
)

enum class PluginFailureReason {
    NETWORK,
    TIMEOUT,
    PARSE,
    NOT_FOUND,
    NOT_PLAYABLE,
    ACCESS_RESTRICTED,
    RATE_LIMITED,
    DISABLED,
    UNSUPPORTED,
    UNKNOWN
}

sealed interface PluginResult<out T> {
    data class Success<T>(
        val value: T,
        val diagnostics: Map<String, String> = emptyMap()
    ) : PluginResult<T>

    data class Failure(
        val reason: PluginFailureReason,
        val message: String,
        val cause: Throwable? = null,
        val diagnostics: Map<String, String> = emptyMap()
    ) : PluginResult<Nothing>
}

data class ProviderStats(
    val pluginId: String,
    val attempts: Long = 0,
    val successes: Long = 0,
    val consecutiveFailures: Int = 0,
    val averageLatencyMs: Long = 0,
    val lastFailure: PluginFailureReason? = null,
    val lastUpdatedAt: Long = 0
) {
    val successRate: Double
        get() = if (attempts == 0L) 1.0 else successes.toDouble() / attempts
}

data class ProviderPolicy(
    val searchTimeoutMs: Long = 6_000L,
    val resolveTimeoutMs: Long = 10_000L,
    val healthTimeoutMs: Long = 4_000L,
    val failureCooldownThreshold: Int = 3
)
