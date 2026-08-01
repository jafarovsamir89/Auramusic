package az.simplesoft.aura.data.plugins.youtube

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class YouTubePlayerClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val requestContext: YouTubeRequestContext = YouTubeRequestContext()
) {
    private val relatedCache = YouTubeMemoryCache<String, String>(maxEntries = 16, ttlMs = 2 * 60_000L)

    suspend fun related(videoId: String): String = relatedCache.get(videoId) ?: post(
        YouTubeSelectors.NEXT_URL,
        JSONObject()
            .put("context", clientContext())
            .put("videoId", videoId)
    ).also { relatedCache.put(videoId, it) }

    private suspend fun post(url: String, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { requestContext.playerHeaders().forEach(::header) }
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    throw YouTubePluginException(YouTubeFailureReason.RATE_LIMITED, "YouTube player is rate limited")
                }
                if (!response.isSuccessful) {
                    throw YouTubePluginException(
                        if (response.code in 500..599) YouTubeFailureReason.NETWORK else YouTubeFailureReason.ACCESS_RESTRICTED,
                        "YouTube player returned HTTP ${response.code}"
                    )
                }
                response.body?.string()?.takeIf(String::isNotBlank)
                    ?: throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Empty YouTube player response")
            }
        } catch (error: YouTubePluginException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw YouTubePluginException(YouTubeFailureReason.TIMEOUT, "YouTube player timed out", error)
        } catch (error: IOException) {
            throw YouTubePluginException(YouTubeFailureReason.NETWORK, "YouTube player network error", error)
        }
    }

    private fun clientContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", requestContext.clientName)
            .put("clientVersion", requestContext.clientVersion)
            .put("hl", requestContext.language)
            .put("gl", requestContext.countryCode)
            .put("androidSdkVersion", requestContext.androidSdkVersion)
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
