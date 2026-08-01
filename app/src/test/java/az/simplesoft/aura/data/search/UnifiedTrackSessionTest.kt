package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedTrackSessionTest {
    @Test
    fun triesSameTrackAlternativeBeforeNextSongAndKeepsCanonicalId() {
        val youtube = candidate("youtube", "video-one", "Numb")
        val zaycev = candidate("zaycev", "track-one", "Numb")
        val next = candidate("youtube", "video-two", "Faint")
        val session = UnifiedTrackSession()
        val primary = session.replace(
            listOf(
                unified("youtube:video-one", youtube, zaycev),
                unified("youtube:video-two", next)
            )
        )

        val order = session.fallbackOrder(youtube, primary, 4)
        val resolvedFromZaycev = session.canonicalize(zaycev, track("zaycev:track-one", "zaycev"))

        assertEquals(listOf("youtube", "zaycev", "youtube"), order.map(TrackCandidate::providerId))
        assertEquals("youtube:video-one", resolvedFromZaycev.id)
        assertEquals("zaycev", resolvedFromZaycev.sourceId)
    }

    @Test
    fun supportsReverseZaycevToYouTubeFallback() {
        val zaycev = candidate("zaycev", "track-one", "Numb")
        val youtube = candidate("youtube", "video-one", "Numb")
        val session = UnifiedTrackSession()
        val primary = session.replace(listOf(unified("zaycev:track-one", zaycev, youtube)))

        val order = session.fallbackOrder(zaycev, primary, 4)

        assertEquals(listOf("zaycev", "youtube"), order.map(TrackCandidate::providerId))
        assertEquals("zaycev:track-one", session.canonicalId(youtube))
    }

    private fun unified(id: String, vararg candidates: TrackCandidate) = UnifiedTrack(
        identity = TrackIdentity("numb", "linkin park", 190),
        metadata = track(id, candidates.first().providerId),
        alternatives = candidates.toList(),
        score = .9
    )

    private fun candidate(provider: String, id: String, title: String) = TrackCandidate(
        providerId = provider,
        id = id,
        title = title,
        artist = "Linkin Park",
        detailUrl = "https://example.test/$provider/$id",
        durationMs = 190_000L
    )

    private fun track(id: String, provider: String) = Track(
        id = id,
        title = "Numb",
        artist = "Linkin Park",
        sourceId = provider,
        sourcePageUrl = "https://example.test/$id",
        playbackType = PlaybackType.DIRECT_STREAM,
        streamUrl = "https://stream.example/$id"
    )
}
