package az.simplesoft.aura.data.plugins.radio

import android.net.Uri
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.RadioBrowserProvider
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.MusicContext
import az.simplesoft.aura.data.plugins.core.MusicPlugin
import az.simplesoft.aura.data.plugins.core.PluginCapability
import az.simplesoft.aura.data.plugins.core.PluginFailureReason
import az.simplesoft.aura.data.plugins.core.PluginHealth
import az.simplesoft.aura.data.plugins.core.PluginHealthStatus
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.SearchMediaKind
import az.simplesoft.aura.data.providers.TrackCandidate

class RadioMusicPlugin(
    private val searchStations: suspend (String, Int) -> List<Track>,
    private val popularStations: suspend (Int) -> List<Track>
) : MusicPlugin {
    constructor(provider: RadioBrowserProvider) : this(provider::search, provider::popular)

    override val id: String = ID
    override val displayName: String = "Radio Browser"
    override val capabilities: Set<PluginCapability> = setOf(
        PluginCapability.STREAM,
        PluginCapability.TRENDING,
        PluginCapability.RADIO
    )
    override val priority: Int = 50

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> {
        if (request.mediaKind != SearchMediaKind.RADIO) {
            return PluginResult.Failure(PluginFailureReason.UNSUPPORTED, "Explicit radio request required")
        }
        val stations = searchStations(request.query, request.limit)
        return if (stations.isEmpty()) PluginResult.Failure(PluginFailureReason.NOT_FOUND, "Radio station not found")
        else PluginResult.Success(stations.map { it.toCandidate() })
    }

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> {
        val stream = candidate.playbackToken?.takeIf { it.startsWith("https://") }
            ?: return PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "Radio stream unavailable")
        val track = Track(
            id = "$id:${candidate.id}",
            title = candidate.title,
            artist = candidate.artist,
            artworkUrl = candidate.artworkUrl,
            durationMs = null,
            sourceId = id,
            sourcePageUrl = candidate.detailUrl,
            playbackType = PlaybackType.DIRECT_STREAM,
            streamUrl = stream,
            isPlayable = true,
            popularity = candidate.popularity?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        )
        return PluginResult.Success(
            PlayableSource(
                providerId = id,
                track = track,
                playbackUri = Uri.parse(stream),
                sourcePageUrl = candidate.detailUrl
            )
        )
    }

    override suspend fun getTrending(context: MusicContext): PluginResult<List<TrackCandidate>> =
        PluginResult.Success(popularStations(context.limit).map { it.toCandidate() })

    override suspend fun healthCheck(): PluginHealth {
        val startedAt = System.currentTimeMillis()
        return runCatching { popularStations(1) }.fold(
            onSuccess = {
                PluginHealth(id, PluginHealthStatus.HEALTHY, latencyMs = System.currentTimeMillis() - startedAt)
            },
            onFailure = {
                PluginHealth(id, PluginHealthStatus.UNAVAILABLE, latencyMs = System.currentTimeMillis() - startedAt)
            }
        )
    }

    private fun Track.toCandidate() = TrackCandidate(
        providerId = this@RadioMusicPlugin.id,
        id = this.id,
        title = title,
        artist = artist,
        detailUrl = sourcePageUrl,
        artworkUrl = artworkUrl,
        durationMs = null,
        playbackToken = streamUrl,
        popularity = popularity?.toLong()
    )

    companion object {
        const val ID = "radio_browser"
    }
}
