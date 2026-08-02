package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterAssistantAdapterTest {
    private val adapter = OpenRouterAssistantAdapter("test-key", "deepseek/test")

    @Test
    fun `chat plan never becomes search`() {
        val result = adapter.parsePlan(
            """{"reply":"Привет! Как настроение?","language":"ru","action":{"type":"chat"},"memory":[]}""",
            AssistantLanguage.RUSSIAN
        )

        assertEquals(MusicIntent.Unknown, result.intent)
        assertEquals(AssistantRoute.LOCAL_CONVERSATION, result.route)
        assertEquals(AssistantSource.DEEPSEEK, result.source)
    }

    @Test
    fun `music plan preserves artist mood and memory`() {
        val result = adapter.parsePlan(
            """{"reply":"Хорошо, подберу грустную песню МакSим.","language":"ru","action":{"type":"play_music","query":"МакSим грустная песня","artist":"МакSим","mood":"sad"},"memory":[{"category":"preference","key":"mood","value":"sad"}]}""",
            AssistantLanguage.RUSSIAN
        )
        val search = result.intent as MusicIntent.Search

        assertEquals("МакSим", search.artist)
        assertEquals(Mood.SAD, search.mood)
        assertEquals(1, result.memoryInsights.size)
    }

    @Test
    fun `request sends compact memory and requires json`() {
        val request = adapter.requestJson(
            input = "hello",
            context = AuraAiContext(hourOfDay = 10),
            memory = AssistantMemorySnapshot(
                facts = listOf(AssistantMemoryFact("identity", "name", "Emil", 1L))
            ),
            language = AssistantLanguage.ENGLISH
        )

        assertEquals("json_object", request.getJSONObject("response_format").getString("type"))
        assertTrue(request.getJSONArray("messages").toString().contains("identity.name"))
    }
}
