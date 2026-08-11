package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraVoiceEngineTest {
    @Test
    fun speechProcessorRemovesTechnicalNoiseAndPronouncesNames() {
        val processed = SpeechTextProcessor().process(
            "Сейчас играет Röya — Linkin Park! https://example.com 😊",
            AssistantLanguage.RUSSIAN
        )
        assertTrue(processed.contains("Ройя"))
        assertTrue(processed.contains("Линкин Парк"))
        assertTrue(!processed.contains("https://"))
    }

    @Test
    fun speechProcessorMakesPercentagesAndAdditionalArtistNamesAudible() {
        val processed = SpeechTextProcessor().process(
            "Поставь 50% громкости: Eyyub Yaqubov, Brilliant Dadaşova, AC/DC и R.E.M.",
            AssistantLanguage.RUSSIAN
        )
        assertTrue(processed.contains("50 процентов"))
        assertTrue(processed.contains("Эйюб Ягубов"))
        assertTrue(processed.contains("Бриллиант Дадашова"))
        assertTrue(processed.contains("эй си ди си"))
        assertTrue(processed.contains("ар и эм"))
    }

    @Test
    fun intentRegistryUsesPriorityWhenConfidenceTies() {
        val registry = IntentRegistry(
            listOf(
                IntentPattern("low", examples = listOf("запусти"), priority = 1),
                IntentPattern("high", examples = listOf("запусти"), priority = 9)
            )
        )
        val candidates = registry.candidates(TextNormalizer.normalize("запусти"))
        assertEquals("high", candidates.first().intentId)
        assertEquals(2, candidates.size)
    }

    @Test
    fun stateMachineSupportsBargeIn() {
        val state = VoiceTurnStateMachine()
        state.transition(VoiceSessionState.LISTENING)
        state.transition(VoiceSessionState.TRANSCRIBING)
        state.transition(VoiceSessionState.UNDERSTANDING)
        state.transition(VoiceSessionState.SPEAKING)
        state.bargeIn()
        assertEquals(VoiceSessionState.LISTENING, state.state)
        assertEquals(1, state.metrics.bargeInCount)
    }
}
