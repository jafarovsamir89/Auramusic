package az.simplesoft.aura.data.plugins.youtube

import android.net.Uri
import java.io.IOException
import java.security.SecureRandom
import java.util.LinkedHashMap
import kotlin.math.min
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class YouTubePlaybackStream(
    val videoId: String,
    val url: String,
    val mimeType: String,
    val itag: Int,
    val bitrate: Int,
    val userAgent: String,
    val expiresAtEpochMs: Long
)

internal data class YouTubeClientProfile(
    val name: String,
    val version: String,
    val userAgent: String,
    val context: Map<String, Any>,
    val requiresVisitorData: Boolean = false
) {
    fun clientJson(language: String, country: String, visitorData: String? = null): JSONObject = JSONObject()
        .put("clientName", name)
        .put("clientVersion", version)
        .put("hl", language)
        .put("gl", country)
        .put("utcOffsetMinutes", 0)
        .also { json -> if (!visitorData.isNullOrBlank()) json.put("visitorData", visitorData) }
        .also { json -> context.forEach(json::put) }
}

internal data class YouTubePlayerFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String
)

internal data class YouTubePlayerResult(
    val profile: YouTubeClientProfile,
    val expiresInSeconds: Long,
    val formats: List<YouTubePlayerFormat>
)

internal sealed class YouTubePlaybackException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {
    class ExpiredStream(videoId: String, cause: Throwable? = null) :
        YouTubePlaybackException("YouTube stream expired for $videoId", cause)

    class RateLimited(val retryAfterMs: Long? = null, cause: Throwable? = null) :
        YouTubePlaybackException("YouTube rate limit reached", cause)

    class ServerFailure(val statusCode: Int, cause: Throwable? = null) :
        YouTubePlaybackException("YouTube server returned HTTP $statusCode", cause)

    class LoginRequired(reason: String) :
        YouTubePlaybackException(reason.ifBlank { "YouTube login required" })

    class GeoBlocked(reason: String) :
        YouTubePlaybackException(reason.ifBlank { "YouTube content is unavailable in this region" })

    class AgeRestricted(reason: String) :
        YouTubePlaybackException(reason.ifBlank { "YouTube age verification required" })

    class UnsupportedCipher :
        YouTubePlaybackException("YouTube returned only unsupported ciphered audio formats")

    class Unavailable(val playabilityStatus: String, reason: String) :
        YouTubePlaybackException(reason.ifBlank { "YouTube content is not playable ($playabilityStatus)" })

    class InvalidResponse(reason: String, cause: Throwable? = null) :
        YouTubePlaybackException(reason, cause)

    class Transport(cause: IOException) :
        YouTubePlaybackException("YouTube network request failed", cause)
}

internal data class YouTubeRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 250L,
    val maxDelayMs: Long = 2_000L
) {
    init {
        require(maxAttempts >= 1)
        require(initialDelayMs >= 0)
        require(maxDelayMs >= initialDelayMs)
    }
}

