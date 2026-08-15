package az.simplesoft.aura.data.plugins.vol

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class VolSearchParser(
    private val baseUrl: String = VolSelectors.BASE_URL
) {
    fun parseSearch(html: String, limit: Int): List<VolSearchItem> {
        val document = Jsoup.parse(html, baseUrl)
        val items = document.select(".playlis p").mapNotNull(::parseItem)
            .distinctBy(VolSearchItem::id)
            .take(limit.coerceIn(1, VolSelectors.MAX_RESULTS))
        if (items.isEmpty()) {
            throw VolPluginException(VolFailureReason.NOT_FOUND, "Vol.az returned no search results")
        }
        return items
    }

    /** Parses Vol.az's public chart/new-release pages, which use `.mp3ul li`
     * rows instead of the search page's `.playlis p` rows. */
    fun parseCategory(html: String, limit: Int): List<VolSearchItem> {
        val document = Jsoup.parse(html, baseUrl)
        val items = document.select(".mp3ul li").mapNotNull(::parseItem)
            .distinctBy(VolSearchItem::id)
            .take(limit.coerceIn(1, VolSelectors.MAX_RESULTS))
        if (items.isEmpty()) {
            throw VolPluginException(VolFailureReason.NOT_FOUND, "Vol.az returned no category results")
        }
        return items
    }

    fun parsePlayback(html: String, detailUrl: String): VolPlaybackData {
        val document = Jsoup.parse(html, detailUrl)
        val button = document.selectFirst("#listenbut[data-dt][data-hs]")
            ?: throw VolPluginException(VolFailureReason.NOT_PLAYABLE, "Vol.az playback token is unavailable")
        val dataDt = button.attr("data-dt").takeIf(String::isNotBlank)
            ?: throw VolPluginException(VolFailureReason.PARSE_CHANGED, "Vol.az playback token is empty")
        val dataHs = button.attr("data-hs").takeIf(String::isNotBlank)
            ?: throw VolPluginException(VolFailureReason.PARSE_CHANGED, "Vol.az playback signature is empty")
        return VolPlaybackData(
            streamUrl = "${VolSelectors.AJAX_URL}?&$dataDt&hs=$dataHs",
            artworkUrl = document.selectFirst("#coverimg[src]")?.absUrl("src")?.takeIf(String::isNotBlank),
            durationMs = document.select(".musicline").firstOrNull { it.text().contains("Müddəti", ignoreCase = true) }
                ?.text()
                ?.let(::parseDuration)
        )
    }

    fun parseAjaxPlayback(html: String): VolPlaybackData {
        val document = Jsoup.parse(html, VolSelectors.BASE_URL)
        val sourceUrl = document.selectFirst("audio source[src]")?.absUrl("src")
            ?.takeIf(::isVolAudioUrl)
            ?: throw VolPluginException(VolFailureReason.NOT_PLAYABLE, "Vol.az audio URL is unavailable")
        val expiresAt = Regex("(?:[?&])e=(\\d+)").find(sourceUrl)?.groupValues?.get(1)?.toLongOrNull()
        return VolPlaybackData(streamUrl = sourceUrl, expiresAt = expiresAt)
    }

    private fun parseItem(item: Element): VolSearchItem? {
        val link = item.selectFirst("a[href]") ?: return null
        val detailUrl = link.absUrl("href").takeIf { it.startsWith(VolSelectors.TRACK_PREFIX) } ?: return null
        val id = Regex("-(\\d+)\\.html$").find(detailUrl)?.groupValues?.get(1) ?: return null
        val rawTitle = link.attr("title").ifBlank { item.text() }.trim()
        val title = rawTitle.replace(Regex("\\s+mp3(?:\\s+yukle)?\\s*$", RegexOption.IGNORE_CASE), "").trim()
        if (title.isBlank()) return null
        val parts = title.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        return VolSearchItem(
            id = id,
            title = parts.getOrElse(1) { title },
            artist = parts.firstOrNull()?.takeIf { parts.size > 1 } ?: "Vol.az",
            detailUrl = detailUrl
        )
    }

    private fun parseDuration(text: String): Long? {
        val match = Regex("(\\d{1,3}):(\\d{2})").find(text) ?: return null
        return ((match.groupValues[1].toLongOrNull() ?: return null) * 60L +
            (match.groupValues[2].toLongOrNull() ?: return null)) * 1_000L
    }

    private fun isVolAudioUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return url.startsWith("https://") && (host == VolSelectors.HOST || host.endsWith(".${VolSelectors.HOST}"))
    }
}

internal object VolSelectors {
    const val BASE_URL = "https://vol.az/"
    const val HOST = "vol.az"
    const val TRACK_PREFIX = "https://vol.az/"
    const val SEARCH_URL = "https://vol.az/search/"
    const val AJAX_URL = "https://vol.az/ajax.php"
    const val MAX_RESULTS = 50
}
