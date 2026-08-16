package az.simplesoft.aura.assistant

import az.simplesoft.aura.assistant.gemini.GeminiToolRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiToolRegistryTest {
    @Test
    fun `declares the high value control commands`() {
        val names = buildSet {
            val declarations = GeminiToolRegistry.declarations()
            for (index in 0 until declarations.length()) {
                add(declarations.getJSONObject(index).getString("name"))
            }
        }

        assertTrue("open_radio" in names)
        assertTrue("clear_queue" in names)
        assertTrue("mute_music" in names)
        assertTrue("unmute_music" in names)
        assertTrue("set_car_mode" in names)
        assertTrue("disable_voice_mode" in names)
        assertTrue("save_queue_as_playlist" in names)
    }
}
