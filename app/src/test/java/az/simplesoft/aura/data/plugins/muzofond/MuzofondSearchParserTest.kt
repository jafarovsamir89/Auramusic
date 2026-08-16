package az.simplesoft.aura.data.plugins.muzofond

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuzofondSearchParserTest {
    @Test
    fun parsesTrackMetadataAndDirectAudioUrl() {
        val html = """
            <ul class="mainSongs unstyled songs" data-type="tracks">
              <li class="item" data-id="2710600">
                <div class="actions">
                  <ul><li class="play" data-id="2710600" data-url="https://dl3s4.muzofond.fm/encoded-audio"></li></ul>
                </div>
                <div class="trackTitle">
                  <a href="https://muzofond.fm/collections/artists/adele"><span class="artist">Адель</span></a>
                  — <a href="https://muzofond.fm/track/7446361" data-trackLink><span class="track">Hello</span></a>
                </div>
                <span class="duration">04:56</span>
              </li>
            </ul>
        """.trimIndent()

        val result = MuzofondSearchParser().parseSearch(html, 10)

        assertEquals(1, result.size)
        assertEquals("7446361", result.single().id)
        assertEquals("Hello", result.single().title)
        assertEquals("Адель", result.single().artist)
        assertEquals("https://muzofond.fm/track/7446361", result.single().detailUrl)
        assertEquals("https://dl3s4.muzofond.fm/encoded-audio", result.single().streamUrl)
        assertEquals(296_000L, result.single().durationMs)
    }

    @Test
    fun ignoresItemsWithoutMuzofondAudioOrTrackPage() {
        val html = """
            <li class="item">
              <li class="play" data-url="https://example.com/song.mp3"></li>
              <span class="artist">Artist</span><span class="track">Song</span>
            </li>
        """.trimIndent()

        try {
            MuzofondSearchParser().parseSearch(html, 10)
            throw AssertionError("Expected no-result failure")
        } catch (error: MuzofondPluginException) {
            assertEquals(MuzofondFailureReason.NOT_FOUND, error.reason)
        }
    }
}
