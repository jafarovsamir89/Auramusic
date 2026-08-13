package az.simplesoft.aura.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModeCommandTest {
    @Test
    fun `recognizes explicit voice mode shutdown phrases`() {
        assertTrue(VoiceModeCommand.isDisable("Аура, отключись"))
        assertTrue(VoiceModeCommand.isDisable("отключись"))
        assertTrue(VoiceModeCommand.isDisable("выключи голосовой режим"))
        assertTrue(VoiceModeCommand.isDisable("Аура замолчи"))
        assertTrue(VoiceModeCommand.isDisable("Аура, останови голос"))
        assertTrue(VoiceModeCommand.isDisable("выйди из голосового режима"))
    }

    @Test
    fun `does not confuse music stop with voice shutdown`() {
        assertFalse(VoiceModeCommand.isDisable("останови музыку"))
        assertFalse(VoiceModeCommand.isDisable("поставь трек на паузу"))
    }
}
