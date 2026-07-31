package az.simplesoft.aura.data.plugins.core

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.MusicProvider
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.TrackCandidate

class LegacyMusicPluginAdapter(
    private val provider: MusicProvider,
    override val capabilities: Set<PluginCapability> = setOf(
        PluginCapability.SEARCH,
        PluginCapability.STREAM
    )
) : MusicPlugin {
    override val id: String get() = provider.id
    override val displayName: String get() = provider.displayName
    override val priority: Int get() = provider.priority

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> =
        provider.search(request).toPluginResult()

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> =
        provider.resolve(candidate).toPluginResult()

    override suspend fun getRelated(track: Track): PluginResult<List<TrackCandidate>> =
        super.getRelated(track)

    override suspend fun healthCheck(): PluginHealth {
        val startedAt = System.currentTimeMillis()
        val result = provider.search(MusicSearchRequest(rawQuery = "test", limit = 1, autoPlay = false))
        return PluginHealth(
            pluginId = id,
            status = if (result is ProviderResult.Success) PluginHealthStatus.HEALTHY else PluginHealthStatus.DEGRADED,
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }
}

private fun <T> ProviderResult<T>.toPluginResult(): PluginResult<T> = when (this) {
    is ProviderResult.Success -> PluginResult.Success(value, diagnostics)
    is ProviderResult.Failure -> PluginResult.Failure(reason.toPluginReason(), message, cause, diagnostics)
}

private fun ProviderFailureReason.toPluginReason(): PluginFailureReason = when (this) {
    ProviderFailureReason.NETWORK -> PluginFailureReason.NETWORK
    ProviderFailureReason.PARSE -> PluginFailureReason.PARSE
    ProviderFailureReason.NOT_FOUND -> PluginFailureReason.NOT_FOUND
    ProviderFailureReason.NOT_PLAYABLE -> PluginFailureReason.NOT_PLAYABLE
    ProviderFailureReason.ACCESS_RESTRICTED -> PluginFailureReason.ACCESS_RESTRICTED
    ProviderFailureReason.TIMEOUT -> PluginFailureReason.TIMEOUT
    ProviderFailureReason.UNKNOWN -> PluginFailureReason.UNKNOWN
}
