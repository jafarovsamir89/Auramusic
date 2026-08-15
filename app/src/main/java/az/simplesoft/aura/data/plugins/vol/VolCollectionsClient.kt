package az.simplesoft.aura.data.plugins.vol

import az.simplesoft.aura.domain.playlist.WorldPlaylist
import az.simplesoft.aura.domain.playlist.WorldPlaylistItem
import az.simplesoft.aura.domain.playlist.WorldPlaylistKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Loads Vol.az's public regional charts as metadata-only playlist cards. */
internal class VolCollectionsClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val parser: VolSearchParser = VolSearchParser()
) {
    suspend fun featured(): List<WorldPlaylist> = withContext(Dispatchers.IO) {
        listOfNotNull(
            runCatching { fetch("${VolSelectors.BASE_URL}yeni-mahnilar", "Новинки (AZ/TR)", WorldPlaylistKind.NEW_RELEASES) }.getOrNull(),
            runCatching { fetch("${VolSelectors.BASE_URL}populyar-mahnilar", "ТОП (AZ/TR)", WorldPlaylistKind.TOP_WEEK) }.getOrNull()
        )
    }

    private fun fetch(url: String, title: String, kind: WorldPlaylistKind): WorldPlaylist {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "az-AZ,az;q=0.9,tr;q=0.8,ru;q=0.7")
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Vol.az chart HTTP ${response.code}" }
            val items = parser.parseCategory(response.body?.string().orEmpty(), 50).mapIndexed { index, item ->
                WorldPlaylistItem(
                    rank = index + 1,
                    artist = item.artist,
                    title = item.title,
                    sourceUrl = item.detailUrl,
                    providerId = VolMusicPlugin.ID,
                    durationMs = item.durationMs,
                    artworkUrl = item.artworkUrl
                )
            }
            return WorldPlaylist(
                id = "vol:${kind.name.lowercase()}",
                title = title,
                description = "Региональная подборка",
                region = "az-tr",
                language = "az,tr",
                kind = kind,
                source = "vol",
                updatedAt = "online",
                items = items,
                visualKey = if (kind == WorldPlaylistKind.NEW_RELEASES) "sunset" else "gold",
                badge = if (kind == WorldPlaylistKind.NEW_RELEASES) "НОВИНКИ" else "ТОП AZ/TR",
                featured = true,
                collectionUrl = url
            )
        }
    }

    private companion object {
        const val USER_AGENT = "AuraMusic/0.6 (Android; Vol.az authorized catalog)"
    }
}
