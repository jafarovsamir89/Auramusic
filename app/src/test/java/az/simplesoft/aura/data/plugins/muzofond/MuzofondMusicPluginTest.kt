package az.simplesoft.aura.data.plugins.muzofond

import az.simplesoft.aura.data.plugins.core.PluginFailureReason
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.providers.TrackCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuzofondMusicPluginTest {
    @Test
    fun rejectsNonMuzofondAudioUrl() = runBlocking {
        val plugin = MuzofondMusicPlugin()
        val result = plugin.resolve(
            TrackCandidate(
                providerId = MuzofondMusicPlugin.ID,
                id = "bad",
                title = "Song",
                artist = "Artist",
                detailUrl = "https://muzofond.fm/track/bad",
                playbackToken = "https://example.com/song.mp3"
            )
        )

        assertTrue(result is PluginResult.Failure)
        assertEquals(PluginFailureReason.NOT_PLAYABLE, (result as PluginResult.Failure).reason)
    }
}
