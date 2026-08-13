package az.simplesoft.aura.data

import az.simplesoft.aura.domain.music.RadioPlaybackSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RadioBrowserProviderTest {
    @Test
    fun `countries are parsed and invalid codes are ignored`() {
        val countries = RadioBrowserProvider.parseCountries(
            """[{"name":"AZ","stationcount":12},{"name":"USA","stationcount":99}]""",
            Locale.ENGLISH
        )

        assertEquals(listOf("AZ"), countries.map(RadioCountry::code))
        assertEquals("Azerbaijan", countries.single().name)
        assertEquals(12, countries.single().stationCount)
    }

    @Test
    fun `stations keep only unique secure playable streams`() {
        val stations = RadioBrowserProvider.parseStations(
            """[
              {"stationuuid":"one","name":"Yurd FM","url_resolved":"https://radio.test/live","country":"Azerbaijan","tags":"pop,music","codec":"MP3","bitrate":192,"clickcount":5},
              {"stationuuid":"duplicate","name":"Duplicate","url_resolved":"https://radio.test/live"},
              {"stationuuid":"clear","name":"Cleartext","url_resolved":"http://radio.test/live"}
            ]"""
        )

        assertEquals(1, stations.size)
        assertEquals("Yurd FM", stations.single().title)
        assertEquals("https://radio.test/live", stations.single().streamUrl)
        assertEquals(stations.single().streamUrl, stations.single().sourcePageUrl)
        assertTrue(stations.single().artist.contains("192 kbps"))
        assertEquals(stations.single(), RadioPlaybackSelector.firstPlayable(stations))
    }

    @Test
    fun `radio selector cycles to adjacent station`() {
        val stations = RadioBrowserProvider.parseStations(
            """[
              {"stationuuid":"one","name":"One","url_resolved":"https://radio.test/one"},
              {"stationuuid":"two","name":"Two","url_resolved":"https://radio.test/two"},
              {"stationuuid":"three","name":"Three","url_resolved":"https://radio.test/three"}
            ]"""
        )

        assertEquals("two", RadioPlaybackSelector.next(stations, stations[0].id)?.id)
        assertEquals("three", RadioPlaybackSelector.previous(stations, stations[0].id)?.id)
    }
}
