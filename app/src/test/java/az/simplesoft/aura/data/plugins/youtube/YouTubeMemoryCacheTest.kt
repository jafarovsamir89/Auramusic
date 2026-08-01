package az.simplesoft.aura.data.plugins.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeMemoryCacheTest {
    @Test
    fun expiresEntriesAtTtl() {
        var now = 1_000L
        val cache = YouTubeMemoryCache<String, String>(maxEntries = 2, ttlMs = 100L) { now }
        cache.put("track", "value")

        assertEquals("value", cache.get("track"))
        now += 100L
        assertNull(cache.get("track"))
    }

    @Test
    fun evictsLeastRecentlyUsedEntry() {
        val cache = YouTubeMemoryCache<String, String>(maxEntries = 2, ttlMs = 1_000L) { 0L }
        cache.put("one", "1")
        cache.put("two", "2")
        cache.get("one")
        cache.put("three", "3")

        assertEquals("1", cache.get("one"))
        assertNull(cache.get("two"))
        assertEquals("3", cache.get("three"))
    }
}
