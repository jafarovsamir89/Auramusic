package az.simplesoft.aura.data.plugins.vol

import android.net.Uri
import az.simplesoft.aura.data.PlaybackType
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
import az.simplesoft.aura.data.providers.TrackCandidate
import okhttp3.OkHttpClient

class VolMusicPlugin internal constructor(
    httpClient: OkHttpClient = OkHttpClient(),
    private val searchClient: VolSearchClient = VolSearchClient(httpClient)
) : MusicPlugin {
    override val id: String = ID
    override val displayName: String = "Vol.az"
    override val capabilities = setOf(PluginCapability.SEARCH, PluginCapability.STREAM)
    override val priority: Int = 240

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> = resultOf {
        searchClient.search(request.query, request.limit).map { it.toCandidate() }
    }

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> {
        if (candidate.providerId != ID) {
            return PluginResult.Failure(PluginFailureReason.NOT_FOUND, "Vol.az candidate belongs to another plugin")
        }
        return try {
            val item = VolSearchItem(candidate.id, candidate.title, candidate.artist, candidate.detailUrl, candidate.artworkUrl, candidate.durationMs)
            val playback = searchClient.resolve(item)
            val track = Track(
                id = "$ID:${candidate.id}",
                title = candidate.title,
                artist = candidate.artist,
                artworkUrl = playback.artworkUrl ?: candidate.artworkUrl,
                durationMs = playback.durationMs ?: candidate.durationMs,
                sourceId = ID,
                sourcePageUrl = candidate.detailUrl,
                playbackType = PlaybackType.DIRECT_STREAM,
                streamUrl = playback.streamUrl,
                requestHeaders = mapOf("Referer" to candidate.detailUrl),
                isPlayable = true,
                year = candidate.year
            )
            PluginResult.Success(
                PlayableSource(
                    providerId = ID,
                    track = track,
                    playbackUri = Uri.parse(playback.streamUrl),
                    requestHeaders = track.requestHeaders,
                    mimeType = "audio/mpeg",
                    expiresAt = playback.expiresAt,
                    sourcePageUrl = candidate.detailUrl
                )
            )
        } catch (error: VolPluginException) {
            PluginResult.Failure(error.reason.toPluginReason(), error.message ?: "Vol.az is temporarily unavailable", error)
        } catch (error: Throwable) {
            PluginResult.Failure(PluginFailureReason.UNKNOWN, "Vol.az is temporarily unavailable", error)
        }
    }

    override suspend fun healthCheck(): PluginHealth {
        val startedAt = System.currentTimeMillis()
        return runCatching { searchClient.search("Aygün", 1) }.fold(
            onSuccess = { PluginHealth(ID, PluginHealthStatus.HEALTHY, latencyMs = System.currentTimeMillis() - startedAt) },
            onFailure = { PluginHealth(ID, PluginHealthStatus.UNAVAILABLE, latencyMs = System.currentTimeMillis() - startedAt) }
        )
    }

    private suspend fun <T> resultOf(block: suspend () -> T): PluginResult<T> = try {
        PluginResult.Success(block())
    } catch (error: VolPluginException) {
        PluginResult.Failure(error.reason.toPluginReason(), error.message ?: "Vol.az is temporarily unavailable", error)
    } catch (error: Throwable) {
        PluginResult.Failure(PluginFailureReason.UNKNOWN, "Vol.az is temporarily unavailable", error)
    }

    private fun VolSearchItem.toCandidate() = TrackCandidate(
        providerId = ID,
        id = id,
        title = title,
        artist = artist,
        detailUrl = detailUrl,
        artworkUrl = artworkUrl,
        durationMs = durationMs
    )

    companion object {
        const val ID = "vol"

        private fun VolFailureReason.toPluginReason(): PluginFailureReason = when (this) {
            VolFailureReason.NETWORK -> PluginFailureReason.NETWORK
            VolFailureReason.TIMEOUT -> PluginFailureReason.TIMEOUT
            VolFailureReason.RATE_LIMITED -> PluginFailureReason.RATE_LIMITED
            VolFailureReason.ACCESS_RESTRICTED -> PluginFailureReason.ACCESS_RESTRICTED
            VolFailureReason.PARSE_CHANGED -> PluginFailureReason.PARSE
            VolFailureReason.NOT_FOUND -> PluginFailureReason.NOT_FOUND
            VolFailureReason.NOT_PLAYABLE -> PluginFailureReason.NOT_PLAYABLE
        }
    }
}
