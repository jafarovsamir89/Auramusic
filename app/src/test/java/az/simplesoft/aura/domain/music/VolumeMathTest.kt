package az.simplesoft.aura.domain.music

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeMathTest {
    @Test
    fun `percentage maps to a real media level and clamps input`() {
        assertEquals(6, VolumeMath.levelForPercent(15, 40))
        assertEquals(0, VolumeMath.levelForPercent(15, -10))
        assertEquals(15, VolumeMath.levelForPercent(15, 140))
    }

    @Test
    fun `media level maps back to user percentage`() {
        assertEquals(40, VolumeMath.percentForLevel(15, 6))
    }
}
