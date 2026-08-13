package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.MusicPlugin
import az.simplesoft.aura.data.plugins.core.PluginCapability
import az.simplesoft.aura.data.plugins.core.PluginFailureReason
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.search.CandidateRankerV2
import az.simplesoft.aura.data.search.TrackIdentityResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MusicBrainTest {
    @Test
    fun searchRanksAndDeduplicatesYouTubeResults() = runBlocking {
        val youtube = SearchOnlyPlugin(
            "youtube",
            200,
            listOf(
                candidate("youtube", "yt", 187_000, true),
                candidate("youtube", "yt-audio", 190_000, false)
            )
        )
        val brain = MusicBrain(
            ProviderManager(setOf(youtube)),
            CandidateRankerV2(),
            TrackIdentityResolver(),
            EmptyRecommendationEngine,
            RecordingPlaybackCoordinator()
        )

        val result = brain.search(
            MusicSearchRequest("Linkin Park Numb", artist = "Linkin Park", title = "Numb")
        )

        assertTrue(result is SearchOutcome.Success)
        val tracks = (result as SearchOutcome.Success).tracks
        assertEquals(1, tracks.size)
        assertEquals(2, tracks.first().alternatives.size)
        assertEquals("youtube", tracks.first().alternatives.first().providerId)
    }

    @Test
    fun searchReturnsFriendlyFailureWhenEveryPluginFails() = runBlocking {
        val failing = object : MusicPlugin {
            override val id = "offline"
            override val displayName = "Offline"
            override val capabilities = setOf(PluginCapability.SEARCH, PluginCapability.STREAM)
            override val priority = 100
            override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> =
                PluginResult.Failure(PluginFailureReason.NETWORK, "Source unavailable")
            override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> =
                PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "not used")
        }
        val brain = MusicBrain(
            ProviderManager(setOf(failing)),
            CandidateRankerV2(),
            TrackIdentityResolver(),
            EmptyRecommendationEngine,
            RecordingPlaybackCoordinator()
        )

        val result = brain.search(MusicSearchRequest("test"))

        assertTrue(result is SearchOutcome.Failure)
        assertEquals("Source unavailable", (result as SearchOutcome.Failure).message)
        assertEquals(PluginFailureReason.NETWORK, result.reason)
    }

    @Test
    fun strictArtistSearchRejectsWrongProviderArtist() = runBlocking {
        val youtube = SearchOnlyPlugin(
            "youtube",
            200,
            listOf(
                candidate("youtube", "good", 180_000, true).copy(artist = "Aygün Kazımova"),
                candidate("youtube", "wrong", 180_000, true).copy(artist = "Teymur Əmrah")
            )
        )
        val brain = MusicBrain(
            ProviderManager(setOf(youtube)), CandidateRankerV2(), TrackIdentityResolver(),
            EmptyRecommendationEngine, RecordingPlaybackCoordinator()
        )

        val result = brain.search(
            MusicSearchRequest("Aygun Kazimova", artist = "Aygün Kazımova", artistStrict = true)
        ) as SearchOutcome.Success

        assertEquals(1, result.tracks.size)
        assertEquals("Aygün Kazımova", result.tracks.single().alternatives.single().artist)
    }

    private fun candidate(provider: String, id: String, duration: Long, official: Boolean) = TrackCandidate(
        providerId = provider,
        id = id,
        title = "Numb",
        artist = "Linkin Park",
        detailUrl = "https://example.test/$id",
        durationMs = duration,
        isOfficial = official
    )
}

private class SearchOnlyPlugin(
    override val id: String,
    override val priority: Int,
    private val candidates: List<TrackCandidate>
) : MusicPlugin {
    override val displayName = id
    override val capabilities = setOf(PluginCapability.SEARCH, PluginCapability.STREAM)
    override suspend fun search(request: MusicSearchRequest) = PluginResult.Success(candidates)
    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> =
        PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "not used")
}

private class RecordingPlaybackCoordinator : PlaybackCoordinator {
    override suspend fun play(queue: List<Track>, selected: Track, startPositionMs: Long) = Unit
    override suspend fun replaceCurrent(track: Track, startPositionMs: Long, fadeDurationMs: Long) = Unit
    override fun currentPositionMs(): Long = 0L
    override fun currentQueue(): List<Track> = emptyList()
}
