package az.simplesoft.aura.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SileroAzerbaijaniTokenizerTest {
    @Test
    fun `adds Silero boundary tokens and maps Azerbaijani latin alphabet`() {
        val tokens = SileroAzerbaijaniTokenizer.encode("Salam, necəsən?")

        assertEquals(2L, tokens.first())
        assertEquals(1L, tokens.last())
        assertTrue(tokens.size > 10)
    }

    @Test
    fun `maps dotted and dotless Azerbaijani i differently`() {
        val dotted = SileroAzerbaijaniTokenizer.encode("i")
        val dotless = SileroAzerbaijaniTokenizer.encode("ı")

        assertTrue(dotted[1] != dotless[1])
        assertArrayEquals(longArrayOf(2L, dotted[1], 1L), dotted)
    }
}
