package az.simplesoft.aura.domain.playlist

/** A playlist assembled from a public collection source; playback data is loaded on demand. */
data class WorldPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val region: String,
    val language: String,
    val kind: WorldPlaylistKind,
    val source: String,
    val updatedAt: String,
    val items: List<WorldPlaylistItem>,
    /** Stable visual identity for the home screen; never affects playback or ranking. */
    val visualKey: String = "default",
    val badge: String = "",
    /** Artist and other niche collections are available on demand, not promoted to everyone. */
    val featured: Boolean = true,
    val collectionUrl: String? = null
)

enum class WorldPlaylistKind {
    TOP_TODAY,
    TOP_WEEK,
    REGIONAL,
    NEW_RELEASES,
    DECADE,
    ARTIST_HITS,
    MOOD,
    EDITORIAL
}

data class WorldPlaylistItem(
    val rank: Int,
    val artist: String,
    val title: String,
    val year: Int? = null,
    val musicBrainzId: String? = null,
    val sourceUrl: String? = null,
    val sourceScore: Double? = null,
    val providerId: String? = null,
    val playbackToken: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null
)

data class WorldPlaylistCatalog(
    val version: Int,
    val generatedAt: String,
    val playlists: List<WorldPlaylist>
) {
    /** Keeps malformed or unexpectedly large snapshots from destabilizing the app. */
    fun sanitized(maxPlaylists: Int = 32, maxItemsPerPlaylist: Int = 100): WorldPlaylistCatalog = copy(
        playlists = playlists.asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy(WorldPlaylist::id)
            .take(maxPlaylists)
            .map { playlist ->
                playlist.copy(
                    items = playlist.items.asSequence()
                        .filter { it.artist.isNotBlank() && it.title.isNotBlank() }
                        .distinctBy { "${it.artist.lowercase()}\u0000${it.title.lowercase()}" }
                        .take(maxItemsPerPlaylist)
                        .toList()
                )
            }
            .toList()
    )

    fun find(query: String): WorldPlaylist? {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return null
        return playlists.firstOrNull { playlist ->
            playlist.id.equals(needle, ignoreCase = true) ||
                playlist.title.lowercase().contains(needle) ||
                needle.contains(playlist.title.lowercase())
        }
    }
}
