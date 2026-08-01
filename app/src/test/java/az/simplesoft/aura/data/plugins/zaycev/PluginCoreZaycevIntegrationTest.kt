package az.simplesoft.aura.data.plugins.zaycev

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.zaycev.ZaycevProvider
import az.simplesoft.aura.data.search.CandidateRankerV2
import az.simplesoft.aura.data.search.TrackIdentityResolver
import az.simplesoft.aura.domain.music.EmptyRecommendationEngine
import az.simplesoft.aura.domain.music.MusicBrain
import az.simplesoft.aura.domain.music.PlaybackCoordinator
import az.simplesoft.aura.domain.music.SearchOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCoreZaycevIntegrationTest {
    @Test
    fun publicSearchPassesThroughPluginCore() = runBlocking {
        val brain = MusicBrain(
            providerManager = ProviderManager(setOf(ZaycevMusicPlugin(ZaycevProvider()))),
            candidateRanker = CandidateRankerV2(),
            identityResolver = TrackIdentityResolver(),
            recommendationEngine = EmptyRecommendationEngine,
            playbackCoordinator = NoOpPlaybackCoordinator
        )

        val result = brain.search(
            MusicSearchRequest(
                rawQuery = "Мот Капкан",
                artist = "Мот",
                title = "Капкан",
                limit = 5,
                autoPlay = false
            )
        )

        assertTrue(result is SearchOutcome.Success)
        val tracks = (result as SearchOutcome.Success).tracks
        assertTrue(tracks.isNotEmpty())
        assertEquals("Капкан", tracks.first().metadata.title)
        assertEquals("Мот", tracks.first().metadata.artist)
        assertEquals("zaycev", tracks.first().alternatives.first().providerId)
    }
}

private object NoOpPlaybackCoordinator : PlaybackCoordinator {
    override suspend fun play(queue: List<Track>, selected: Track, startPositionMs: Long) = Unit
    override suspend fun replaceCurrent(track: Track, startPositionMs: Long, fadeDurationMs: Long) = Unit
    override fun currentPositionMs(): Long = 0L
    override fun currentQueue(): List<Track> = emptyList()
}
