package az.simplesoft.aura.domain.playlist

import az.simplesoft.aura.data.providers.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldPlaylistCandidateMatcherTest {
    @Test
    fun rejectsUnrelatedFallbackInsteadOfPlayingIt() {
        val candidates = listOf(
            candidate("BTS", "SWIM", "bts-video"),
            candidate("Miri Yusif", "Ağ Qarğa", "miri-video")
        )

        val result = WorldPlaylistCandidateMatcher.rank(
            WorldPlaylistItem(1, "Miri Yusif", "Ağ Qarğa"),
            candidates
        )

        assertEquals(listOf("miri-video"), result.map(TrackCandidate::id))
    }

    private fun candidate(artist: String, title: String, id: String) = TrackCandidate(
        providerId = "youtube",
        id = id,
        title = title,
        artist = artist,
        detailUrl = "https://youtube.test/$id"
    )
}
