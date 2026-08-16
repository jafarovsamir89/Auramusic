package az.simplesoft.aura.data

enum class PlaybackType {
    DIRECT_STREAM,
    WEB,
    LOCAL
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val sourceId: String,
    val sourcePageUrl: String,
    val playbackType: PlaybackType,
    val streamUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val isPlayable: Boolean = true,
    val popularity: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val addedAt: Long? = null
)

object DemoCatalog {
    val tracks = listOf(
        Track(
            id = "aura-placeholder",
            title = "Найдите песню",
            artist = "AURA",
            sourceId = "aura",
            sourcePageUrl = "",
            playbackType = PlaybackType.DIRECT_STREAM,
            isPlayable = false
        )
    )
}
