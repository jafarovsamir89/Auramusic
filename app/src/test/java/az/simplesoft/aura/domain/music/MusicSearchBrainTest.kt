package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.providers.MusicSearchRequest
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSearchBrainTest {
    private val brain = MusicSearchBrain()

    @Test
    fun `lullaby becomes bedtime query and excludes generic chill`() {
        val interpreted = brain.interpret(MusicSearchRequest(rawQuery = "поставь колыбельную для ребёнка"))

        assertTrue(interpreted.query.contains("lullaby"))
        assertTrue("lullaby" in interpreted.semanticTags)
        assertTrue("chill" in interpreted.excludedTerms)
    }

    @Test
    fun `azerbaijani lullaby is recognized`() {
        val interpreted = brain.interpret(MusicSearchRequest(rawQuery = "uşaq yuxu mahnısı"))

        assertTrue("nursery" in interpreted.query)
        assertTrue("bedtime" in interpreted.semanticTags)
    }
}
