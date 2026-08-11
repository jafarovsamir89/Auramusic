package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun preservesSearchSafeArtistSpelling() {
        val normalized = TextNormalizer.normalize("Включи Röya — Aygün")

        assertEquals("Включи Röya — Aygün", normalized.searchSafeText)
        assertEquals("включи roya aygun", normalized.normalizedText)
        assertTrue(normalized.tokens.containsAll(listOf("roya", "aygun")))
    }

    @Test
    fun foldsAzerbaijaniAndAsciiSpeechFormsForMatching() {
        assertEquals("novbeti mahni qos", TextNormalizer.normalizeForMatching("növbəti mahnı qoş"))
        assertEquals("novbeti mahni qos", TextNormalizer.normalizeForMatching("nobeti mahni qos"))
    }

    @Test
    fun detectsMixedRussianAndArtistAsRussian() {
        assertEquals(AssistantLanguage.RUSSIAN, AssistantLanguage.detect("Включи Linkin Park Numb"))
    }

    @Test
    fun detectsAsciiAzerbaijaniSpeechAsAzerbaijani() {
        assertEquals(AssistantLanguage.AZERBAIJANI, AssistantLanguage.detect("nobeti mahnini qos"))
        assertEquals(AssistantLanguage.AZERBAIJANI, AssistantLanguage.detect("Linkin Park-dan Numb qos"))
    }
}
