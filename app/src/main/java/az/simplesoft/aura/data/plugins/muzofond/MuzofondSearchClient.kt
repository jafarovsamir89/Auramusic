package az.simplesoft.aura.data.plugins.muzofond

import android.net.Uri
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import az.simplesoft.aura.data.providers.AuraHttpClient

internal class MuzofondSearchClient(
    private val httpClient: OkHttpClient = AuraHttpClient.create(),
    private val parser: MuzofondSearchParser = MuzofondSearchParser()
) {
    private val cache = MuzofondMemoryCache<String, List<MuzofondSearchItem>>(32, 5 * 60_000L)

    suspend fun search(query: String, limit: Int): List<MuzofondSearchItem> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            throw MuzofondPluginException(MuzofondFailureReason.NOT_FOUND, "Empty Muzofond query")
        }
        val cacheKey = "$normalizedQuery:$limit".lowercase()
        cache.get(cacheKey)?.let { return@withContext it }
        val url = MuzofondSelectors.SEARCH_PREFIX + Uri.encode(normalizedQuery)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> throw MuzofondPluginException(
                        MuzofondFailureReason.RATE_LIMITED,
                        "Muzofond search is rate limited"
                    )
                    response.code == 403 -> throw MuzofondPluginException(
                        MuzofondFailureReason.ACCESS_RESTRICTED,
                        "Muzofond search is access restricted"
                    )
                    !response.isSuccessful -> throw MuzofondPluginException(
                        if (response.code in 500..599) MuzofondFailureReason.NETWORK
                        else MuzofondFailureReason.ACCESS_RESTRICTED,
                        "Muzofond search returned HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                val result = parser.parseSearch(body, limit)
                cache.put(cacheKey, result)
                result
            }
        } catch (error: MuzofondPluginException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw MuzofondPluginException(MuzofondFailureReason.TIMEOUT, "Muzofond search timed out", error)
        } catch (error: IOException) {
            throw MuzofondPluginException(MuzofondFailureReason.NETWORK, "Muzofond search network error", error)
        }
    }

    companion object {
        private const val USER_AGENT = "AuraMusic/0.2 (Android)"
    }
}

private class MuzofondMemoryCache<K, V>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = LinkedHashMap<K, Entry<V>>(maxEntries, .75f, true)

    @Synchronized
    fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= now()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = Entry(value, now() + ttlMs)
        while (entries.size > maxEntries) entries.remove(entries.entries.first().key)
    }
}
