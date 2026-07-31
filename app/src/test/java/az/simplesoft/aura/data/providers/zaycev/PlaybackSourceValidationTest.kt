package az.simplesoft.aura.data.providers.zaycev

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceValidationTest {
    @Test
    fun validatesRangeResponseAndRejectsFailure() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setBody("x"))
            server.enqueue(MockResponse().setResponseCode(403))
            val client = ZaycevSearchClient(baseUrl = server.url("/").toString())

            assertTrue(client.validatePlayable(server.url("/ok").toString(), mapOf("Referer" to "https://zaycev.net/")))
            assertFalse(client.validatePlayable(server.url("/blocked").toString(), emptyMap()))
        }
    }
}
