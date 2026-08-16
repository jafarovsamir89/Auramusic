package az.simplesoft.aura.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Small metadata-only artwork lookup used when a catalog has no usable cover.
 * It never downloads audio and keeps results in memory for the current session.
 */
class ArtistArtworkLookup(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = "https://itunes.apple.com/search"
) {
    private val cache = linkedMapOf<String, String?>()

    suspend fun lookup(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return@withContext null
        val key = "$cleanArtist\u0000$cleanTitle".lowercase()
        synchronized(cache) {
            if (cache.containsKey(key)) return@withContext cache[key]
        }
        val result = runCatching {
            val normalizedArtist = cleanArtist.replace(Regex("[^\\p{L}\\p{N} ]"), " ").collapseWhitespace()
            val normalizedTitle = cleanTitle.replace(Regex("[^\\p{L}\\p{N} ]"), " ").collapseWhitespace()
            val shortTitle = normalizedTitle.split(' ').filter { it.length >= 2 }.take(4).joinToString(" ")
            val artistIsGeneric = normalizedArtist.equals("vol az", ignoreCase = true) ||
                normalizedArtist.equals("vol", ignoreCase = true)
            val queries = buildList {
                add("$cleanArtist $cleanTitle" to "songTerm")
                if (normalizedArtist.isNotBlank() && normalizedTitle.isNotBlank()) {
                    add("$normalizedArtist $shortTitle" to "songTerm")
                }
                if (!artistIsGeneric && normalizedArtist.isNotBlank()) add(normalizedArtist to "artistTerm")
                if (normalizedTitle.isNotBlank()) add(normalizedTitle to "songTerm")
            }.distinctBy { it.first.lowercase() to it.second }
            queries.firstNotNullOfOrNull { (query, attribute) -> fetch(query, attribute) }
        }.getOrNull()
        synchronized(cache) {
            if (cache.size >= MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
            cache[key] = result
        }
        result
    }

    private fun fetch(rawQuery: String, attribute: String): String? {
        val query = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8.name())
        val request = Request.Builder()
            .url("$endpoint?term=$query&entity=song&attribute=$attribute&limit=5")
            .header("Accept", "application/json")
            .header("User-Agent", "AuraMusic/0.8 (artwork metadata)")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val results = JSONObject(response.body?.string().orEmpty()).optJSONArray("results")
                ?: return@use null
            val candidates = (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                val image = item.optString("artworkUrl100")
                    .replace(Regex("/\\d+x\\d+bb"), "/600x600bb")
                    .takeIf { it.startsWith("https://") }
                    ?: return@mapNotNull null
                val candidateArtist = item.optString("artistName")
                val candidateTitle = item.optString("trackName")
                val artistOverlap = tokenOverlap(cleanTokens(rawQuery), cleanTokens(candidateArtist))
                val titleOverlap = tokenOverlap(cleanTokens(rawQuery), cleanTokens(candidateTitle))
                val score = if (candidateArtist.isBlank() && candidateTitle.isBlank()) {
                    // Some catalog responses omit metadata but still provide a
                    // valid artwork URL; preserve the explicit fallback query.
                    1
                } else if (attribute == "artistTerm") {
                    artistOverlap * 3
                } else {
                    titleOverlap * 2 + artistOverlap
                }
                image to score
            }
            candidates.maxByOrNull { it.second }
                ?.takeIf { it.second > 0 }
                ?.first
        }
    }

    private fun cleanTokens(value: String): Set<String> = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .filter { it.length >= 2 }
        .toSet()

    private fun tokenOverlap(left: Set<String>, right: Set<String>): Int = left.count { it in right }

    companion object {
        private const val MAX_CACHE_ENTRIES = 120

        fun isUsable(url: String?): Boolean {
            val value = url?.trim().orEmpty()
            if (!value.startsWith("https://")) return false
            return !value.contains("fblogo", ignoreCase = true) &&
                !value.contains("placeholder", ignoreCase = true) &&
                !value.contains("noimage", ignoreCase = true)
        }
    }
}

private fun String.collapseWhitespace(): String = replace(Regex("\\s+"), " ").trim()
