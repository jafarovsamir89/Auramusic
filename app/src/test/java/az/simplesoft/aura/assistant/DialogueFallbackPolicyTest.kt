package az.simplesoft.aura.assistant

import az.simplesoft.aura.data.database.AssistantDialogueNodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogueFallbackPolicyTest {
    private val nodes = listOf(
        AssistantDialogueNodeEntity("ru_greeting", "greeting", "ru", null, 100),
        AssistantDialogueNodeEntity("ru_unknown", "fallback", "ru", null, -100)
    )

    @Test
    fun unknownUsesExplicitFallbackInsteadOfGreeting() {
        assertEquals("ru_unknown", DialogueFallbackPolicy.select(nodes, AssistantLanguage.RUSSIAN, AssistantIntent.UNKNOWN)?.id)
    }

    @Test
    fun knownIntentDoesNotUseFallbackPolicy() {
        assertNull(DialogueFallbackPolicy.select(nodes, AssistantLanguage.RUSSIAN, AssistantIntent.GREETING))
    }
}
