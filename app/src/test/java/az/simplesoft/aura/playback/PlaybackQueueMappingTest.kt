package az.simplesoft.aura.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueMappingTest {
    @Test
    fun skipsAuraPlaceholderWhenSelectingQueueTrack() {
        assertEquals(0, PlaybackQueueMapping.mediaIndexForTrack(listOf("song-1", "song-2"), "song-1"))
        assertEquals(1, PlaybackQueueMapping.mediaIndexForTrack(listOf("song-1", "song-2"), "song-2"))
        assertEquals(-1, PlaybackQueueMapping.mediaIndexForTrack(listOf("song-1"), "aura-placeholder"))
    }
}
