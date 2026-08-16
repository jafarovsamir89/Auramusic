package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEditorTest {
    private val one = track("one")
    private val two = track("two")
    private val three = track("three")
    private val four = track("four")

    @Test
    fun playNextMovesExistingTrackWithoutChangingCurrentTrack() {
        val result = QueueEditor.playNext(listOf(one, two, three, four), 2, one)

        assertEquals(listOf(two, three, one, four), result.tracks)
        assertEquals(1, result.currentIndex)
        assertEquals(three.id, result.tracks[result.currentIndex].id)
    }

    @Test
    fun addToEndDeduplicatesAndPreservesCurrentTrack() {
        val result = QueueEditor.addToEnd(listOf(one, two, three), 1, one)

        assertEquals(listOf(two, three, one), result.tracks)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun moveAndRemovePreservePlayingIdentity() {
        val moved = QueueEditor.move(listOf(one, two, three), 1, 2, 0)
        assertEquals(listOf(three, one, two), moved.tracks)
        assertEquals(2, moved.currentIndex)

        val removed = QueueEditor.remove(moved.tracks, moved.currentIndex, three.id)
        assertEquals(listOf(one, two), removed.tracks)
        assertEquals(1, removed.currentIndex)
    }

    @Test
    fun currentTrackCannotBeRemovedAndClearKeepsIt() {
        val unchanged = QueueEditor.remove(listOf(one, two, three), 1, two.id)
        assertEquals(listOf(one, two, three), unchanged.tracks)

        val cleared = QueueEditor.clear(unchanged.tracks, unchanged.currentIndex)
        assertEquals(listOf(two), cleared.tracks)
        assertEquals(0, cleared.currentIndex)
    }

    @Test
    fun removeLastKeepsCurrentTrackIndexValid() {
        val result = QueueEditor.removeLast(listOf(one, two, three), 1)

        assertEquals(listOf(one, two), result.tracks)
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun repeatModeCyclesThroughAllSupportedStates() {
        assertEquals(AuraRepeatMode.ONE, AuraRepeatMode.OFF.next())
        assertEquals(AuraRepeatMode.ALL, AuraRepeatMode.ONE.next())
        assertEquals(AuraRepeatMode.OFF, AuraRepeatMode.ALL.next())
    }

    private fun track(id: String) = Track(
        id = id,
        title = id,
        artist = "artist",
        sourceId = "test",
        sourcePageUrl = "https://example.test/$id",
        playbackType = PlaybackType.DIRECT_STREAM,
        streamUrl = "https://example.test/$id.mp3"
    )
}
