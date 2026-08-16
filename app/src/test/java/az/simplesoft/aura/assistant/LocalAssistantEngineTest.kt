package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAssistantEngineTest {
    private val engine = LocalAssistantEngine()

    @Test
    fun transliteratedAzerbaijaniCommandsStayLocal() {
        assertEquals(MusicIntent.Next, engine.decide("nobeti mahni").action)
        assertEquals(MusicIntent.Louder, engine.decide("sesi artir").action)
    }

    @Test
    fun artistTransliterationBecomesSearchWithoutChangingOriginalQuery() {
        val decision = engine.decide("Roya mahnisi qos")
        val search = decision.action as MusicIntent.Search

        assertEquals(AssistantLanguage.AZERBAIJANI, decision.language)
        assertTrue(search.query.contains("Roya", ignoreCase = true))
        assertEquals("Roya", search.artist)
    }

    @Test
    fun ordinalUsesPreviousSearchResults() {
        val decision = engine.decide(
            "Включи вторую",
            AssistantContext(lastSearchResults = listOf(
                AssistantTrackContext("1", "First", "Artist"),
                AssistantTrackContext("2", "Second", "Artist")
            ))
        )
        assertEquals(MusicIntent.Search("Second", "Artist"), decision.action)
        assertTrue(decision.diagnostics.contextReferences.contains("lastSearchResults[1]"))
    }

    @Test
    fun greetingsAndFactsDoNotBecomeMusicSearch() {
        assertEquals(MusicIntent.Unknown, engine.decide("Привет").action ?: MusicIntent.Unknown)
        assertEquals(MusicIntent.Unknown, engine.decide("Я люблю рок").action ?: MusicIntent.Unknown)
    }

    @Test
    fun localKnowledgeUsesPlayerContextAndDoesNotNeedRemoteReasoning() {
        val decision = engine.decide(
            "Сколько в очереди?",
            AssistantContext(queue = listOf(
                AssistantTrackContext("1", "One", "Artist"),
                AssistantTrackContext("2", "Two", "Artist")
            ))
        )

        assertTrue(decision.reply.contains("2"))
        assertTrue(!decision.isUnresolved)
        assertEquals("local-knowledge", decision.diagnostics.reason)
    }

    @Test
    fun preferenceAndNameBecomeCompactMemoryInsights() {
        val name = engine.decide("Меня зовут Самир")
        val preference = engine.decide("Я люблю рок")

        assertTrue(name.memoryInsights.any { it.category == "identity" && it.key == "name" && it.value == "Самир" })
        assertTrue(preference.memoryInsights.any { it.category == "preference" && it.key == "genre" && it.value == "рок" })
    }

    @Test
    fun naturalMixRequestStartsMusicMixInsteadOfConversation() {
        assertEquals(MusicIntent.MyMix, engine.decide("Поставь микс песен").action)
    }

    @Test
    fun naturalVolumePhrasesStayLocalActions() {
        assertEquals(MusicIntent.Louder, engine.decide("Сделай звук погромче").action)
        assertEquals(MusicIntent.Quieter, engine.decide("Сделай звук потише").action)
    }
}
