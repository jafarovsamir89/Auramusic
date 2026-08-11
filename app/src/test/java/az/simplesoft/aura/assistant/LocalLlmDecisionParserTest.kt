package az.simplesoft.aura.assistant

import az.simplesoft.aura.assistant.llm.LocalLlmDecision
import az.simplesoft.aura.assistant.llm.LocalLlmDecisionParser
import az.simplesoft.aura.assistant.llm.LocalLlmAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmDecisionParserTest {
    private val parser = LocalLlmDecisionParser()

    @Test
    fun parsesOnlySupportedStructuredActions() {
        val decision = parser.parse(
            """{"type":"action","action":"SEARCH_MUSIC","parameters":{"artist":"Linkin Park","query":"спокойное","mood":"CALM"},"reply":"Хорошо."}"""
        )
        assertTrue(decision is LocalLlmDecision.Action)
        assertTrue((decision as LocalLlmDecision.Action).action is LocalLlmAction.SearchMusic)
    }

    @Test
    fun rejectsUnknownToolAndMalformedOutput() {
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("{\"type\":\"action\",\"action\":\"CALL_PHONE\"}"))
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("{\"type\":\"action\",\"action\":\"PLAY\",\"parameters\":{\"tool\":\"phone\"}}"))
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("not json"))
        assertEquals(LocalLlmDecision.Unresolved, parser.parse("{\"type\":\"action\",\"action\":\"PLAY\",\"evil\":true}"))
    }
}
