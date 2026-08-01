package az.simplesoft.aura.playback

object PlaybackSourceRegistry {
    private const val MAX_ENTRIES = 64
    private val byUri = LinkedHashMap<String, Map<String, String>>(MAX_ENTRIES, .75f, true)

    @Synchronized
    fun register(uri: String, headers: Map<String, String>) {
        if (uri.isBlank()) return
        byUri[uri] = headers.toMap()
        while (byUri.size > MAX_ENTRIES) byUri.remove(byUri.entries.first().key)
    }

    @Synchronized
    fun headersFor(uri: String): Map<String, String> = byUri[uri].orEmpty()

    @Synchronized
    internal fun clear() = byUri.clear()
}
