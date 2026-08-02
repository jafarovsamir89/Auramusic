package az.simplesoft.aura.assistant

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactAssistantMemoryTest {
    @Test
    fun `memory deduplicates durable facts and stays bounded`() = runBlocking {
        val persistence = InMemoryAssistantMemoryPersistence()
        var clock = 1_000L
        val memory = CompactAssistantMemory(persistence) { clock++ }

        repeat(100) { index ->
            memory.record(
                userText = "message-$index-" + "x".repeat(500),
                reply = AssistantReply(
                    intent = MusicIntent.Unknown,
                    text = "reply-$index-" + "y".repeat(500),
                    language = AssistantLanguage.RUSSIAN,
                    route = AssistantRoute.LOCAL_CONVERSATION,
                    memoryInsights = listOf(MemoryInsight("preference", "favorite_artist", "Artist $index"))
                )
            )
        }

        val snapshot = memory.snapshot()
        assertEquals("Artist 99", snapshot.facts.single().value)
        assertTrue(snapshot.recentMessages.size <= CompactAssistantMemory.MAX_RECENT_MESSAGES)
        assertTrue(persistence.value.length <= CompactAssistantMemory.MAX_PERSISTED_CHARS)
    }

    @Test
    fun `memory survives reload without full transcript`() = runBlocking {
        val persistence = InMemoryAssistantMemoryPersistence()
        val first = CompactAssistantMemory(persistence) { 5_000L }
        first.record(
            "Меня зовут Эмиль",
            AssistantReply(
                MusicIntent.Unknown,
                "Приятно познакомиться!",
                memoryInsights = listOf(MemoryInsight("identity", "name", "Эмиль"))
            )
        )

        val restored = CompactAssistantMemory(persistence).snapshot()

        assertEquals("Эмиль", restored.facts.single().value)
        assertEquals(2, restored.recentMessages.size)
    }
}

private class InMemoryAssistantMemoryPersistence : AssistantMemoryPersistence {
    var value = ""
    override suspend fun read(): String? = value.takeIf(String::isNotBlank)
    override suspend fun write(value: String) { this.value = value }
}
