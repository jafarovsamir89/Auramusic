package az.simplesoft.aura.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraAiEngineTest {

    @Test
    fun `configured AI agent decides every utterance before local rules`() = runBlocking {
        val remote = RecordingRemoteAdapter(
            reply = AssistantReply(
                intent = MusicIntent.Unknown,
                text = "Что именно поставить?",
                source = AssistantSource.DEEPSEEK
            )
        )
        val engine = engine(remote)

        val result = engine.respond("Поставь пожалуйста", AuraAiContext(hourOfDay = 12))

        assertTrue(remote.called)
        assertEquals(MusicIntent.Unknown, result.intent)
        assertEquals(AssistantSource.DEEPSEEK, result.source)
    }

    @Test
    fun `local commands are only a fallback when remote agent fails`() = runBlocking {
        val remote = RecordingRemoteAdapter(failure = IllegalStateException("offline"))
        val engine = engine(remote)

        val result = engine.respond("пауза", AuraAiContext(hourOfDay = 12))

        assertTrue(remote.called)
        assertEquals(MusicIntent.Pause, result.intent)
        assertEquals(AssistantSource.FALLBACK, result.source)
    }

    private fun engine(remote: RemoteAssistantAdapter) = AuraAiEngine(
        local = LocalIntentEngine(),
        remote = remote,
        memory = CompactAssistantMemory(TestMemoryPersistence())
    )
}

private class RecordingRemoteAdapter(
    private val reply: AssistantReply? = null,
    private val failure: Throwable? = null
) : RemoteAssistantAdapter {
    var called = false
    override val isAvailable = true

    override suspend fun reason(
        input: String,
        context: AuraAiContext,
        memory: AssistantMemorySnapshot,
        language: AssistantLanguage
    ): AssistantReply {
        called = true
        failure?.let { throw it }
        return requireNotNull(reply)
    }
}

private class TestMemoryPersistence : AssistantMemoryPersistence {
    private var value: String? = null
    override suspend fun read(): String? = value
    override suspend fun write(value: String) { this.value = value }
}
