package az.simplesoft.aura.playback

import java.util.concurrent.ConcurrentHashMap

object PlaybackSourceRegistry {
    private val byUri = ConcurrentHashMap<String, Map<String, String>>()

    fun register(uri: String, headers: Map<String, String>) {
        byUri[uri] = headers.toMap()
    }

    fun headersFor(uri: String): Map<String, String> = byUri[uri].orEmpty()
}
