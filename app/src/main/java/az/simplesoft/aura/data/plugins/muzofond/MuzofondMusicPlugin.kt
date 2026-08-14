package az.simplesoft.aura.data.plugins.muzofond

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

class MuzofondMusicPlugin internal constructor(
    httpClient: OkHttpClient = OkHttpClient(),
    private val searchClient: MuzofondSearchClient = MuzofondSearchClient(httpClient)
) : MusicPlugin {
    override val id: String = ID
    override val displayName: String = "Muzofond"
    override val capabilities: Set<PluginCapability> = setOf(
        PluginCapability.SEARCH,
        PluginCapability.STREAM
    )
    // Higher than YouTube so the Muzofond candidate is preferred when metadata matches.
    override val priority: Int = 250

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> = resultOf {
        searchClient.search(request.query, request.limit).map { it.toCandidate() }
    }

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> {
        if (candidate.providerId != ID) {
            return PluginResult.Failure(PluginFailureReason.NOT_FOUND, "Muzofond candidate belongs to another plugin")
        }
        val streamUrl = candidate.playbackToken?.takeIf(::isMuzofondAudioUrl)
            ?: return PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "Muzofond audio URL is unavailable")
        val track = Track(
            id = "$ID:${candidate.id}",
            title = candidate.title,
            artist = candidate.artist,
            artworkUrl = candidate.artworkUrl,
            durationMs = candidate.durationMs,
            sourceId = ID,
            sourcePageUrl = candidate.detailUrl,
            playbackType = PlaybackType.DIRECT_STREAM,
            streamUrl = streamUrl,
            requestHeaders = mapOf("Referer" to candidate.detailUrl),
            isPlayable = true,
            popularity = candidate.popularity?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            year = candidate.year
        )
        return PluginResult.Success(
            PlayableSource(
                providerId = ID,
                track = track,
                playbackUri = Uri.parse(streamUrl),
                requestHeaders = track.requestHeaders,
                mimeType = "audio/mpeg",
                sourcePageUrl = candidate.detailUrl
            )
        )
    }

    override suspend fun healthCheck(): PluginHealth {
        val startedAt = System.currentTimeMillis()
        return runCatching { searchClient.search("music", 1) }.fold(
            onSuccess = {
                PluginHealth(ID, PluginHealthStatus.HEALTHY, latencyMs = System.currentTimeMillis() - startedAt)
            },
            onFailure = {
                PluginHealth(ID, PluginHealthStatus.UNAVAILABLE, latencyMs = System.currentTimeMillis() - startedAt)
            }
        )
    }

    private suspend fun <T> resultOf(block: suspend () -> T): PluginResult<T> = try {
        PluginResult.Success(block())
    } catch (error: MuzofondPluginException) {
        PluginResult.Failure(
            reason = error.reason.toPluginReason(),
            message = error.message ?: "Muzofond is temporarily unavailable",
            cause = error,
            diagnostics = mapOf("muzofondReason" to error.reason.name)
        )
    } catch (error: Throwable) {
        PluginResult.Failure(
            PluginFailureReason.UNKNOWN,
            "Muzofond is temporarily unavailable",
            error
        )
    }

    private fun MuzofondSearchItem.toCandidate() = TrackCandidate(
        providerId = ID,
        id = id,
        title = title,
        artist = artist,
        detailUrl = detailUrl,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        bitrateKbps = bitrateKbps,
        playbackToken = streamUrl
    )

    private fun isMuzofondAudioUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return url.startsWith("https://") &&
            (host == MuzofondSelectors.HOST || host.endsWith(".${MuzofondSelectors.HOST}"))
    }

    companion object {
        const val ID = "muzofond"

        private fun MuzofondFailureReason.toPluginReason(): PluginFailureReason = when (this) {
            MuzofondFailureReason.NETWORK -> PluginFailureReason.NETWORK
            MuzofondFailureReason.TIMEOUT -> PluginFailureReason.TIMEOUT
            MuzofondFailureReason.RATE_LIMITED -> PluginFailureReason.RATE_LIMITED
            MuzofondFailureReason.ACCESS_RESTRICTED -> PluginFailureReason.ACCESS_RESTRICTED
            MuzofondFailureReason.PARSE_CHANGED -> PluginFailureReason.PARSE
            MuzofondFailureReason.NOT_FOUND -> PluginFailureReason.NOT_FOUND
        }
    }
}
