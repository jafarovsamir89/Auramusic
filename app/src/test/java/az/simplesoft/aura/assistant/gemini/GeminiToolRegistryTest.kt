package az.simplesoft.aura.assistant.gemini

import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiToolRegistryTest {
    @Test
    fun `registry exposes narrow music actions`() {
        val names = (0 until GeminiToolRegistry.declarations().length())
            .map { GeminiToolRegistry.declarations().getJSONObject(it).getString("name") }
        assertTrue("search_music" in names)
        assertTrue("next_track" in names)
        assertTrue("set_volume" in names)
        assertTrue("execute_anything" !in names)
    }

    @Test
    fun `volume schema requires bounded percent`() {
        val declaration = (0 until GeminiToolRegistry.declarations().length())
            .map { GeminiToolRegistry.declarations().getJSONObject(it) }
            .first { it.getString("name") == "set_volume" }
        assertTrue(declaration.getJSONObject("parameters").getJSONArray("required").toString().contains("percent"))
        assertTrue(declaration.getJSONObject("parameters").getJSONObject("properties").getJSONObject("percent").getString("type") == "INTEGER")
    }
}
