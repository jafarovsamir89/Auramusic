package az.simplesoft.aura.data.plugins.youtube

import androidx.core.net.toUri
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.TrackCandidate
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface YouTubeStreamValidator {
    suspend fun validate(url: String, headers: Map<String, String>): Boolean
}

class YouTubeHttpStreamValidator(
    private val httpClient: OkHttpClient = OkHttpClient()
) : YouTubeStreamValidator {
    override suspend fun validate(url: String, headers: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Range", "bytes=0-1023").apply {
            headers.forEach(::header)
        }.build()
        try {
            httpClient.newCall(request).execute().use { response ->
                response.code == 200 || response.code == 206
            }
        } catch (_: IOException) {
            false
        }
    }
}

class YouTubeStreamResolver(
    private val signatureResolver: YouTubeSignatureResolver = YouTubeSignatureResolver(),
    private val validator: YouTubeStreamValidator = YouTubeHttpStreamValidator(),
    private val requestContext: YouTubeRequestContext = YouTubeRequestContext(),
    private val maxBitrate: Int = 192_000,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun resolve(candidate: TrackCandidate, player: YouTubePlayerData): PlayableSource {
        val headers = mapOf(
            "User-Agent" to requestContext.userAgent,
            "Referer" to "${YouTubeSelectors.WATCH_URL}?v=${player.videoId}"
        )
        val selected = selectValidatedFormat(player.formats, player.expiresInSeconds, headers)
        val track = Track(
            id = "$ID:${player.videoId}",
            title = candidate.title.ifBlank { player.title },
            artist = candidate.artist.ifBlank { player.artist },
            artworkUrl = candidate.artworkUrl ?: player.artworkUrl,
            durationMs = candidate.durationMs ?: player.durationMs,
            sourceId = ID,
            sourcePageUrl = "${YouTubeSelectors.WATCH_URL}?v=${player.videoId}",
            playbackType = PlaybackType.DIRECT_STREAM,
            streamUrl = selected.url,
            requestHeaders = headers,
            isPlayable = true,
            popularity = player.popularity?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            year = candidate.year ?: player.year
        )
        return PlayableSource(
            providerId = ID,
            track = track,
            playbackUri = selected.url.toUri(),
            requestHeaders = headers,
            mimeType = selected.format.containerMimeType,
            expiresAt = selected.expiresAtEpochMs,
            sourcePageUrl = track.sourcePageUrl
        )
    }

    fun selectFormat(formats: List<YouTubeAudioFormat>, expiresInSeconds: Long?): YouTubeResolvedFormat {
        return rankedFormats(formats, expiresInSeconds).first()
    }

    suspend fun selectValidatedFormat(
        formats: List<YouTubeAudioFormat>,
        expiresInSeconds: Long?,
        headers: Map<String, String>
    ): YouTubeResolvedFormat {
        for (resolved in rankedFormats(formats, expiresInSeconds)) {
            if (validator.validate(resolved.url, headers)) return resolved
        }
        throw YouTubePluginException(YouTubeFailureReason.NO_AUDIO_FORMAT, "YouTube audio validation failed")
    }

    private fun rankedFormats(
        formats: List<YouTubeAudioFormat>,
        expiresInSeconds: Long?
    ): List<YouTubeResolvedFormat> {
        var lastSignatureError: YouTubePluginException? = null
        val resolved = formats.asSequence()
            .filter { it.mimeType.startsWith("audio/") }
            .mapNotNull { format ->
                try {
                    format to signatureResolver.resolve(format)
                } catch (error: YouTubePluginException) {
                    lastSignatureError = error
                    null
                }
            }
            .sortedByDescending { (format, _) -> formatScore(format) }
            .toList()
        if (resolved.isEmpty()) {
            throw lastSignatureError
                ?: YouTubePluginException(YouTubeFailureReason.NO_AUDIO_FORMAT, "No compatible YouTube audio format")
        }
        return resolved.map { (format, url) ->
            val expiryFromUrl = url.toHttpUrlOrNull()?.queryParameter("expire")
                ?.toLongOrNull()?.times(1000L)
            val expiry = expiryFromUrl ?: expiresInSeconds?.let { now() + it * 1000L }
            YouTubeResolvedFormat(
                format = format,
                url = url,
                expiresAtEpochMs = expiry?.minus(30_000L)?.coerceAtLeast(now())
            )
        }
    }

    private fun formatScore(format: YouTubeAudioFormat): Long {
        val codec = format.codec.orEmpty().lowercase()
        val family = when {
            format.containerMimeType == "audio/webm" && "opus" in codec -> 3_000_000L
            format.containerMimeType == "audio/mp4" -> 2_000_000L
            else -> 1_000_000L
        }
        val bitrate = format.bitrate.coerceAtLeast(0)
        val quality = if (bitrate <= maxBitrate) bitrate.toLong() else maxBitrate - (bitrate - maxBitrate).toLong()
        return family + quality.coerceAtLeast(0L) + if (format.contentLength != null) 1_000L else 0L
    }

    companion object {
        const val ID = "youtube"
    }
}
