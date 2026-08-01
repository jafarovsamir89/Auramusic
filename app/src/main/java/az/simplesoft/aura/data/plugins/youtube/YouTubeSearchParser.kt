package az.simplesoft.aura.data.plugins.youtube

import org.json.JSONArray
import org.json.JSONObject

class YouTubeSearchParser {
    fun parseHtml(html: String, limit: Int): List<YouTubeSearchItem> {
        val initialData = extractAssignedJson(html, YouTubeSelectors.INITIAL_DATA_MARKER)
        return parseJson(initialData, limit)
    }

    fun parseJson(json: String, limit: Int): List<YouTubeSearchItem> {
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Invalid YouTube search JSON", it)
        }
        val items = mutableListOf<YouTubeSearchItem>()
        collect(root, items)
        return items.distinctBy(YouTubeSearchItem::videoId).take(limit.coerceIn(1, 50))
    }

    internal fun extractAssignedJson(html: String, marker: String): String {
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) {
            val reason = when {
                html.contains("consent.youtube.com", ignoreCase = true) -> YouTubeFailureReason.ACCESS_RESTRICTED
                html.contains("unusual traffic", ignoreCase = true) -> YouTubeFailureReason.RATE_LIMITED
                else -> YouTubeFailureReason.PARSE_CHANGED
            }
            throw YouTubePluginException(reason, "YouTube initial data is unavailable")
        }
        var start = markerIndex + marker.length
        while (start < html.length && html[start].isWhitespace()) start += 1
        if (start >= html.length) {
            throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Search JSON start not found")
        }
        if (html[start] == '\'' || html[start] == '"') {
            return decodeJavaScriptString(html, start)
        }
        if (html[start] != '{') {
            throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Unexpected YouTube search data encoding")
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return html.substring(start, index + 1)
                }
            }
        }
        throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Search JSON is incomplete")
    }

    private fun decodeJavaScriptString(source: String, start: Int): String {
        val quote = source[start]
        val output = StringBuilder()
        var index = start + 1
        while (index < source.length) {
            val char = source[index++]
            if (char == quote) return output.toString()
            if (char != '\\') {
                output.append(char)
                continue
            }
            if (index >= source.length) break
            when (val escaped = source[index++]) {
                'x' -> output.append(readHex(source, index, 2).also { index += 2 })
                'u' -> output.append(readHex(source, index, 4).also { index += 4 })
                'n' -> output.append('\n')
                'r' -> output.append('\r')
                't' -> output.append('\t')
                'b' -> output.append('\b')
                'f' -> output.append('\u000C')
                '\n' -> Unit
                '\r' -> if (index < source.length && source[index] == '\n') index += 1
                else -> output.append(escaped)
            }
        }
        throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Encoded YouTube search JSON is incomplete")
    }

    private fun readHex(source: String, start: Int, length: Int): Char {
        if (start + length > source.length) {
            throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Invalid YouTube search escape")
        }
        return source.substring(start, start + length).toIntOrNull(16)?.toChar()
            ?: throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Invalid YouTube search escape")
    }

    private fun collect(node: Any?, output: MutableList<YouTubeSearchItem>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("videoRenderer")?.let { parseVideoRenderer(it)?.let(output::add) }
                node.optJSONObject("compactVideoRenderer")?.let { parseVideoRenderer(it)?.let(output::add) }
                node.optJSONObject("playlistPanelVideoRenderer")?.let { parseVideoRenderer(it)?.let(output::add) }
                node.optJSONObject("endScreenVideoRenderer")?.let { parseVideoRenderer(it)?.let(output::add) }
                node.optJSONObject("musicResponsiveListItemRenderer")?.let {
                    parseMusicRenderer(it)?.let(output::add)
                }
                val keys = node.keys()
                while (keys.hasNext()) collect(node.opt(keys.next()), output)
            }
            is JSONArray -> for (index in 0 until node.length()) collect(node.opt(index), output)
        }
    }

    private fun parseVideoRenderer(renderer: JSONObject): YouTubeSearchItem? {
        val videoId = renderer.optString("videoId").takeIf(String::isNotBlank) ?: return null
        val title = renderer.optJSONObject("title").text().ifBlank { return null }
        val channel = sequenceOf("ownerText", "longBylineText", "shortBylineText")
            .mapNotNull { renderer.optJSONObject(it)?.text()?.takeIf(String::isNotBlank) }
            .firstOrNull().orEmpty().ifBlank { "YouTube" }
        val badgeText = renderer.optJSONArray("ownerBadges").texts() + renderer.optJSONArray("badges").texts()
        val durationText = renderer.optJSONObject("lengthText").text()
            .ifBlank { renderer.optJSONObject("thumbnailOverlays").deepTextContaining(":") }
        val metadataText = renderer.optJSONObject("shortViewCountText").text()
            .ifBlank { renderer.optJSONObject("viewCountText").text() }
        return YouTubeSearchItem(
            videoId = videoId,
            title = title,
            artist = channel.removeSuffix(" - Topic"),
            channel = channel,
            artworkUrl = renderer.optJSONObject("thumbnail").largestThumbnail(),
            durationMs = parseDuration(durationText),
            popularity = parsePopularity(metadataText),
            isOfficial = channel.endsWith(" - Topic") ||
                badgeText.any { it.contains("verified", true) || it.contains("official artist", true) } ||
                title.contains("official audio", true),
            type = YouTubeResultType.VIDEO
        )
    }

    private fun parseMusicRenderer(renderer: JSONObject): YouTubeSearchItem? {
        val columns = renderer.optJSONArray("flexColumns")
        val texts = buildList {
            if (columns != null) for (index in 0 until columns.length()) {
                columns.optJSONObject(index)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.let(::add)
            }
        }
        val titleObject = texts.firstOrNull() ?: return null
        val titleRuns = titleObject.optJSONArray("runs")
        val title = titleObject.text().ifBlank { return null }
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf(String::isNotBlank)
            ?: titleRuns?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf(String::isNotBlank)
            ?: return null
        val secondaryRuns = texts.getOrNull(1)?.optJSONArray("runs")
        val secondaryValues = secondaryRuns.values()
        val artist = secondaryValues.firstOrNull().orEmpty().ifBlank { "YouTube Music" }
        val album = secondaryValues.getOrNull(1)
        val duration = renderer.optJSONArray("fixedColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            .text()
        val badges = renderer.optJSONArray("badges").texts()
        return YouTubeSearchItem(
            videoId = videoId,
            title = title,
            artist = artist,
            channel = artist,
            album = album,
            artworkUrl = renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                .largestThumbnail(),
            durationMs = parseDuration(duration),
            isExplicit = badges.any { it.contains("explicit", true) }.takeIf { it },
            isOfficial = badges.any { it.contains("official", true) },
            type = YouTubeResultType.SONG
        )
    }

    private fun JSONObject?.text(): String {
        if (this == null) return ""
        optString("simpleText").takeIf(String::isNotBlank)?.let { return it }
        return optJSONArray("runs").values().joinToString("")
    }

    private fun JSONArray?.values(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.optString("text")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun JSONArray?.texts(): List<String> {
        if (this == null) return emptyList()
        val result = mutableListOf<String>()
        for (index in 0 until length()) {
            val item = opt(index)
            when (item) {
                is JSONObject -> item.deepTexts(result)
                is String -> result += item
            }
        }
        return result
    }

    private fun JSONObject?.largestThumbnail(): String? {
        val thumbnails = this?.optJSONArray("thumbnails") ?: return null
        return (0 until thumbnails.length()).mapNotNull { thumbnails.optJSONObject(it) }
            .maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }
            ?.optString("url")
            ?.takeIf { it.startsWith("https://") }
    }

    private fun JSONObject?.deepTextContaining(marker: String): String {
        if (this == null) return ""
        val texts = mutableListOf<String>()
        deepTexts(texts)
        return texts.firstOrNull { marker in it }.orEmpty()
    }

    private fun JSONObject.deepTexts(output: MutableList<String>) {
        optString("text").takeIf(String::isNotBlank)?.let(output::add)
        optString("label").takeIf(String::isNotBlank)?.let(output::add)
        val keys = keys()
        while (keys.hasNext()) {
            when (val value = opt(keys.next())) {
                is JSONObject -> value.deepTexts(output)
                is JSONArray -> for (index in 0 until value.length()) {
                    (value.opt(index) as? JSONObject)?.deepTexts(output)
                }
            }
        }
    }

    private fun parseDuration(value: String): Long? {
        val parts = value.trim().split(':').mapNotNull(String::toLongOrNull)
        if (parts.size !in 2..3) return null
        val seconds = parts.fold(0L) { total, part -> total * 60L + part }
        return seconds.takeIf { it > 0L }?.times(1000L)
    }

    private fun parsePopularity(value: String): Long? {
        val normalized = value.lowercase().replace(",", "").replace(" ", "")
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)([kmb]?)").find(normalized) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (amount * multiplier).toLong()
    }
}
