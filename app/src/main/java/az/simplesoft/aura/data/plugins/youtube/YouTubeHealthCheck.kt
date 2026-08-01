package az.simplesoft.aura.data.plugins.youtube

import az.simplesoft.aura.data.plugins.core.PluginHealth
import az.simplesoft.aura.data.plugins.core.PluginHealthStatus

class YouTubeHealthCheck(
    private val searchClient: YouTubeSearchClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val cooldownMs: Long = 60_000L
) {
    private var cached: PluginHealth? = null

    suspend fun check(pluginId: String): PluginHealth {
        val previous = cached
        if (previous != null && now() - previous.checkedAt < cooldownMs) return previous
        val startedAt = now()
        val health = try {
            searchClient.search("music", 1)
            PluginHealth(pluginId, PluginHealthStatus.HEALTHY, latencyMs = now() - startedAt)
        } catch (error: YouTubePluginException) {
            PluginHealth(
                pluginId,
                if (error.reason == YouTubeFailureReason.RATE_LIMITED) {
                    PluginHealthStatus.DEGRADED
                } else {
                    PluginHealthStatus.UNAVAILABLE
                },
                latencyMs = now() - startedAt,
                message = error.reason.name
            )
        }
        cached = health
        return health
    }
}
