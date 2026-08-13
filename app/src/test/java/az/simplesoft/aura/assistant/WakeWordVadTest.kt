package az.simplesoft.aura.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordVadTest {
    @Test
    fun `silence never opens recognizer`() {
        val vad = WakeWordVad()

        repeat(100) { index ->
            assertFalse(vad.onRms(rms = 80f, nowMs = 1_000L + index * 20L))
        }
    }

    @Test
    fun `speech must be sustained before opening recognizer`() {
        val vad = WakeWordVad()

        assertFalse(vad.onRms(2_000f, 1_000L))
        assertFalse(vad.onRms(2_000f, 1_100L))
        assertTrue(vad.onRms(2_000f, 1_340L))
        assertFalse(vad.onRms(2_000f, 1_360L))
    }

    @Test
    fun `reset allows a second speech segment`() {
        val vad = WakeWordVad()

        assertFalse(vad.onRms(2_000f, 1_000L))
        assertTrue(vad.onRms(2_000f, 1_340L))
        vad.reset()

        assertFalse(vad.onRms(2_000f, 2_000L))
        assertTrue(vad.onRms(2_000f, 2_340L))
    }
}
