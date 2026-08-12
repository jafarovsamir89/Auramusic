package az.simplesoft.aura.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraAiEngineTest {

    @Test
    fun `local conversation is answered without a network adapter`() = runBlocking {
        val engine = engine()

        val result = engine.respond("Какой сегодня день?", AuraAiContext(hourOfDay = 12))

        assertEquals(MusicIntent.Unknown, result.intent)
        assertEquals(AssistantSource.LOCAL, result.source)
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun `exact player command does not wait for the agent`() = runBlocking {
        val engine = engine()

        val result = engine.respond("пауза", AuraAiContext(hourOfDay = 12))

        assertEquals(MusicIntent.Pause, result.intent)
        assertEquals(AssistantSource.LOCAL, result.source)
    }

    @Test
    fun `companion remembers a name locally`() = runBlocking {
        val engine = engine()

        engine.respond("Меня зовут Лейла", AuraAiContext(hourOfDay = 12))
        val result = engine.respond("Что ты помнишь?", AuraAiContext(hourOfDay = 12))

        assertTrue(result.text.contains("Лейла"))
    }

    private fun engine() = AuraAiEngine(
        local = LocalIntentEngine(),
        memory = CompactAssistantMemory(TestMemoryPersistence())
    )
}

private class TestMemoryPersistence : AssistantMemoryPersistence {
    private var value: String? = null
    override suspend fun read(): String? = value
    override suspend fun write(value: String) { this.value = value }
}
