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

    @Test
    fun `sleep request excludes energetic mixes`() {
        val interpreted = brain.interpret(MusicSearchRequest(rawQuery = "музыка чтобы уснуть"))

        assertTrue("sleep" in interpreted.semanticTags)
        assertTrue("hardstyle" in interpreted.excludedTerms)
    }

    @Test
    fun `sad request prefers acoustic piano and rejects party`() {
        val interpreted = brain.interpret(MusicSearchRequest(rawQuery = "поставь грустную песню"))

        assertTrue("acoustic" in interpreted.query)
        assertTrue("piano" in interpreted.semanticTags)
        assertTrue("party" in interpreted.excludedTerms)
    }

    @Test
    fun `romantic request is not treated as a generic search`() {
        val interpreted = brain.interpret(MusicSearchRequest(rawQuery = "романтичные песни для двоих"))

        assertTrue("romantic" in interpreted.semanticTags)
        assertTrue(interpreted.query.contains("love songs"))
    }

    @Test
    fun `regional requests keep Azerbaijani and Turkish intent`() {
        val az = brain.interpret(MusicSearchRequest(rawQuery = "азербайджанская музыка"))
        val tr = brain.interpret(MusicSearchRequest(rawQuery = "турецкие хиты"))

        assertTrue("region-az" in az.semanticTags)
        assertTrue("region-tr" in tr.semanticTags)
    }
}
