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

    @Test
    fun `safe playback controls are classified without the UI`() {
        assertEquals(MusicIntent.Shuffle, classifier.classify("shuffle queue").intent)
        assertEquals(MusicIntent.Repeat, classifier.classify("repeat track").intent)
        assertEquals(MusicIntent.NowPlaying, classifier.classify("now playing").intent)
        assertEquals(MusicIntent.Unmute, classifier.classify("включи звук").intent)
    }

    @Test
    fun `user state controls are durable UI commands`() {
        val like = classifier.classify("like this")
        assertEquals(MusicIntent.Like, like.intent)
        assertTrue(like.requiresUi)
        assertEquals(MusicIntent.AutoContinue(true), classifier.classify("enable auto continue").intent)
    }
}
