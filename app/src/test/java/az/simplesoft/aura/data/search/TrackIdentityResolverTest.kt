package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.providers.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIdentityResolverTest {
    private val resolver = TrackIdentityResolver()

    @Test
    fun mergesSameTrackFromDifferentProviders() {
        val youtube = candidate("youtube", "yt", "Numb", 187_000)
        val zaycev = candidate("zaycev", "z", "Numb", 190_000)
        val ranked = listOf(
            RankedCandidate(youtube, .95, emptyList(), emptyList()),
            RankedCandidate(zaycev, .88, emptyList(), emptyList())
        )

        val unified = resolver.unify(ranked)

        assertEquals(1, unified.size)
        assertEquals(2, unified.first().alternatives.size)
        assertTrue(resolver.areSame(youtube, zaycev))
    }

    @Test
    fun keepsLiveVersionSeparateFromStudioTrack() {
        val studio = candidate("youtube", "studio", "Numb", 187_000)
        val live = candidate("youtube", "live", "Numb Live", 188_000)

        assertFalse(resolver.areSame(studio, live))
    }

    @Test
    fun rejectsLargeDurationMismatch() {
        val short = candidate("youtube", "short", "Numb", 187_000)
        val extended = candidate("zaycev", "long", "Numb", 230_000)

        assertFalse(resolver.areSame(short, extended))
    }

    @Test
    fun mergesOfficialMusicVideoWithSameStudioRecording() {
        val studio = candidate("zaycev", "one", "Numb", 190_000)
        val officialVideo = candidate("youtube", "two", "Numb (Official Music Video)", 187_000)

        assertTrue(resolver.areSame(studio, officialVideo))
    }

    private fun candidate(provider: String, id: String, title: String, duration: Long) = TrackCandidate(
        providerId = provider,
        id = id,
        title = title,
        artist = "Linkin Park",
        detailUrl = "https://example.test/$id",
        durationMs = duration
    )
}
