package az.simplesoft.aura.data.plugins.vol

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import az.simplesoft.aura.data.providers.AuraHttpClient

internal class VolSearchClient(
    private val httpClient: OkHttpClient = AuraHttpClient.create(),
    private val parser: VolSearchParser = VolSearchParser()
) {
    private val cache = VolMemoryCache<String, List<VolSearchItem>>(32, 5 * 60_000L)

    suspend fun search(query: String, limit: Int): List<VolSearchItem> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) throw VolPluginException(VolFailureReason.NOT_FOUND, "Empty Vol.az query")
        val cacheKey = "$normalizedQuery:$limit".lowercase()
        cache.get(cacheKey)?.let { return@withContext it }
        val request = Request.Builder()
            .url(VolSelectors.SEARCH_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "az-AZ,az;q=0.9,tr;q=0.8,ru;q=0.7,en;q=0.6")
            .post(FormBody.Builder().add("query", normalizedQuery).build())
            .build()
        val result = execute(request) { body -> parser.parseSearch(body, limit) }
        cache.put(cacheKey, result)
        result
    }

    suspend fun resolve(item: VolSearchItem): VolPlaybackData = withContext(Dispatchers.IO) {
        val detailRequest = Request.Builder()
            .url(item.detailUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        val page = execute(detailRequest) { body -> parser.parsePlayback(body, item.detailUrl) }
        val ajaxRequest = Request.Builder()
            .url(page.streamUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", item.detailUrl)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        val playback = execute(ajaxRequest) { body -> parser.parseAjaxPlayback(body) }
        playback.copy(
            artworkUrl = playback.artworkUrl ?: item.artworkUrl,
            durationMs = playback.durationMs ?: item.durationMs
        )
    }

    private fun <T> execute(request: Request, parse: (String) -> T): T {
        try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> throw VolPluginException(VolFailureReason.RATE_LIMITED, "Vol.az is rate limited")
                    response.code == 403 -> throw VolPluginException(VolFailureReason.ACCESS_RESTRICTED, "Vol.az access is restricted")
                    !response.isSuccessful -> throw VolPluginException(
                        if (response.code in 500..599) VolFailureReason.NETWORK else VolFailureReason.ACCESS_RESTRICTED,
                        "Vol.az returned HTTP ${response.code}"
                    )
                }
                return parse(response.body?.string().orEmpty())
            }
        } catch (error: VolPluginException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw VolPluginException(VolFailureReason.TIMEOUT, "Vol.az request timed out", error)
        } catch (error: IOException) {
            throw VolPluginException(VolFailureReason.NETWORK, "Vol.az network error", error)
        }
    }

    companion object {
        private const val USER_AGENT = "AuraMusic/0.6 (Android; Vol.az authorized catalog)"
    }
}

private class VolMemoryCache<K, V>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(maxEntries, .75f, true)

    @Synchronized fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= now()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized fun put(key: K, value: V) {
        entries[key] = Entry(value, now() + ttlMs)
        while (entries.size > maxEntries) entries.remove(entries.entries.first().key)
    }
}
