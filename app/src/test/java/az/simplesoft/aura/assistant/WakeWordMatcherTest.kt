package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeWordMatcherTest {
    @Test
    fun `extracts command following Russian wake word`() {
        assertEquals("включи руки вверх", WakeWordMatcher.commandAfterWakeWord("Аура, включи руки вверх"))
    }

    @Test
    fun `recognizes Latin spelling and a standalone wake word`() {
        assertEquals("", WakeWordMatcher.commandAfterWakeWord("AURA"))
        assertEquals("play Imagine", WakeWordMatcher.commandAfterWakeWord("aura: play Imagine"))
    }

    @Test
    fun `does not activate on unrelated speech`() {
        assertNull(WakeWordMatcher.commandAfterWakeWord("поставь музыку"))
    }
}
