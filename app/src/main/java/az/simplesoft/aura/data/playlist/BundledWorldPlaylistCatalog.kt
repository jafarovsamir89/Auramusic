package az.simplesoft.aura.data.playlist

import android.content.Context
import az.simplesoft.aura.domain.playlist.WorldPlaylist
import az.simplesoft.aura.domain.playlist.WorldPlaylistCatalog
import az.simplesoft.aura.domain.playlist.WorldPlaylistItem
import az.simplesoft.aura.domain.playlist.WorldPlaylistKind
import org.json.JSONArray
import org.json.JSONObject

/** Loads only compact metadata. Audio, cookies and direct playback URLs are never bundled. */
class BundledWorldPlaylistCatalog(private val context: Context) {
    fun load(): WorldPlaylistCatalog = runCatching {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        parse(JSONObject(json)).sanitized()
    }.getOrElse { WorldPlaylistCatalog(1, "bundled-fallback", emptyList()) }

    private fun parse(root: JSONObject): WorldPlaylistCatalog {
        val playlists = root.optJSONArray("playlists") ?: JSONArray()
        return WorldPlaylistCatalog(
            version = root.optInt("version", 1),
            generatedAt = root.optString("generatedAt", "unknown"),
            playlists = buildList {
                for (index in 0 until playlists.length()) {
                    val value = playlists.optJSONObject(index) ?: continue
                    val items = value.optJSONArray("items") ?: JSONArray()
                    add(
                        WorldPlaylist(
                            id = value.optString("id"),
                            title = value.optString("title"),
                            description = value.optString("description"),
                            region = value.optString("region", "world"),
                            language = value.optString("language", "multi"),
                            kind = value.optString("kind").toKind(),
                            source = value.optString("source", "bundled"),
                            updatedAt = value.optString("updatedAt", root.optString("generatedAt")),
                            items = parseItems(items),
                            visualKey = value.optString("visualKey", "default"),
                            badge = value.optString("badge"),
                            featured = value.optBoolean("featured", true)
                        )
                    )
                }
            }
        )
    }

    private fun parseItems(items: JSONArray): List<WorldPlaylistItem> = buildList {
        for (index in 0 until items.length()) {
            val value = items.optJSONObject(index) ?: continue
            add(
                WorldPlaylistItem(
                    rank = value.optInt("rank", index + 1),
                    artist = value.optString("artist"),
                    title = value.optString("title"),
                    year = value.optInt("year").takeIf { it > 0 },
                    musicBrainzId = value.optString("musicBrainzId").takeIf(String::isNotBlank),
                    sourceUrl = value.optString("sourceUrl").takeIf(String::isNotBlank),
                    sourceScore = value.optDouble("sourceScore").takeIf { !it.isNaN() }
                )
            )
        }
    }

    private fun String.toKind(): WorldPlaylistKind = runCatching {
        WorldPlaylistKind.valueOf(uppercase())
    }.getOrDefault(WorldPlaylistKind.EDITORIAL)

    private companion object {
        const val ASSET_PATH = "playlists/world_playlists.json"
    }
}
