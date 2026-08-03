package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCommandClassifierTest {
    private val classifier = LocalCommandClassifier()

    @Test
    fun `simple imperative is safe for background execution`() {
        val result = classifier.classify("поставь на паузу")
        assertEquals(MusicIntent.Pause, result.intent)
        assertTrue(result.confidence >= .90f)
        assertFalse(result.negated)
        assertFalse(result.requiresUi)
    }

    @Test
    fun `negated command never executes`() {
        val result = classifier.classify("не ставь на паузу")
        assertTrue(result.intent == null || result.intent == MusicIntent.Pause)
        assertTrue(result.negated)
        assertTrue(result.requiresUi)
    }

    @Test
    fun `discussion and unrelated shuffle do not become player commands`() {
        assertTrue(classifier.classify("Я сказал ему остановиться").intent == null)
        assertTrue(classifier.classify("Перемешай фотографии").intent == null)
        assertTrue(classifier.classify("Слово пауза означает перерыв").intent == null)
    }

    @Test
    fun `music preference complaint is not an imperative`() {
        val result = classifier.classify("Мне не нравится, когда музыка останавливается")
        assertTrue(result.intent == null)
        assertTrue(result.requiresUi)
    }
}
