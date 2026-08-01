package az.simplesoft.aura.data.plugins.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSearchParserTest {
    private val parser = YouTubeSearchParser()

    @Test
    fun parsesVideoAndMusicRenderersFromSanitizedFixture() {
        val html = resource("youtube/search.html")

        val results = parser.parseHtml(html, 10)

        assertEquals(2, results.size)
        assertEquals("kXYiU_JCYtU", results[0].videoId)
        assertEquals("Linkin Park", results[0].artist)
        assertEquals(187_000L, results[0].durationMs)
        assertEquals(2_300_000_000L, results[0].popularity)
        assertTrue(results[0].isOfficial)
        assertEquals("dQw4w9WgXcQ", results[1].videoId)
        assertEquals(YouTubeResultType.SONG, results[1].type)
        assertEquals(213_000L, results[1].durationMs)
    }

    @Test
    fun classifiesConsentPageWithoutTryingToParseIt() {
        val error = runCatching {
            parser.parseHtml("<a href='https://consent.youtube.com'>continue</a>", 5)
        }.exceptionOrNull() as YouTubePluginException

        assertEquals(YouTubeFailureReason.ACCESS_RESTRICTED, error.reason)
    }

    @Test
    fun decodesHexEscapedMobileInitialData() {
        val html = "<script>var ytInitialData = '\\x7b\\x22contents\\x22:\\x5b\\x5d\\x7d';</script>"

        val json = parser.extractAssignedJson(html, YouTubeSelectors.INITIAL_DATA_MARKER)

        assertEquals("{\"contents\":[]}", json)
    }

    private fun resource(path: String): String = checkNotNull(javaClass.classLoader?.getResource(path)).readText()
}
