package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {
    @Test
    fun ranksArtistAndTitleAboveLooseMatch() {
        val exact = candidate("1", "Капкан", "Мот")
        val loose = candidate("2", "Капкан любви", "Другой артист")

        val ranked = TrackMatcher.rank(MusicSearchRequest("Мот Капкан"), listOf(loose, exact))

        assertEquals("1", ranked.first().id)
        assertTrue(ranked.first().confidence >= 0.72)
    }

    @Test
    fun normalizesCasePunctuationAndYo() {
        assertTrue(TrackMatcher.similarity("Всё, как есть!", "все как есть") > 0.95)
    }

    @Test
    fun toleratesSmallTypo() {
        assertTrue(TrackMatcher.similarity("капкан", "капкн") > 0.75)
    }

    @Test
    fun supportsCyrillicLatinTransliteration() {
        assertTrue(TrackMatcher.similarity("Мот Капкан", "Mot Kapkan") > 0.9)
    }

    private fun candidate(id: String, title: String, artist: String) = TrackCandidate(
        providerId = "youtube",
        id = id,
        title = title,
        artist = artist,
        detailUrl = "https://www.youtube.com/watch?v=$id"
    )
}
