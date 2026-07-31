package az.simplesoft.aura.data.providers.zaycev

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.ProviderResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZaycevProviderIntegrationTest {
    @Test
    fun publicSearchFindsExactTrack() = runBlocking {
        val result = ZaycevProvider().search(MusicSearchRequest("Мот Капкан", limit = 5, autoPlay = false))

        assertTrue(result is ProviderResult.Success)
        val candidates = (result as ProviderResult.Success).value
        assertEquals("Капкан", candidates.first().title)
        assertEquals("Мот", candidates.first().artist)
    }
}
