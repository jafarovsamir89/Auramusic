package az.simplesoft.aura.data.providers.zaycev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ZaycevSearchClient(
    private val baseUrl: String = ZaycevSelectors.BASE_URL,
    val cookieStore: ZaycevCookieStore = ZaycevCookieStore(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    data class HttpPayload(val body: String, val code: Int, val finalUrl: String)
    @Volatile var lastValidationStatus: Int? = null
        private set

    suspend fun search(query: String, limit: Int): HttpPayload {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(ZaycevSelectors.SEARCH_PATH)
            .addQueryParameter("q", query)
            .addQueryParameter("limitTrack", limit.coerceIn(1, 30).toString())
            .addQueryParameter("limitMusicset", "0")
            .addQueryParameter("limitArtist", "3")
            .build()
        return execute(Request.Builder().url(url).get().publicHeaders().build())
    }

    suspend fun searchHtml(query: String): HttpPayload {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("query_search", query)
            .addQueryParameter("type", "all")
            .build()
        return execute(Request.Builder().url(url).get().publicHeaders().build())
    }

    suspend fun trackPage(url: String): HttpPayload =
        execute(Request.Builder().url(url).get().publicHeaders().build())

    suspend fun fileMeta(trackId: String): ZaycevFileMeta {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(ZaycevSelectors.FILE_META_PATH).build()
        val body = JSONObject()
            .put("trackIds", JSONArray().put(trackId.toLongOrNull() ?: trackId))
            .put("subscription", false)
            .toString()
            .toRequestBody(JSON)
        val payload = execute(Request.Builder().url(url).post(body).publicHeaders().build())
        val item = JSONObject(payload.body).getJSONArray("tracks").getJSONObject(0)
        val token = item.optString("streaming")
        if (token.isBlank()) throw IOException("Zaycev did not return a public streaming token")
        return ZaycevFileMeta(item.get("id").toString(), token)
    }

    suspend fun playback(token: String, referer: String): ZaycevPlaybackInfo {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(ZaycevSelectors.PLAY_PATH)
            .addPathSegment(token)
            .build()
        val payload = execute(
            Request.Builder().url(url).get().publicHeaders().header("Referer", referer).build()
        )
        val json = JSONObject(payload.body)
        val playableUrl = json.optString("url")
        if (!playableUrl.startsWith("https://")) throw IOException("Public playback URL is missing")
        return ZaycevPlaybackInfo(playableUrl, json.optLong("duration").takeIf { it > 0L })
    }

    suspend fun validatePlayable(url: String, headers: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().header("Range", "bytes=0-0").apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            lastValidationStatus = response.code
            response.isSuccessful || response.code == 206
        }
    }

    fun playbackHeaders(referer: String, playableUrl: String): Map<String, String> = buildMap {
        put("User-Agent", ZaycevSelectors.USER_AGENT)
        put("Referer", referer)
        runCatching { playableUrl.toHttpUrl() }.getOrNull()?.let { url ->
            cookieStore.headerFor(url).takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
    }

    private suspend fun execute(request: Request): HttpPayload = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code, body.take(240))
            HttpPayload(body, response.code, response.request.url.toString())
        }
    }

    private fun Request.Builder.publicHeaders() =
        header("User-Agent", ZaycevSelectors.USER_AGENT)
            .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")

    class HttpStatusException(val statusCode: Int, bodyPreview: String) :
        IOException("HTTP $statusCode: $bodyPreview")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
