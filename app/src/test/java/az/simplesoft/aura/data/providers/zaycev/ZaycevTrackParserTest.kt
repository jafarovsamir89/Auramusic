package az.simplesoft.aura.data.providers.zaycev

import az.simplesoft.aura.data.providers.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class ZaycevTrackParserTest {
    @Test
    fun enrichesCandidateFromNextData() {
        val original = TrackCandidate(
            providerId = ZaycevProvider.ID,
            id = "6714715",
            title = "Капкан",
            artist = "Мот",
            detailUrl = "https://zaycev.net/pages/67147/6714715.shtml"
        )

        val enriched = ZaycevTrackParser().enrich(resource("track-page.html"), original)

        assertEquals(228_000L, enriched.durationMs)
        assertEquals(320, enriched.bitrateKbps)
        assertEquals("https://cdnimg.zaycev.net/capcan-large.webp", enriched.artworkUrl)
    }

    private fun resource(name: String): String = checkNotNull(
        javaClass.classLoader?.getResource("zaycev/$name")
    ).readText()
}
