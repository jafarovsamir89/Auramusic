package az.simplesoft.aura.data.plugins

import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.LegacyMusicPluginAdapter
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.plugins.local.LocalMusicPlugin
import az.simplesoft.aura.data.plugins.radio.RadioMusicPlugin
import az.simplesoft.aura.data.providers.MusicProvider
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.SearchMediaKind
import az.simplesoft.aura.data.providers.TrackCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginAdaptersTest {
    @Test
    fun legacyAdapterPreservesProviderResultAndIdentity() = runBlocking {
        val provider = object : MusicProvider {
            override val id = "legacy"
            override val displayName = "Legacy"
            override val priority = 77
            override suspend fun search(request: MusicSearchRequest) = ProviderResult.Success(
                listOf(candidate("legacy", "one", "Numb")),
                mapOf("stage" to "fixture")
            )
            override suspend fun resolve(candidate: TrackCandidate): ProviderResult<PlayableSource> =
                ProviderResult.Failure(az.simplesoft.aura.data.providers.ProviderFailureReason.NOT_PLAYABLE, "unused")
            override suspend fun validate(source: PlayableSource) = true
        }

        val result = LegacyMusicPluginAdapter(provider).search(MusicSearchRequest("Numb"))

        assertTrue(result is PluginResult.Success)
        result as PluginResult.Success
        assertEquals("legacy", result.value.single().providerId)
        assertEquals("fixture", result.diagnostics["stage"])
    }

    @Test
    fun concreteTrackSearchDoesNotCallRadio() = runBlocking {
        var radioCalled = false
        val local = LocalMusicPlugin {
            listOf(localTrack("local-1", "Numb", "Linkin Park"))
        }
        val radio = RadioMusicPlugin(
            searchStations = { _, _ ->
                radioCalled = true
                listOf(radioTrack())
            },
            popularStations = { emptyList() }
        )
        val manager = ProviderManager(setOf(local, radio))

        val result = manager.search(
            MusicSearchRequest("Linkin Park Numb", artist = "Linkin Park", title = "Numb")
        )

        assertTrue(result is PluginResult.Success)
        assertFalse(radioCalled)
        assertEquals("local", (result as PluginResult.Success).value.single().providerId)
    }

    @Test
    fun explicitRadioSearchCallsOnlyRadioPlugin() = runBlocking {
        var localCalled = false
        val local = LocalMusicPlugin {
            localCalled = true
            listOf(localTrack("local-1", "Relax", "Artist"))
        }
        val radio = RadioMusicPlugin(
            searchStations = { _, _ -> listOf(radioTrack()) },
            popularStations = { emptyList() }
        )
        val manager = ProviderManager(setOf(local, radio))

        val result = manager.search(
            MusicSearchRequest("Relax FM", mediaKind = SearchMediaKind.RADIO)
        )

        assertTrue(result is PluginResult.Success)
        assertFalse(localCalled)
        assertEquals("radio_browser", (result as PluginResult.Success).value.single().providerId)
    }

    private fun candidate(provider: String, id: String, title: String) = TrackCandidate(
        providerId = provider,
        id = id,
        title = title,
        artist = "Linkin Park",
        detailUrl = "https://example.test/$id"
    )

    private fun localTrack(id: String, title: String, artist: String) = Track(
        id = id,
        title = title,
        artist = artist,
        sourceId = "local",
        sourcePageUrl = "content://media/$id",
        playbackType = PlaybackType.LOCAL,
        streamUrl = "content://media/$id"
    )

    private fun radioTrack() = Track(
        id = "station",
        title = "Relax FM",
        artist = "Radio",
        sourceId = "radio_browser",
        sourcePageUrl = "https://radio.example",
        playbackType = PlaybackType.DIRECT_STREAM,
        streamUrl = "https://stream.example/radio"
    )
}
