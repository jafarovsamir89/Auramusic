package az.simplesoft.aura.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackSourceRegistryTest {
    @Before
    fun setUp() = PlaybackSourceRegistry.clear()

    @After
    fun tearDown() = PlaybackSourceRegistry.clear()

    @Test
    fun keepsRecentHeadersAndEvictsOldTemporaryUris() {
        repeat(65) { index ->
            PlaybackSourceRegistry.register("https://stream.example/$index", mapOf("X-Index" to "$index"))
        }

        assertTrue(PlaybackSourceRegistry.headersFor("https://stream.example/0").isEmpty())
        assertEquals(
            "64",
            PlaybackSourceRegistry.headersFor("https://stream.example/64")["X-Index"]
        )
    }
}
