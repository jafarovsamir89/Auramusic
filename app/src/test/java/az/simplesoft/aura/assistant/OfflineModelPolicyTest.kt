package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineModelPolicyTest {
    @Test
    fun weakDevicesPreferLightAndPowerfulDevicesPreferQuality() {
        assertEquals(WhisperModelProfile.LIGHT, WhisperModelPolicy.recommend(2048, 4))
        assertEquals(WhisperModelProfile.BALANCED, WhisperModelPolicy.recommend(4096, 6))
        assertEquals(WhisperModelProfile.QUALITY, WhisperModelPolicy.recommend(8192, 8))
    }
}
