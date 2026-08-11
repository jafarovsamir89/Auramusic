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
