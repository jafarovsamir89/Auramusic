package az.simplesoft.aura.data.plugins.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class YouTubePlaybackIdentityTest {
    @Test
    fun resolvedCacheKeyIncludesFormatItag() {
        val opus = YouTubePlaybackIdentity.cacheKey("kXYiU_JCYtU", 251)
        val aac = YouTubePlaybackIdentity.cacheKey("kXYiU_JCYtU", 140)

        assertEquals("youtube:kXYiU_JCYtU:251", opus)
        assertNotEquals(opus, aac)
    }
}
