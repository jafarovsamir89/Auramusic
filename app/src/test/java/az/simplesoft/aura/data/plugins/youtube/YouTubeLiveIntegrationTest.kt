package az.simplesoft.aura.data.plugins.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeLiveIntegrationTest {
    @Test
    fun anonymousSearchPlayerAndRangeValidation() = runBlocking {
        assumeTrue(System.getenv("AURA_YOUTUBE_LIVE_TEST") == "1")
        val context = YouTubeRequestContext(language = "en", countryCode = "US")
        val search = YouTubeSearchClient(requestContext = context)
        val playerClient = YouTubePlayerClient(requestContext = context)
        val parser = YouTubePlayerParser()
        val resolver = YouTubeStreamResolver(requestContext = context)

        val result = search.search("Linkin Park Numb", 5)
        val numb = result.first { it.title.contains("Numb", ignoreCase = true) }
        val player = parser.parse(playerClient.player(numb.videoId))
        val headers = mapOf(
            "User-Agent" to context.userAgent,
            "Referer" to "${YouTubeSelectors.WATCH_URL}?v=${numb.videoId}"
        )
        val selected = resolver.selectValidatedFormat(player.formats, player.expiresInSeconds, headers)
        val related = YouTubeSearchParser().parseJson(playerClient.related(numb.videoId), 10)

        assertEquals(numb.videoId, player.videoId)
        assertTrue(selected.format.mimeType.startsWith("audio/"))
        assertTrue(related.isNotEmpty())
    }
}
