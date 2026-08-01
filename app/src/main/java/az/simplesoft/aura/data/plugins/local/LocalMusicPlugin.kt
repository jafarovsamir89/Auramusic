package az.simplesoft.aura.data.plugins.local

import android.net.Uri
import az.simplesoft.aura.data.LocalMusicProvider
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
import az.simplesoft.aura.data.search.TrackMatcher

class LocalMusicPlugin(
    private val loadTracks: suspend () -> List<Track>
) : MusicPlugin {
    constructor(provider: LocalMusicProvider) : this(provider::load)

    override val id: String = ID
    override val displayName: String = "On device"
    override val capabilities: Set<PluginCapability> = setOf(
        PluginCapability.SEARCH,
        PluginCapability.STREAM,
        PluginCapability.LOCAL
    )
    override val priority: Int = 300

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> {
        val tracks = loadTracks()
        val matches = tracks.map { track ->
            val titleScore = TrackMatcher.similarity(request.title ?: request.rawQuery, track.title)
            val artistScore = request.artist?.let { TrackMatcher.similarity(it, track.artist) } ?: .5
            (titleScore * .7 + artistScore * .3) to track
        }.filter { (score, _) -> score >= .35 }
            .sortedByDescending { it.first }
            .take(request.limit)
            .map { (score, track) -> track.toCandidate(score) }
        return if (matches.isEmpty()) {
            PluginResult.Failure(PluginFailureReason.NOT_FOUND, "No matching local track")
        } else {
            PluginResult.Success(matches, mapOf("localCount" to tracks.size.toString()))
        }
    }

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> {
        if (candidate.providerId != id || !candidate.detailUrl.startsWith("content://")) {
            return PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "Invalid local media URI")
        }
        val track = Track(
            id = "$id:${candidate.id}",
            title = candidate.title,
            artist = candidate.artist,
            artworkUrl = candidate.artworkUrl,
            durationMs = candidate.durationMs,
            sourceId = id,
            sourcePageUrl = candidate.detailUrl,
            playbackType = PlaybackType.LOCAL,
            streamUrl = candidate.detailUrl,
            isPlayable = true,
            year = candidate.year
        )
        return PluginResult.Success(
            PlayableSource(
                providerId = id,
                track = track,
                playbackUri = Uri.parse(candidate.detailUrl),
                sourcePageUrl = candidate.detailUrl
            )
        )
    }

    override suspend fun getTrending(context: MusicContext): PluginResult<List<TrackCandidate>> =
        PluginResult.Success(loadTracks().take(context.limit).map { it.toCandidate(.5) })

    override suspend fun healthCheck(): PluginHealth = PluginHealth(
        pluginId = id,
        status = PluginHealthStatus.HEALTHY,
        message = "${loadTracks().size} tracks"
    )

    private fun Track.toCandidate(score: Double) = TrackCandidate(
        providerId = this@LocalMusicPlugin.id,
        id = this.id,
        title = title,
        artist = artist,
        detailUrl = streamUrl ?: sourcePageUrl,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        confidence = score,
        year = year
    )

    companion object {
        const val ID = "local"
    }
}
