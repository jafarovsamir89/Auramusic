package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.PlaybackType
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecommendationEngineTest {
    @Test
    fun myMixExcludesSkipsAndKeepsArtistDiversity() = runBlocking {
        val candidates = listOf(
            candidate("aaaaaaaaaaa", "Muse", "Uprising"),
            candidate("bbbbbbbbbbb", "Muse", "Starlight"),
            candidate("ccccccccccc", "Muse", "Madness"),
            candidate("ddddddddddd", "Radiohead", "Creep"),
            candidate("eeeeeeeeeee", "Coldplay", "Yellow")
        )
        val plugin = RecommendationPlugin(candidates)
        val engine = PersonalRecommendationEngine(
            providerManager = ProviderManager(setOf(plugin)),
            candidateRanker = CandidateRankerV2(),
            identityResolver = TrackIdentityResolver(),
            candidateResolver = { it.toTrack() }
        )
        val seed = track("youtube:seedseedsee", "Muse", "Hysteria")
        val context = RecommendationContext(
            queue = listOf(seed),
            currentIndex = 0,
            recentTracks = listOf(seed),
            likedTrackIds = setOf(seed.id),
            skippedTrackIds = setOf("youtube:eeeeeeeeeee"),
            hourOfDay = 20
        )

        val mix = engine.myMix(context, limit = 5)

        assertFalse(mix.any { it.id == "youtube:eeeeeeeeeee" })
        assertTrue(mix.first().artist == "Muse")
        assertEquals(2, mix.count { it.artist == "Muse" })
        assertTrue(mix.any { it.artist == "Radiohead" })
    }

    @Test
    fun memoryPreferencesBoostPreferredArtistsAndSuppressDislikes() = runBlocking {
        val candidates = listOf(
            candidate("11111111111", "Muse", "Uprising"),
            candidate("22222222222", "Coldplay", "Yellow"),
            candidate("33333333333", "Radiohead", "Creep")
        )
        val plugin = RecommendationPlugin(candidates)
        val engine = PersonalRecommendationEngine(
            providerManager = ProviderManager(setOf(plugin)),
            candidateRanker = CandidateRankerV2(),
            identityResolver = TrackIdentityResolver(),
            candidateResolver = { it.toTrack() }
        )
        val seed = track("youtube:seedseedsee", "Muse", "Hysteria")
        val context = RecommendationContext(
            queue = listOf(seed),
            currentIndex = 0,
            recentTracks = listOf(seed),
            likedTrackIds = emptySet(),
            preferredArtists = setOf("Coldplay"),
            dislikedArtists = setOf("Muse"),
            hourOfDay = 20
        )

        val mix = engine.myMix(context, limit = 3)

        assertEquals("Coldplay", mix.first().artist)
        assertFalse(mix.any { it.artist == "Muse" })
    }

    private fun candidate(id: String, artist: String, title: String) = TrackCandidate(
        providerId = "youtube",
        id = id,
        title = title,
        artist = artist,
        detailUrl = "https://www.youtube.com/watch?v=$id",
        isOfficial = true
    )

    private fun TrackCandidate.toTrack() = track("youtube:$id", artist, title)

    private fun track(id: String, artist: String, title: String) = Track(
        id = id,
        title = title,
        artist = artist,
        sourceId = "youtube",
        sourcePageUrl = "https://www.youtube.com/watch?v=${id.removePrefix("youtube:")}",
        playbackType = PlaybackType.DIRECT_STREAM,
        streamUrl = "aura-youtube://video/${id.removePrefix("youtube:")}"
    )
}

private class RecommendationPlugin(
    private val candidates: List<TrackCandidate>
) : MusicPlugin {
    override val id = "youtube"
    override val displayName = "Fixture YouTube"
    override val capabilities = setOf(
        PluginCapability.SEARCH,
        PluginCapability.STREAM,
        PluginCapability.RELATED
    )
    override val priority = 200

    override suspend fun search(request: MusicSearchRequest) = PluginResult.Success(candidates)

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> =
        PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "custom resolver is used")

    override suspend fun getRelated(track: Track) = PluginResult.Success(candidates)
}
