package az.simplesoft.aura.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import kotlinx.coroutines.runBlocking

class ArtistArtworkLookupTest {
    @Test
    fun `generic catalog placeholders are not treated as covers`() {
        assertFalse(ArtistArtworkLookup.isUsable("https://vol.az/images/fblogo.jpg"))
        assertFalse(ArtistArtworkLookup.isUsable("https://example.com/placeholder.png"))
        assertTrue(ArtistArtworkLookup.isUsable("https://is1-ssl.mzstatic.com/image/thumb/123/600x600bb.jpg"))
    }

    @Test
    fun `falls back from exact title to artist when Vol spelling differs`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"resultCount\":0,\"results\":[]}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"resultCount":0,"results":[]}
        """.trimIndent()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"resultCount":1,"results":[{"artworkUrl100":"https://images.example/100x100bb.jpg"}]}
        """.trimIndent()))
        server.start()
        try {
            val lookup = ArtistArtworkLookup(OkHttpClient(), server.url("/search").toString())
            assertEquals("https://images.example/600x600bb.jpg", lookup.lookup("Aygün Kazımova", "Редкое название"))
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