internal class AuraYouTubePlayerClient(
    private val httpClient: OkHttpClient,
    private val endpoint: String = YouTubeSelectors.PLAYER_URL,
    private val visitorEndpoint: String = YouTubeSelectors.VISITOR_ID_URL,
    private val profiles: List<YouTubeClientProfile> = DEFAULT_PROFILES,
    private val language: String = "en",
    private val country: String = "US",
    private val retryPolicy: YouTubeRetryPolicy = YouTubeRetryPolicy(),
    private val sleeper: (Long) -> Unit = Thread::sleep,
    private val nonceFactory: () -> String = ::generateContentPlaybackNonce,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Volatile
    private var visitorSession: VisitorSession? = null

    @Throws(IOException::class)
    fun player(videoId: String): YouTubePlayerResult {
        val failures = mutableListOf<String>()
        var lastTypedFailure: YouTubePlaybackException? = null
        for (profile in profiles) {
            try {
                val result = requestWithFreshVisitor(videoId, profile)
                if (result.formats.isNotEmpty()) return result
                failures += "${profile.name}: no direct audio formats"
            } catch (error: IOException) {
                failures += "${profile.name}: ${error.message}"
                if (error is YouTubePlaybackException) lastTypedFailure = error
            }
        }
        throw lastTypedFailure ?: IOException("YouTube clients failed: ${failures.joinToString()}")
    }

    private fun requestWithFreshVisitor(
        videoId: String,
        profile: YouTubeClientProfile
    ): YouTubePlayerResult = try {
        request(videoId, profile)
    } catch (error: YouTubePlaybackException.LoginRequired) {
        if (!profile.requiresVisitorData) throw error
        visitorSession = null
        request(videoId, profile)
    }

    private fun request(videoId: String, profile: YouTubeClientProfile): YouTubePlayerResult {
        val cpn = nonceFactory()
        val visitorData = if (profile.requiresVisitorData) visitorData(profile) else null
        val payload = JSONObject()
            .put("context", requestContext(profile, visitorData))
            .put("videoId", videoId)
            .put("cpn", cpn)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
                )
            )
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", profile.userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val json = executeWithRetry(request)
        val status = json.optJSONObject("playabilityStatus")
        val statusCode = status?.optString("status").orEmpty()
        val reason = status?.optString("reason").orEmpty()
        if (statusCode != "OK") throw classifyPlayability(statusCode, reason)
        val returnedId = json.optJSONObject("videoDetails")?.optString("videoId")
        if (!returnedId.isNullOrBlank() && returnedId != videoId) {
            throw YouTubePlaybackException.InvalidResponse("YouTube video ID mismatch")
        }

        val streamingData = json.optJSONObject("streamingData") ?: return YouTubePlayerResult(profile, 0, emptyList())
        val formatsJson = streamingData.optJSONArray("adaptiveFormats")
        var hasCipheredAudio = false
        val formats = buildList {
            if (formatsJson != null) for (index in 0 until formatsJson.length()) {
                val item = formatsJson.optJSONObject(index) ?: continue
                val mimeType = item.optString("mimeType")
                val url = item.optString("url")
                if (!mimeType.startsWith("audio/")) continue
                if (url.isBlank()) {
                    hasCipheredAudio = hasCipheredAudio || item.has("signatureCipher") || item.has("cipher")
                    continue
                }
                val parsed = url.toHttpUrlOrNull() ?: continue
                if (!parsed.isHttps || !parsed.host.endsWith("googlevideo.com")) continue
                add(
                    YouTubePlayerFormat(
                        itag = item.optInt("itag", -1),
                        mimeType = mimeType,
                        bitrate = item.optInt("bitrate", 0),
                        url = parsed.newBuilder().addQueryParameter("cpn", cpn).build().toString()
                    )
                )
            }
        }
        if (formats.isEmpty() && hasCipheredAudio) throw YouTubePlaybackException.UnsupportedCipher()
        return YouTubePlayerResult(
            profile = profile,
            expiresInSeconds = streamingData.optLong("expiresInSeconds", 3600L),
            formats = formats
        )
    }

    private fun requestContext(profile: YouTubeClientProfile, visitorData: String?): JSONObject =
        JSONObject()
            .put("client", profile.clientJson(language, country, visitorData))
            .put(
                "request",
                JSONObject()
                    .put("internalExperimentFlags", JSONArray())
                    .put("useSsl", true)
            )
            .put("user", JSONObject().put("lockedSafetyMode", false))

    private fun visitorData(profile: YouTubeClientProfile): String {
        visitorSession?.takeIf { it.expiresAtEpochMs > now() }?.let { return it.value }
        synchronized(this) {
            visitorSession?.takeIf { it.expiresAtEpochMs > now() }?.let { return it.value }
            val request = Request.Builder()
                .url(visitorEndpoint)
                .header("User-Agent", profile.userAgent)
                .header("X-Goog-Api-Format-Version", "2")
                .header("Content-Type", "application/json")
                .post(
                    JSONObject()
                        .put("context", requestContext(profile, null))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build()
            val value = executeWithRetry(request)
                .optJSONObject("responseContext")
                ?.optString("visitorData")
                .orEmpty()
            if (value.isBlank()) {
                throw YouTubePlaybackException.InvalidResponse("YouTube returned no visitorData")
            }
            return value.also {
                visitorSession = VisitorSession(it, now() + VISITOR_SESSION_TTL_MS)
            }
        }
    }

    private fun executeWithRetry(request: Request): JSONObject {
        var attempt = 1
        var backoffMs = retryPolicy.initialDelayMs
        while (true) {
            val failure = try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        return try {
                            JSONObject(body)
                        } catch (error: RuntimeException) {
                            throw YouTubePlaybackException.InvalidResponse(
                                "Invalid YouTube player response",
                                error
                            )
                        }
                    }
                    when {
                        response.code == 429 -> YouTubePlaybackException.RateLimited(
                            retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1_000L)
                        )
                        response.code in 500..599 -> YouTubePlaybackException.ServerFailure(response.code)
                        else -> YouTubePlaybackException.Unavailable(
                            "HTTP_${response.code}",
                            response.message
                        )
                    }
                }
            } catch (error: YouTubePlaybackException) {
                error
            } catch (error: IOException) {
                throw YouTubePlaybackException.Transport(error)
            }

            val retryable = failure is YouTubePlaybackException.RateLimited ||
                failure is YouTubePlaybackException.ServerFailure
            if (!retryable || attempt >= retryPolicy.maxAttempts) throw failure

            val retryAfterMs = (failure as? YouTubePlaybackException.RateLimited)?.retryAfterMs
            sleepBeforeRetry((retryAfterMs ?: backoffMs).coerceAtMost(retryPolicy.maxDelayMs))
            backoffMs = min(backoffMs.coerceAtLeast(1L) * 2L, retryPolicy.maxDelayMs)
            attempt++
        }
    }

    private fun sleepBeforeRetry(delayMs: Long) {
        if (delayMs <= 0) return
        try {
            sleeper(delayMs)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw YouTubePlaybackException.Transport(IOException("YouTube retry interrupted", error))
        }
    }

    private fun classifyPlayability(status: String, reason: String): YouTubePlaybackException {
        val normalized = reason.lowercase()
        return when {
            "age" in normalized || "inappropriate" in normalized ->
                YouTubePlaybackException.AgeRestricted(reason)
            "country" in normalized || "region" in normalized || "not available in your" in normalized ->
                YouTubePlaybackException.GeoBlocked(reason)
            status == "LOGIN_REQUIRED" -> YouTubePlaybackException.LoginRequired(reason)
            else -> YouTubePlaybackException.Unavailable(status, reason)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val ANDROID_VR = YouTubeClientProfile(
            name = "ANDROID_VR",
            version = "1.65.10",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            context = mapOf(
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12L"
            )
        )
        val IOS = YouTubeClientProfile(
            name = "IOS",
            version = "21.03.2",
            userAgent = "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; en_US)",
            context = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.7.2"
            )
        )
        val ANDROID = YouTubeClientProfile(
            name = "ANDROID",
            version = "21.03.36",
            userAgent = "com.google.android.youtube/21.03.36 (Linux; U; Android 13) gzip",
            context = mapOf(
                "androidSdkVersion" to 33,
                "osName" to "Android",
                "osVersion" to "13"
            )
        )
        val VISIONOS = YouTubeClientProfile(
            name = "VISIONOS",
            version = "1.02",
            userAgent = "com.google.visionos.youtube/1.02" +
                "(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; US)",
            context = mapOf(
                "clientScreen" to "WATCH",
                "platform" to "MOBILE",
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice14,1",
                "osName" to "visionOS",
                "osVersion" to "25.6.0.23O471"
            ),
            requiresVisitorData = true
        )
        private val DEFAULT_PROFILES = listOf(VISIONOS, ANDROID_VR, ANDROID)
        private const val VISITOR_SESSION_TTL_MS = 6 * 60 * 60_000L

        private val NONCE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray()
        private val SECURE_RANDOM = SecureRandom()

        private fun generateContentPlaybackNonce(): String = CharArray(16) {
            NONCE_ALPHABET[SECURE_RANDOM.nextInt(NONCE_ALPHABET.size)]
        }.concatToString()
    }

    private data class VisitorSession(val value: String, val expiresAtEpochMs: Long)
}

