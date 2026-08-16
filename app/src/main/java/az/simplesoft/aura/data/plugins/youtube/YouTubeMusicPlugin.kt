package az.simplesoft.aura.data.plugins.youtube

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.MusicContext
import az.simplesoft.aura.data.plugins.core.MusicPlugin
import az.simplesoft.aura.data.plugins.core.PluginCapability
import az.simplesoft.aura.data.plugins.core.PluginFailureReason
import az.simplesoft.aura.data.plugins.core.PluginHealth
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.providers.AuraHttpClient
import okhttp3.OkHttpClient

class YouTubeMusicPlugin(
    httpClient: OkHttpClient = AuraHttpClient.create(),
    requestContext: YouTubeRequestContext = YouTubeRequestContext(),
    private val searchParser: YouTubeSearchParser = YouTubeSearchParser(),
    private val searchClient: YouTubeSearchClient = YouTubeSearchClient(httpClient, requestContext, searchParser),
    private val playerClient: YouTubePlayerClient = YouTubePlayerClient(httpClient, requestContext)
) : MusicPlugin {
    private val healthCheck = YouTubeHealthCheck(searchClient)

    override val id: String = ID
    override val displayName: String = "YouTube Music"
    override val capabilities: Set<PluginCapability> = setOf(
        PluginCapability.SEARCH,
        PluginCapability.STREAM,
        PluginCapability.TRENDING,
        PluginCapability.RELATED
    )
    override val priority: Int = 200

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> = resultOf {
        searchClient.search(request.query, request.limit).map { it.toCandidate() }
    }

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> {
        if (candidate.providerId != id) {
            return PluginResult.Failure(PluginFailureReason.NOT_FOUND, "YouTube candidate belongs to another plugin")
        }
        val videoId = candidate.playbackToken ?: candidate.id
        if (!VIDEO_ID.matches(videoId)) {
            return PluginResult.Failure(PluginFailureReason.PARSE, "Invalid YouTube video ID")
        }
        return resultOf {
            val playbackUri = YouTubePlaybackIdentity.uri(videoId)
            val track = Track(
                id = "$ID:$videoId",
                title = candidate.title,
                artist = candidate.artist,
                artworkUrl = candidate.artworkUrl,
                durationMs = candidate.durationMs,
                sourceId = ID,
                sourcePageUrl = "${YouTubeSelectors.WATCH_URL}?v=$videoId",
                playbackType = az.simplesoft.aura.data.PlaybackType.DIRECT_STREAM,
                streamUrl = playbackUri.toString(),
                isPlayable = true,
                popularity = candidate.popularity?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                year = candidate.year
            )
            PlayableSource(
                providerId = ID,
                track = track,
                playbackUri = playbackUri,
                sourcePageUrl = track.sourcePageUrl
            )
        }
    }

    override suspend fun getTrending(context: MusicContext): PluginResult<List<TrackCandidate>> = resultOf {
        searchClient.search("top songs ${context.countryCode.orEmpty()} music", context.limit)
            .map { it.toCandidate() }
    }

    override suspend fun getRelated(track: Track): PluginResult<List<TrackCandidate>> {
        val videoId = track.id.removePrefix("$ID:").takeIf(VIDEO_ID::matches)
            ?: return PluginResult.Failure(PluginFailureReason.NOT_FOUND, "Related YouTube video ID is unavailable")
        return resultOf {
            searchParser.parseJson(playerClient.related(videoId), 20).map { it.toCandidate() }
        }
    }

    override suspend fun healthCheck(): PluginHealth = healthCheck.check(id)

    private suspend fun <T> resultOf(block: suspend () -> T): PluginResult<T> = try {
        PluginResult.Success(block())
    } catch (error: YouTubePluginException) {
        PluginResult.Failure(
            reason = error.reason.toPluginReason(),
            message = friendlyMessage(error.reason),
            cause = error,
            diagnostics = mapOf("youtubeReason" to error.reason.name)
        )
    } catch (error: Throwable) {
        PluginResult.Failure(
            PluginFailureReason.UNKNOWN,
            "YouTube Music is temporarily unavailable",
            error
        )
    }

    private fun YouTubeSearchItem.toCandidate() = TrackCandidate(
        providerId = ID,
        id = videoId,
        title = title,
        artist = artist,
        detailUrl = "${YouTubeSelectors.WATCH_URL}?v=$videoId",
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        playbackToken = videoId,
        album = album,
        year = year,
        isExplicit = isExplicit,
        popularity = popularity,
        channel = channel,
        isOfficial = isOfficial
    )

    companion object {
        const val ID = "youtube"
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        private fun YouTubeFailureReason.toPluginReason(): PluginFailureReason = when (this) {
            YouTubeFailureReason.NETWORK -> PluginFailureReason.NETWORK
            YouTubeFailureReason.TIMEOUT -> PluginFailureReason.TIMEOUT
            YouTubeFailureReason.PARSE_CHANGED -> PluginFailureReason.PARSE
            YouTubeFailureReason.NOT_FOUND -> PluginFailureReason.NOT_FOUND
            YouTubeFailureReason.NO_AUDIO_FORMAT,
            YouTubeFailureReason.SIGNATURE_UNSUPPORTED -> PluginFailureReason.NOT_PLAYABLE
            YouTubeFailureReason.RATE_LIMITED -> PluginFailureReason.RATE_LIMITED
            YouTubeFailureReason.ACCESS_RESTRICTED,
            YouTubeFailureReason.LOGIN_REQUIRED,
            YouTubeFailureReason.AGE_RESTRICTED,
            YouTubeFailureReason.PAID_CONTENT,
            YouTubeFailureReason.DRM,
            YouTubeFailureReason.REGION_BLOCKED -> PluginFailureReason.ACCESS_RESTRICTED
        }

        private fun friendlyMessage(reason: YouTubeFailureReason): String = when (reason) {
            YouTubeFailureReason.NOT_FOUND -> "YouTube Music did not find this track"
            YouTubeFailureReason.RATE_LIMITED -> "YouTube Music is temporarily rate limited"
            YouTubeFailureReason.NO_AUDIO_FORMAT,
            YouTubeFailureReason.SIGNATURE_UNSUPPORTED -> "No supported YouTube audio source"
            YouTubeFailureReason.LOGIN_REQUIRED,
            YouTubeFailureReason.AGE_RESTRICTED,
            YouTubeFailureReason.PAID_CONTENT,
            YouTubeFailureReason.DRM,
            YouTubeFailureReason.REGION_BLOCKED,
            YouTubeFailureReason.ACCESS_RESTRICTED -> "This YouTube content is restricted"
            YouTubeFailureReason.NETWORK,
            YouTubeFailureReason.TIMEOUT,
            YouTubeFailureReason.PARSE_CHANGED -> "YouTube Music is temporarily unavailable"
        }
    }
}
