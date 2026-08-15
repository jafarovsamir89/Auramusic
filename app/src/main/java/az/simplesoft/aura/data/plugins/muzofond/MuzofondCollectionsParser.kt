package az.simplesoft.aura.data.plugins.muzofond

import az.simplesoft.aura.domain.playlist.WorldPlaylist
import az.simplesoft.aura.domain.playlist.WorldPlaylistItem
import az.simplesoft.aura.domain.playlist.WorldPlaylistKind
import java.net.URI
import kotlin.math.absoluteValue
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class MuzofondCollectionsParser(
    private val baseUrl: String = MuzofondSelectors.BASE_URL
) {
    fun parseCollections(html: String, limit: Int): List<WorldPlaylist> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("div.item[data-id] > a[href]")
            .mapNotNull { link ->
                val url = link.absUrl("href").takeIf(::isCollectionUrl) ?: return@mapNotNull null
                val title = link.selectFirst(".title")?.text()?.trim().orEmpty()
                    .ifBlank { link.attr("title").trim() }
                if (title.isBlank()) return@mapNotNull null
                WorldPlaylist(
                    id = "muzofond:${url.substringAfter("/collections/")}",
                    title = title,
                    description = "Онлайн-подборка",
                    region = "world",
                    language = "multi",
                    kind = kindFor(url),
                    source = "muzofond",
                    updatedAt = "online",
                    items = emptyList(),
                    visualKey = visualKeyFor(kindFor(url)),
                    badge = badgeFor(kindFor(url)),
                    featured = true,
                    collectionUrl = url
                )
            }
            .distinctBy(WorldPlaylist::collectionUrl)
            .take(limit.coerceIn(1, 240))
    }

    /** The popular page is Muzofond's public genre directory. Keep only unique
     * genre links; the actual tracks are fetched lazily when a genre is opened. */
    fun parseGenres(html: String, limit: Int): List<WorldPlaylist> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("a[href^='/popular/'], a[href^='${MuzofondSelectors.BASE_URL}popular/']")
            .mapNotNull { link ->
                val url = link.absUrl("href").takeIf(::isGenreUrl) ?: return@mapNotNull null
                val title = link.text().trim()
                if (title.isBlank()) return@mapNotNull null
                WorldPlaylist(
                    id = "muzofond:genre:${url.substringAfter("/popular/")}",
                    title = title,
                    description = "Музыкальный жанр",
                    region = "world",
                    language = "multi",
                    kind = WorldPlaylistKind.MOOD,
                    source = "muzofond",
                    updatedAt = "online",
                    items = emptyList(),
                    visualKey = "genre-${title.lowercase().hashCode().absoluteValue % 6}",
                    badge = "ЖАНР",
                    featured = false,
                    collectionUrl = url
                )
            }
            .distinctBy(WorldPlaylist::collectionUrl)
            .take(limit.coerceIn(1, 80))
    }

    fun parseCollection(html: String, sourceUrl: String, limit: Int): WorldPlaylist {
        val document = Jsoup.parse(html, sourceUrl)
        val title = document.selectFirst("h1")?.text()?.trim().orEmpty().ifBlank { "Подборка Muzofond" }
        val kind = kindFor(sourceUrl)
        // Collections use `ul.mainSongs`; genre pages expose the same track
        // rows directly under the page content, so accept both layouts.
        val items = document.select("li.item[data-id]")
            .mapNotNull(::parseItem)
            .distinctBy { "${it.artist.lowercase()}\u0000${it.title.lowercase()}" }
            .take(limit.coerceIn(1, 100))
        return WorldPlaylist(
            id = "muzofond:${sourceUrl.substringAfter("/collections/")}",
            title = title,
            description = "Онлайн-подборка",
            region = "world",
            language = "multi",
            kind = kind,
            source = "muzofond",
            updatedAt = "online",
            items = items,
            visualKey = visualKeyFor(kind),
            badge = badgeFor(kind),
            featured = true,
            collectionUrl = sourceUrl
        )
    }

    private fun parseItem(item: Element): WorldPlaylistItem? {
        val id = item.attr("data-id").trim().takeIf(String::isNotBlank) ?: return null
        val artist = item.selectFirst(".artist")?.text()?.trim().orEmpty()
        val title = item.selectFirst(".track")?.text()?.trim().orEmpty()
        val streamUrl = item.selectFirst("li.play[data-url]")?.attr("data-url")?.trim()
            ?.takeIf(::isAudioUrl)
        if (artist.isBlank() || title.isBlank() || streamUrl == null) return null
        val detailUrl = item.selectFirst("a[data-trackLink]")?.absUrl("href")
            ?.takeIf { it.startsWith(MuzofondSelectors.TRACK_PREFIX) }
        val durationSeconds = item.attr("data-duration").toLongOrNull()
        return WorldPlaylistItem(
            rank = item.parent()?.children()?.indexOf(item)?.plus(1) ?: 1,
            artist = artist,
            title = title,
            sourceUrl = detailUrl,
            providerId = MuzofondMusicPlugin.ID,
            playbackToken = streamUrl,
            artworkUrl = item.attr("data-img").takeIf(String::isNotBlank)?.let { absolute(it) },
            durationMs = durationSeconds?.times(1_000L)
        )
    }

    private fun absolute(url: String): String = when {
        url.startsWith("http") -> url
        else -> URI(baseUrl).resolve(url).toString()
    }

    private fun isCollectionUrl(url: String): Boolean =
        url.startsWith("${MuzofondSelectors.BASE_URL}collections/") &&
            !url.endsWith("/collections")

    private fun isGenreUrl(url: String): Boolean =
        url.startsWith("${MuzofondSelectors.BASE_URL}popular/") &&
            !url.endsWith("/popular")

    private fun isAudioUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return url.startsWith("https://") &&
            (host == MuzofondSelectors.HOST || host.endsWith(".${MuzofondSelectors.HOST}"))
    }

    private fun kindFor(url: String): WorldPlaylistKind = when {
        "/collections/new" in url -> WorldPlaylistKind.NEW_RELEASES
        "/collections/top" in url -> WorldPlaylistKind.TOP_WEEK
        else -> WorldPlaylistKind.EDITORIAL
    }

    private fun visualKeyFor(kind: WorldPlaylistKind): String = when (kind) {
        WorldPlaylistKind.NEW_RELEASES -> "sunset"
        WorldPlaylistKind.TOP_WEEK -> "gold"
        else -> "classic"
    }

    private fun badgeFor(kind: WorldPlaylistKind): String = when (kind) {
        WorldPlaylistKind.NEW_RELEASES -> "НОВИНКИ"
        WorldPlaylistKind.TOP_WEEK -> "ПОПУЛЯРНОЕ"
        else -> "ПОДБОРКА"
    }
}