internal class AuraYouTubePlaybackResolver(
    httpClient: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val playerClient: AuraYouTubePlayerClient = AuraYouTubePlayerClient(httpClient)
) {
    private val entries = object : LinkedHashMap<String, YouTubePlaybackStream>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTubePlaybackStream>?): Boolean =
            size > MAX_URL_CACHE_ENTRIES
    }

    @Synchronized
    @Throws(IOException::class)
    fun resolve(videoId: String, forceRefresh: Boolean = false): YouTubePlaybackStream {
        if (!forceRefresh) {
            entries[videoId]?.takeIf { it.expiresAtEpochMs > now() + EXPIRY_SAFETY_MS }?.let { return it }
        }
        val player = playerClient.player(videoId)
        val format = player.formats.maxByOrNull(::score)
            ?: throw IOException("YouTube returned no direct audio format")
        val expiry = Uri.parse(format.url).getQueryParameter("expire")
            ?.toLongOrNull()
            ?.times(1000L)
            ?: (now() + player.expiresInSeconds.coerceAtLeast(60L) * 1000L)
        return YouTubePlaybackStream(
            videoId = videoId,
            url = format.url,
            mimeType = format.mimeType.substringBefore(';'),
            itag = format.itag,
            bitrate = format.bitrate,
            userAgent = player.profile.userAgent,
            expiresAtEpochMs = expiry
        ).also { entries[videoId] = it }
    }

    @Synchronized
    fun invalidate(videoId: String) {
        entries.remove(videoId)
    }

    private fun score(format: YouTubePlayerFormat): Long {
        val codecScore = when {
            "opus" in format.mimeType.lowercase() -> 3_000_000L
            format.mimeType.startsWith("audio/mp4") -> 2_000_000L
            else -> 1_000_000L
        }
        return codecScore + format.bitrate.coerceAtMost(192_000)
    }

    companion object {
        private const val MAX_URL_CACHE_ENTRIES = 8
        private const val EXPIRY_SAFETY_MS = 5 * 60_000L
    }
}
