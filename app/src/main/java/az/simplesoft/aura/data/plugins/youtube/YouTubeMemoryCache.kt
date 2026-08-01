package az.simplesoft.aura.data.plugins.youtube

internal class YouTubeMemoryCache<K, V>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(val value: V, val storedAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(maxEntries, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (now() - entry.storedAt >= ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, now())
    }
}
