package az.simplesoft.aura.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class MusicSource(
    val id: String,
    val name: String,
    val subtitle: String,
    val accent: Long,
    val searchUrl: (String) -> String
)

object MusicProviders {
    private fun enc(query: String) =
        URLEncoder.encode(query, StandardCharsets.UTF_8.toString())

    val all = listOf(
        MusicSource(
            id = "zaycev",
            name = "Zaycev",
            subtitle = "Популярная музыка",
            accent = 0xFF98B6FF,
            searchUrl = { q -> "https://zaycev.net/search.html?query_search=${enc(q)}" }
        ),
        MusicSource(
            id = "soundcloud",
            name = "SoundCloud",
            subtitle = "Артисты и миксы",
            accent = 0xFFFF9A67,
            searchUrl = { q -> "https://soundcloud.com/search?q=${enc(q)}" }
        ),
        MusicSource(
            id = "audiomack",
            name = "Audiomack",
            subtitle = "Хиты и новинки",
            accent = 0xFFFFD262,
            searchUrl = { q -> "https://audiomack.com/search?q=${enc(q)}" }
        ),
        MusicSource(
            id = "bandcamp",
            name = "Bandcamp",
            subtitle = "Независимая сцена",
            accent = 0xFF5EDBD3,
            searchUrl = { q -> "https://bandcamp.com/search?q=${enc(q)}" }
        ),
        MusicSource(
            id = "jamendo",
            name = "Jamendo",
            subtitle = "Свободная музыка",
            accent = 0xFFB99AFF,
            searchUrl = { q -> "https://www.jamendo.com/search?q=${enc(q)}" }
        )
    )
}
