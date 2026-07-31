package az.simplesoft.aura.data.plugins.core

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.TrackCandidate

interface MusicPlugin {
    val id: String
    val displayName: String
    val capabilities: Set<PluginCapability>
    val priority: Int

    suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>>

    suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource>

    suspend fun getTrending(context: MusicContext): PluginResult<List<TrackCandidate>> =
        unsupported(PluginCapability.TRENDING)

    suspend fun getRelated(track: Track): PluginResult<List<TrackCandidate>> =
        unsupported(PluginCapability.RELATED)

    suspend fun healthCheck(): PluginHealth = PluginHealth(id, PluginHealthStatus.UNKNOWN)

    private fun unsupported(capability: PluginCapability): PluginResult.Failure =
        PluginResult.Failure(
            PluginFailureReason.UNSUPPORTED,
            "$displayName does not support $capability"
        )
}
