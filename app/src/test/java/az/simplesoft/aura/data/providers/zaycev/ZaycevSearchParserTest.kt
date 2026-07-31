package az.simplesoft.aura.data.providers.zaycev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZaycevSearchParserTest {
    private val parser = ZaycevSearchParser()

    @Test
    fun parsesPublicSearchApiResponse() {
        val tracks = parser.parseApiJson(resource("search-results.json"))

        assertEquals(2, tracks.size)
        assertEquals("6714715", tracks.first().id)
        assertEquals("Капкан", tracks.first().title)
        assertEquals("Мот", tracks.first().artist)
        assertEquals(228_000L, tracks.first().durationMs)
        assertEquals("https://zaycev.net/pages/67147/6714715.shtml", tracks.first().detailUrl)
    }

    @Test
    fun fallsBackToPublicHtml() {
        val tracks = parser.parseHtml(resource("search-results.html"))

        assertEquals(1, tracks.size)
        assertEquals("Капкан", tracks.first().title)
        assertTrue(tracks.first().detailUrl.endsWith("/pages/67147/6714715.shtml"))
    }

    private fun resource(name: String): String = checkNotNull(
        javaClass.classLoader?.getResource("zaycev/$name")
    ).readText()
}
