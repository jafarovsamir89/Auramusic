package az.simplesoft.aura.data.plugins.vol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolSearchParserTest {
    private val parser = VolSearchParser()

    @Test
    fun parsesVolSearchRowsIntoArtistTitleAndStableDetailUrl() {
        val html = """
            <div class="playlis">
              <p><i class="btndown"><a href="/aygun-kazimova-meclis-mp3-yukle-13991.html" title="Aygün Kazımova - Məclis"></a></i><i class="btnplay" mpdemo="13991_123"></i> Aygün Kazımova - Məclis</p>
              <p><i class="btndown"><a href="/sezen-aksu-gulumse-mp3-yukle-42.html" title="Sezen Aksu - Gülümse"></a></i><i class="btnplay" mpdemo="42_456"></i> Sezen Aksu - Gülümse</p>
            </div>
        """.trimIndent()

        val result = parser.parseSearch(html, 20)

        assertEquals(2, result.size)
        assertEquals("13991", result.first().id)
        assertEquals("Aygün Kazımova", result.first().artist)
        assertEquals("Məclis", result.first().title)
        assertEquals("https://vol.az/aygun-kazimova-meclis-mp3-yukle-13991.html", result.first().detailUrl)
    }

    @Test
    fun resolvesAuthorizedListenTokenAndAudioExpiry() {
        val detail = """
            <img id="coverimg" src="/images/fblogo.jpg" />
            <div class="musicline">Müddəti: <span>02:36</span></div>
            <button id="listenbut" data-dt="id=13991&amp;go=7&amp;lk=token" data-hs="signature"></button>
        """.trimIndent()

        val page = parser.parsePlayback(detail, "https://vol.az/track.html")
        assertTrue(page.streamUrl.startsWith("https://vol.az/ajax.php?&id=13991"))
        assertEquals(156_000L, page.durationMs)
        assertEquals("https://vol.az/images/fblogo.jpg", page.artworkUrl)

        val audio = parser.parseAjaxPlayback(
            "<audio><source src=\"https://vol.az/uplarx/song.mp3?st=token&amp;e=1786756117\" /></audio>"
        )
        assertEquals("https://vol.az/uplarx/song.mp3?st=token&e=1786756117", audio.streamUrl)
        assertEquals(1_786_756_117L, audio.expiresAt)
    }

    @Test
    fun parsesVolCategoryRowsWithoutSearchMarkup() {
        val html = """
            <div class="contentgray"><ul class="mp3ul">
              <li><a href="/tunzale-yeniden-baslayaq-mp3-yukle-13934.html" title="Tünzalə - Yenidən başlayaq">Tünzalə - Yenidən başlayaq</a> <i>108</i></li>
              <li><a href="/aygun-kazimova-karma-mp3-yukle-13898.html" title="Aygün Kazımova - Karma">Aygün Kazımova - Karma</a> <i>1194</i></li>
            </ul></div>
        """.trimIndent()

        val result = parser.parseCategory(html, 50)

        assertEquals(2, result.size)
        assertEquals("Tünzalə", result.first().artist)
        assertEquals("Yenidən başlayaq", result.first().title)
        assertEquals("13898", result.last().id)
    }
}
