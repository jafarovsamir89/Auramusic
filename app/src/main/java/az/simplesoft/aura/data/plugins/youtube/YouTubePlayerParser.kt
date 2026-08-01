package az.simplesoft.aura.data.plugins.youtube

import org.json.JSONArray
import org.json.JSONObject

class YouTubePlayerParser {
    fun parse(json: String): YouTubePlayerData {
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Invalid YouTube player JSON", it)
        }
        ensurePlayable(root)
        if (containsKey(root, "drmFamilies")) {
            throw YouTubePluginException(YouTubeFailureReason.DRM, "DRM-protected YouTube content is unsupported")
        }
        val details = root.optJSONObject("videoDetails")
            ?: throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "YouTube video details missing")
        val streaming = root.optJSONObject("streamingData")
            ?: throw YouTubePluginException(YouTubeFailureReason.NO_AUDIO_FORMAT, "YouTube streaming data missing")
        val adaptive = streaming.optJSONArray("adaptiveFormats") ?: JSONArray()
        val formats = buildList {
            for (index in 0 until adaptive.length()) {
                val item = adaptive.optJSONObject(index) ?: continue
                val mime = item.optString("mimeType")
                if (!mime.startsWith("audio/")) continue
                add(
                    YouTubeAudioFormat(
                        itag = item.optInt("itag", -1),
                        mimeType = mime,
                        bitrate = item.optInt("bitrate", 0),
                        contentLength = item.optString("contentLength").toLongOrNull(),
                        approximateDurationMs = item.optString("approxDurationMs").toLongOrNull(),
                        audioQuality = item.optString("audioQuality").takeIf(String::isNotBlank),
                        audioSampleRate = item.optString("audioSampleRate").toIntOrNull(),
                        audioChannels = item.optInt("audioChannels", 0).takeIf { it > 0 },
                        directUrl = item.optString("url").takeIf(String::isNotBlank),
                        signatureCipher = sequenceOf("signatureCipher", "cipher")
                            .map(item::optString).firstOrNull(String::isNotBlank)
                    )
                )
            }
        }
        if (formats.isEmpty()) {
            throw YouTubePluginException(YouTubeFailureReason.NO_AUDIO_FORMAT, "No audio-only YouTube formats")
        }
        val microformat = root.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
        val publishDate = microformat?.optString("publishDate").orEmpty()
        return YouTubePlayerData(
            videoId = details.optString("videoId").takeIf(String::isNotBlank)
                ?: throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "YouTube video ID missing"),
            title = details.optString("title").ifBlank { "YouTube" },
            artist = details.optString("author").ifBlank { "YouTube" },
            artworkUrl = details.optJSONObject("thumbnail")?.largestThumbnail(),
            durationMs = details.optString("lengthSeconds").toLongOrNull()?.times(1000L),
            popularity = details.optString("viewCount").toLongOrNull(),
            year = publishDate.take(4).toIntOrNull(),
            expiresInSeconds = streaming.optString("expiresInSeconds").toLongOrNull(),
            formats = formats
        )
    }

    private fun ensurePlayable(root: JSONObject) {
        val statusObject = root.optJSONObject("playabilityStatus")
        val status = statusObject?.optString("status").orEmpty()
        if (status == "OK") return
        val message = buildString {
            append(statusObject?.optString("reason").orEmpty())
            statusObject?.deepTexts()?.forEach { append(' ').append(it) }
        }.trim().ifBlank { "YouTube content is not playable" }
        val lower = message.lowercase()
        val reason = when {
            status == "LOGIN_REQUIRED" || "sign in" in lower || "login" in lower -> YouTubeFailureReason.LOGIN_REQUIRED
            "age" in lower || "confirm your age" in lower -> YouTubeFailureReason.AGE_RESTRICTED
            "purchase" in lower || "paid" in lower || "members-only" in lower -> YouTubeFailureReason.PAID_CONTENT
            "country" in lower || "region" in lower -> YouTubeFailureReason.REGION_BLOCKED
            "bot" in lower || "captcha" in lower || "traffic" in lower -> YouTubeFailureReason.RATE_LIMITED
            status == "UNPLAYABLE" -> YouTubeFailureReason.ACCESS_RESTRICTED
            else -> YouTubeFailureReason.ACCESS_RESTRICTED
        }
        throw YouTubePluginException(reason, message.take(240))
    }

    private fun containsKey(node: Any?, target: String): Boolean = when (node) {
        is JSONObject -> {
            if (node.has(target)) true else {
                val keys = node.keys()
                var found = false
                while (keys.hasNext() && !found) found = containsKey(node.opt(keys.next()), target)
                found
            }
        }
        is JSONArray -> (0 until node.length()).any { containsKey(node.opt(it), target) }
        else -> false
    }

    private fun JSONObject.deepTexts(): List<String> {
        val result = mutableListOf<String>()
        fun collect(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optString("text").takeIf(String::isNotBlank)?.let(result::add)
                    val keys = node.keys()
                    while (keys.hasNext()) collect(node.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until node.length()) collect(node.opt(index))
            }
        }
        collect(this)
        return result
    }

    private fun JSONObject.largestThumbnail(): String? {
        val thumbnails = optJSONArray("thumbnails") ?: return null
        return (0 until thumbnails.length()).mapNotNull(thumbnails::optJSONObject)
            .maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }
            ?.optString("url")?.takeIf { it.startsWith("https://") }
    }
}
