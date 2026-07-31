package az.simplesoft.aura.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class RadioBrowserProvider {
    private val hosts = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info"
    )

    suspend fun popular(limit: Int = 30): List<Track> = request(
        "/json/stations/topclick/$limit?hidebroken=true"
    )

    suspend fun search(query: String, limit: Int = 30): List<Track> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        return request(
            "/json/stations/search?name=$encoded&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
        ).ifEmpty {
            request(
                "/json/stations/search?tag=$encoded&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
            )
        }
    }

    suspend fun registerClick(stationId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open("${hosts.first()}/json/url/$stationId")
            connection.inputStream.close()
            connection.disconnect()
        }
    }

    private suspend fun request(path: String): List<Track> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (host in hosts) {
            try {
                val connection = open(host + path)
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                return@withContext parse(body)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Radio Browser недоступен")
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 12_000
        setRequestProperty("User-Agent", "AuraMusic/0.3")
        setRequestProperty("Accept", "application/json")
        instanceFollowRedirects = true
    }

    private fun parse(body: String): List<Track> {
        val result = mutableListOf<Track>()
        val items = JSONArray(body)
        for (index in 0 until items.length()) {
            val station = items.getJSONObject(index)
            val stream = station.optString("url_resolved").trim()
            if (!stream.startsWith("https://") || stream in result.mapNotNull(Track::streamUrl)) continue
            val id = station.optString("stationuuid").ifBlank { stream.hashCode().toString() }
            val name = station.optString("name").trim().ifBlank { "Internet Radio" }
            val tags = station.optString("tags").split(',').map(String::trim).filter(String::isNotBlank)
            val country = station.optString("country").trim()
            result += Track(
                id = id,
                title = name,
                artist = tags.take(2).joinToString(" · ").ifBlank { country.ifBlank { "Internet Radio" } },
                artworkUrl = station.optString("favicon").takeIf { it.startsWith("https://") },
                durationMs = null,
                sourceId = "radio_browser",
                sourcePageUrl = station.optString("homepage").takeIf { it.startsWith("https://") }
                    ?: "https://www.radio-browser.info",
                playbackType = PlaybackType.DIRECT_STREAM,
                streamUrl = stream,
                isPlayable = true,
                popularity = station.optInt("clickcount"),
                genre = tags.firstOrNull()
            )
        }
        return result
    }
}
