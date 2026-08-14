package az.simplesoft.aura.data.plugins.muzofond

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class MuzofondSearchParser(
    private val baseUrl: String = MuzofondSelectors.BASE_URL
) {
    fun parseSearch(html: String, limit: Int): List<MuzofondSearchItem> {
        val document = Jsoup.parse(html, baseUrl)
        val items = document.select("li.item").mapNotNull(::parseItem)
        if (items.isEmpty()) {
            throw MuzofondPluginException(
                MuzofondFailureReason.NOT_FOUND,
                "Muzofond returned no playable search results"
            )
        }
        return items.distinctBy(MuzofondSearchItem::id).take(limit.coerceIn(1, 50))
    }

    private fun parseItem(item: Element): MuzofondSearchItem? {
        val detailUrl = item.selectFirst("a[data-trackLink]")
            ?.absUrl("href")
            ?.takeIf(::isTrackUrl)
            ?: return null
        val streamUrl = item.selectFirst("li.play[data-url]")
            ?.attr("data-url")
            ?.takeIf(::isAudioUrl)
            ?: return null
        val title = item.selectFirst(".track")?.text()?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = item.selectFirst(".artist")?.text()?.trim()
            .orEmpty()
            .ifBlank { "Muzofond" }
        val id = detailUrl.substringAfterLast('/').substringBefore('?')
            .takeIf(String::isNotBlank)
            ?: item.attr("data-id").takeIf(String::isNotBlank)
            ?: return null
        return MuzofondSearchItem(
            id = id,
            title = title,
            artist = artist,
            detailUrl = detailUrl,
            streamUrl = streamUrl,
            artworkUrl = item.selectFirst("img[src]")?.absUrl("src")?.takeIf(String::isNotBlank),
            durationMs = item.selectFirst(".duration, .trackDuration, .time")?.text()?.let(::parseDuration),
            bitrateKbps = parseBitrate(item.text())
        )
    }

    private fun isTrackUrl(url: String): Boolean =
        url.startsWith(MuzofondSelectors.TRACK_PREFIX)

    private fun isAudioUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return url.startsWith("https://") &&
            (host == MuzofondSelectors.HOST || host.endsWith(".${MuzofondSelectors.HOST}"))
    }

    private fun parseDuration(text: String): Long? {
        val match = DURATION.find(text) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        return (minutes * 60L + seconds) * 1_000L
    }

    private fun parseBitrate(text: String): Int? =
        BITRATE.find(text)?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        private val DURATION = Regex("(?<!\\d)(\\d{1,3}):(\\d{2})(?!\\d)")
        private val BITRATE = Regex("(?i)(\\d{2,3})\\s*k(?:b|bit)\\s*/?\\s*s")
    }
}

internal object MuzofondSelectors {
    const val BASE_URL = "https://muzofond.fm/"
    const val HOST = "muzofond.fm"
    const val TRACK_PREFIX = "https://muzofond.fm/track/"
    const val SEARCH_PREFIX = "https://muzofond.fm/search/"
}
