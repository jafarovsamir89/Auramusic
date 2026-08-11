package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchingTest {
    @Test
    fun damerauDistanceHandlesTransposition() {
        assertEquals(1, FuzzyMatching.damerauLevenshtein("mahni", "manhi"))
        assertEquals(0, FuzzyMatching.damerauLevenshtein("nobeti", "nobeti"))
    }

    @Test
    fun typoStillScoresCloseToCommand() {
        assertTrue(FuzzyMatching.combined("пастав следующую", "поставь следующим") > 0.55)
        assertTrue(FuzzyMatching.combined("sesi artir", "səsi artır") > 0.90)
    }
}
