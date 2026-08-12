package az.simplesoft.aura.assistant

import az.simplesoft.aura.assistant.llm.LocalLlmDecision
import az.simplesoft.aura.assistant.llm.LocalLlmDecisionParser
import az.simplesoft.aura.assistant.llm.LocalLlmAction
import az.simplesoft.aura.assistant.llm.LocalLlmPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmDecisionParserTest {
    private val parser = LocalLlmDecisionParser()

    @Test
    fun promptRoutesNormalQuestionsToConversation() {
        val prompt = LocalLlmPromptBuilder().systemPrompt(AssistantLanguage.RUSSIAN)
        assertTrue(prompt.contains("Для любого обычного вопроса"))
        assertTrue(prompt.contains("Не используй unresolved"))
    }

    @Test
    fun promptDoesNotTeachTheBrainToRepeatFallbacks() {
        val prompt = LocalLlmPromptBuilder().userPrompt(
            AssistantRequest(
                originalText = "почему люди слушают музыку",
                normalizedText = "почему люди слушают музыку",
                language = AssistantLanguage.RUSSIAN
            ),
            AssistantContext(),
            listOf("AURA" to "Я пока не знаю ответа на это локально и не буду придумывать.")
        )
        assertTrue(!prompt.contains("Я пока не знаю ответа на это локально"))
        assertTrue(prompt.endsWith("/no_think"))
    }

    @Test
    fun parsesOnlySupportedStructuredActions() {
        val decision = parser.parse(
            """{"type":"action","action":"SEARCH_MUSIC","parameters":{"artist":"Linkin Park","query":"спокойное","mood":"CALM"},"reply":"Хорошо."}"""
        )
        assertTrue(decision is LocalLlmDecision.Action)
        assertTrue((decision as LocalLlmDecision.Action).action is LocalLlmAction.SearchMusic)
    }

    @Test
    fun stripsQwenThinkingWrapperBeforeParsing() {
        val decision = parser.parse("<think>brief internal trace</think>\n{\"type\":\"conversation\",\"reply\":\"Я рядом.\"}")
        assertTrue(decision is LocalLlmDecision.Conversation)
    }

    @Test
    fun rejectsUnknownToolAndMalformedOutput() {
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("{\"type\":\"action\",\"action\":\"CALL_PHONE\"}"))
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("{\"type\":\"action\",\"action\":\"PLAY\",\"parameters\":{\"tool\":\"phone\"}}"))
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("not json"))
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("{\"type\":\"action\",\"action\":\"PLAY\",\"evil\":true}"))
    }
}
