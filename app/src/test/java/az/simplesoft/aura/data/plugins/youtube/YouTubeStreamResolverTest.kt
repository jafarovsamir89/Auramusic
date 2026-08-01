package az.simplesoft.aura.data.plugins.youtube

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import az.simplesoft.aura.data.plugins.core.PluginFailureReason
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.providers.MusicSearchRequest
import kotlinx.coroutines.runBlocking

class YouTubeStreamResolverTest {
    @Test
    fun prefersOpusAndUsesUrlExpiryWithSafetyWindow() {
        val player = YouTubePlayerParser().parse(resource("youtube/player.json"))
        val resolver = YouTubeStreamResolver(
            validator = YouTubeStreamValidator { _, _ -> true },
            now = { 1_900_000_000_000L }
        )

        val selected = resolver.selectFormat(player.formats, player.expiresInSeconds)

        assertEquals(251, selected.format.itag)
        assertEquals(1_999_999_970_000L, selected.expiresAtEpochMs)
        assertTrue(selected.url.startsWith("https://"))
    }

    @Test
    fun validatesNextAudioFormatWhenPreferredOpusFails() = runBlocking {
        val player = YouTubePlayerParser().parse(resource("youtube/player.json"))
        val resolver = YouTubeStreamResolver(
            validator = YouTubeStreamValidator { url, _ -> "fixture-mp4" in url }
        )

        val selected = resolver.selectValidatedFormat(player.formats, player.expiresInSeconds, emptyMap())

        assertEquals(140, selected.format.itag)
    }

    @Test
    fun failsClosedForUnknownSignatureTransformation() {
        val url = URLEncoder.encode("https://rr.example.googlevideo.com/videoplayback?id=test", StandardCharsets.UTF_8)
        val format = YouTubeAudioFormat(
            itag = 251,
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 128_000,
            signatureCipher = "url=$url&sp=sig&s=encrypted"
        )

        val error = runCatching { YouTubeSignatureResolver().resolve(format) }
            .exceptionOrNull() as YouTubePluginException

        assertEquals(YouTubeFailureReason.SIGNATURE_UNSUPPORTED, error.reason)
    }

    @Test
    fun anonymousCookieStoreIsOffByDefault() {
        val store = YouTubeCookieStore()
        store.update("CONSENT", "YES")

        assertEquals(null, store.requestHeader())
    }

    @Test
    fun disabledPluginIsNeverCalledByProviderManager() = runBlocking {
        val manager = ProviderManager(setOf(YouTubeMusicPlugin()))
        manager.setEnabled(YouTubeMusicPlugin.ID, false)

        val result = manager.search(
            MusicSearchRequest("Numb", preferredProviderId = YouTubeMusicPlugin.ID)
        )

        assertTrue(result is az.simplesoft.aura.data.plugins.core.PluginResult.Failure)
        assertEquals(
            PluginFailureReason.DISABLED,
            (result as az.simplesoft.aura.data.plugins.core.PluginResult.Failure).reason
        )
    }

    private fun resource(path: String): String = checkNotNull(javaClass.classLoader?.getResource(path)).readText()
}
