package az.simplesoft.aura.data.plugins.muzofond

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MuzofondCollectionsParserTest {
    @Test
    fun parsesCollectionCardsAndDirectTracks() {
        val parser = MuzofondCollectionsParser()
        val collections = parser.parseCollections(
            """
            <div class="item" data-id="8170">
              <a href="/collections/top/хиты">
                <img data-src="/img/collections/400311_big.jpg" alt="Хиты 2026">
                <span class="title">Хиты 2026</span>
              </a>
            </div>
            """.trimIndent(),
            10
        )
        assertEquals(1, collections.size)
        assertEquals("Хиты 2026", collections.single().title)
        assertEquals("https://muzofond.fm/collections/top/хиты", collections.single().collectionUrl)

        val playlist = parser.parseCollection(
            """
            <h1>Хиты 2026</h1>
            <ul class="mainSongs">
              <li class="item" data-id="9940112" data-duration="121" data-img="/img/albums/508036_small.jpg">
                <div class="actions"><ul><li class="play" data-url="https://dl3s5.muzofond.fm/encoded"></li></ul></div>
                <div class="trackTitle">
                  <span class="artist">Kalvados</span>
                  <a href="https://muzofond.fm/track/14675873" data-trackLink><span class="track">Где ты была</span></a>
                </div>
              </li>
            </ul>
            """.trimIndent(),
            "https://muzofond.fm/collections/top/хиты",
            50
        )
        val item = playlist.items.single()
        assertEquals("Kalvados", item.artist)
        assertEquals("Где ты была", item.title)
        assertEquals(MuzofondMusicPlugin.ID, item.providerId)
        assertEquals("https://dl3s5.muzofond.fm/encoded", item.playbackToken)
        assertEquals(121_000L, item.durationMs)
        assertNotNull(item.sourceUrl)
    }

    @Test
    fun parsesUniqueGenresFromPopularPage() {
        val genres = MuzofondCollectionsParser().parseGenres(
            """
            <nav><a href="/popular">Жанры</a></nav>
            <ul><li><a href="/popular/rock">Русский рок</a></li><li><a href="/popular/rock">Русский рок</a></li><li><a href="/popular/jazz">Джаз</a></li></ul>
            """.trimIndent(),
            20
        )
        assertEquals(listOf("Русский рок", "Джаз"), genres.map { it.title })
        assertEquals("https://muzofond.fm/popular/rock", genres.first().collectionUrl)
    }

    @Test
    fun parsesGenrePageRowsOutsideMainSongsList() {
        val playlist = MuzofondCollectionsParser().parseCollection(
            """
            <h1>Зарубежный рок</h1>
            <div class="content"><li class="item" data-id="1" data-duration="218">
              <div class="actions"><ul><li class="play" data-url="https://dl3s5.muzofond.fm/encoded"></li></ul></div>
              <div class="trackTitle"><span class="artist">The Hu</span><a data-trackLink href="https://muzofond.fm/track/1"><span class="track">Grey Hun</span></a></div>
            </li></div>
            """.trimIndent(),
            "https://muzofond.fm/popular/rock",
            50
        )
        assertEquals("Grey Hun", playlist.items.single().title)
    }
}
