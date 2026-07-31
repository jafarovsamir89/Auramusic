package az.simplesoft.aura.data.plugins.core

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.SearchMediaKind
import az.simplesoft.aura.data.providers.TrackCandidate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

class ProviderManager(
    plugins: Set<MusicPlugin>,
    private val policy: ProviderPolicy = ProviderPolicy(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val pluginsById = plugins.associateBy(MusicPlugin::id)
    private val enabled = ConcurrentHashMap<String, Boolean>().apply {
        pluginsById.keys.forEach { put(it, true) }
    }
    private val stats = ConcurrentHashMap<String, ProviderStats>()

    fun setEnabled(pluginId: String, value: Boolean): Boolean {
        if (pluginId !in pluginsById) return false
        enabled[pluginId] = value
        return true
    }

    fun isEnabled(pluginId: String): Boolean = enabled[pluginId] == true

    fun plugins(): List<MusicPlugin> = pluginsById.values.sortedWith(pluginOrder)

    fun stats(): Map<String, ProviderStats> = stats.toMap()

    suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> = coroutineScope {
        val requiredCapability = when (request.mediaKind) {
            SearchMediaKind.TRACK -> PluginCapability.SEARCH
            SearchMediaKind.RADIO -> PluginCapability.RADIO
        }
        val selected = eligible(requiredCapability, request.preferredProviderId)
        if (selected.isEmpty()) {
            return@coroutineScope PluginResult.Failure(
                if (request.preferredProviderId != null) PluginFailureReason.DISABLED else PluginFailureReason.UNSUPPORTED,
                "No enabled search plugin"
            )
        }

        val results = selected.map { plugin ->
            async {
                plugin to execute(plugin, policy.searchTimeoutMs) { plugin.search(request) }
            }
        }.awaitAll()

        val candidates = results.flatMap { (_, result) ->
            (result as? PluginResult.Success)?.value.orEmpty()
        }.distinctBy { "${it.providerId}:${it.id}" }

        if (candidates.isNotEmpty()) {
            PluginResult.Success(
                candidates,
                diagnostics = mapOf(
                    "plugins" to selected.joinToString(",", transform = MusicPlugin::id),
                    "partialFailures" to results.count { it.second is PluginResult.Failure }.toString()
                )
            )
        } else {
            results.mapNotNull { it.second as? PluginResult.Failure }.firstOrNull()
                ?: PluginResult.Failure(PluginFailureReason.NOT_FOUND, "No tracks found")
        }
    }

    suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> {
        val plugin = pluginsById[candidate.providerId]
            ?: return PluginResult.Failure(PluginFailureReason.NOT_FOUND, "Plugin not found")
        if (!isEnabled(plugin.id)) {
            return PluginResult.Failure(PluginFailureReason.DISABLED, "Plugin is disabled")
        }
        if (PluginCapability.STREAM !in plugin.capabilities) {
            return PluginResult.Failure(PluginFailureReason.UNSUPPORTED, "Plugin cannot resolve streams")
        }
        return execute(plugin, policy.resolveTimeoutMs) { plugin.resolve(candidate) }
    }

    suspend fun related(track: Track): PluginResult<List<TrackCandidate>> = coroutineScope {
        val selected = eligible(PluginCapability.RELATED)
        if (selected.isEmpty()) {
            return@coroutineScope PluginResult.Failure(PluginFailureReason.UNSUPPORTED, "No related-track plugin")
        }
        val results = selected.map { plugin ->
            async { execute(plugin, policy.searchTimeoutMs) { plugin.getRelated(track) } }
        }.awaitAll()
        val candidates = results.flatMap { (it as? PluginResult.Success)?.value.orEmpty() }
            .distinctBy { "${it.providerId}:${it.id}" }
        if (candidates.isNotEmpty()) PluginResult.Success(candidates)
        else results.filterIsInstance<PluginResult.Failure>().firstOrNull()
            ?: PluginResult.Failure(PluginFailureReason.NOT_FOUND, "No related tracks")
    }

    suspend fun healthCheckAll(): Map<String, PluginHealth> = coroutineScope {
        plugins().map { plugin ->
            async {
                val health = try {
                    withTimeout(policy.healthTimeoutMs) { plugin.healthCheck() }
                } catch (_: TimeoutCancellationException) {
                    PluginHealth(plugin.id, PluginHealthStatus.UNAVAILABLE, message = "timeout")
                } catch (error: Throwable) {
                    PluginHealth(plugin.id, PluginHealthStatus.UNAVAILABLE, message = error.javaClass.simpleName)
                }
                plugin.id to health
            }
        }.awaitAll().toMap()
    }

    private fun eligible(capability: PluginCapability, preferredId: String? = null): List<MusicPlugin> =
        pluginsById.values.asSequence()
            .filter { isEnabled(it.id) }
            .filter { capability in it.capabilities }
            .filter { preferredId == null || it.id == preferredId }
            .sortedWith(pluginOrder)
            .toList()

    private suspend fun <T> execute(
        plugin: MusicPlugin,
        timeoutMs: Long,
        block: suspend () -> PluginResult<T>
    ): PluginResult<T> {
        val startedAt = now()
        val result = try {
            withTimeout(timeoutMs) { block() }
        } catch (error: TimeoutCancellationException) {
            PluginResult.Failure(PluginFailureReason.TIMEOUT, "${plugin.displayName} timed out", error)
        } catch (error: Throwable) {
            PluginResult.Failure(
                PluginFailureReason.UNKNOWN,
                "${plugin.displayName} failed",
                error
            )
        }
        record(plugin.id, result, (now() - startedAt).coerceAtLeast(0L))
        return result
    }

    private fun record(pluginId: String, result: PluginResult<*>, latencyMs: Long) {
        stats.compute(pluginId) { _, previous ->
            val current = previous ?: ProviderStats(pluginId)
            val attempts = current.attempts + 1
            val average = ((current.averageLatencyMs * current.attempts) + latencyMs) / attempts
            when (result) {
                is PluginResult.Success -> current.copy(
                    attempts = attempts,
                    successes = current.successes + 1,
                    consecutiveFailures = 0,
                    averageLatencyMs = average,
                    lastFailure = null,
                    lastUpdatedAt = now()
                )
                is PluginResult.Failure -> current.copy(
                    attempts = attempts,
                    consecutiveFailures = current.consecutiveFailures + 1,
                    averageLatencyMs = average,
                    lastFailure = result.reason,
                    lastUpdatedAt = now()
                )
            }
        }
    }

    private val pluginOrder = compareByDescending<MusicPlugin> { plugin ->
        val healthPenalty = stats[plugin.id]?.consecutiveFailures.orZero()
        plugin.priority - healthPenalty.coerceAtMost(policy.failureCooldownThreshold) * 10
    }.thenBy(MusicPlugin::id)

    private fun Int?.orZero(): Int = this ?: 0
}
