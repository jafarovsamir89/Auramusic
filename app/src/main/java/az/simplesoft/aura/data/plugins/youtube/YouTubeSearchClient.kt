package az.simplesoft.aura.data.plugins.youtube

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import az.simplesoft.aura.data.providers.AuraHttpClient

class YouTubeSearchClient(
    private val httpClient: OkHttpClient = AuraHttpClient.create(),
    private val requestContext: YouTubeRequestContext = YouTubeRequestContext(),
    private val parser: YouTubeSearchParser = YouTubeSearchParser()
) {
    private val cache = YouTubeMemoryCache<String, List<YouTubeSearchItem>>(maxEntries = 32, ttlMs = 5 * 60_000L)

    suspend fun search(query: String, limit: Int): List<YouTubeSearchItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${requestContext.language}:${requestContext.countryCode}:${query.trim().lowercase()}:$limit"
        cache.get(cacheKey)?.let { return@withContext it }
        val url = YouTubeSelectors.SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("search_query", query.trim())
            .addQueryParameter("hl", requestContext.language)
            .addQueryParameter("gl", requestContext.countryCode)
            .build()
        val request = Request.Builder().url(url).apply {
            requestContext.browserHeaders().forEach(::header)
        }.build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 429 || response.request.url.encodedPath.startsWith("/sorry")) {
                    throw YouTubePluginException(YouTubeFailureReason.RATE_LIMITED, "YouTube search is rate limited")
                }
                if (response.request.url.host in YouTubeSelectors.RESTRICTED_HOSTS &&
                    response.request.url.host != "www.youtube.com"
                ) {
                    throw YouTubePluginException(YouTubeFailureReason.ACCESS_RESTRICTED, "YouTube consent is required")
                }
                if (!response.isSuccessful) {
                    throw YouTubePluginException(
                        if (response.code in 500..599) YouTubeFailureReason.NETWORK else YouTubeFailureReason.ACCESS_RESTRICTED,
                        "YouTube search returned HTTP ${response.code}"
                    )
                }
                val body = response.body?.string().orEmpty()
                val result = parser.parseHtml(body, limit)
                if (result.isEmpty()) {
                    throw YouTubePluginException(YouTubeFailureReason.NOT_FOUND, "No playable YouTube results")
                }
                cache.put(cacheKey, result)
                result
            }
        } catch (error: YouTubePluginException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw YouTubePluginException(YouTubeFailureReason.TIMEOUT, "YouTube search timed out", error)
        } catch (error: IOException) {
            throw YouTubePluginException(YouTubeFailureReason.NETWORK, "YouTube search network error", error)
        }
    }
}
