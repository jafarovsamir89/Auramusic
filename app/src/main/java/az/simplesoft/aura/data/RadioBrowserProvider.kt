package az.simplesoft.aura.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class RadioCountry(
    val code: String,
    val name: String,
    val stationCount: Int
)

class RadioBrowserProvider {
    @Volatile
    private var discoveredHosts: List<String>? = null

    suspend fun countries(limit: Int = 80): List<RadioCountry> = requestJson(
        "/json/countrycodes?order=stationcount&reverse=true&limit=$limit"
    ) { parseCountries(it) }

    suspend fun popular(limit: Int = 30): List<Track> = requestJson(
        "/json/stations/topclick/$limit?hidebroken=true"
    ) { parseStations(it) }

    suspend fun byCountry(countryCode: String, limit: Int = 60): List<Track> {
        val code = countryCode.trim().uppercase(Locale.ROOT).take(2)
        return requestJson(
            "/json/stations/search?countrycode=$code&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
        ) { parseStations(it) }
    }

    suspend fun search(query: String, countryCode: String? = null, limit: Int = 30): List<Track> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val country = countryCode?.trim()?.uppercase(Locale.ROOT)?.take(2)
            ?.let { "&countrycode=$it" }.orEmpty()
        return requestJson(
            "/json/stations/search?name=$encoded$country&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
        ) { parseStations(it) }.ifEmpty {
            requestJson(
                "/json/stations/search?tag=$encoded$country&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
            ) { parseStations(it) }
        }
    }

    suspend fun registerClick(stationId: String) = withContext(Dispatchers.IO) {
        for (host in hosts()) {
            val completed = runCatching {
                val connection = open("$host/json/url/$stationId")
                connection.inputStream.use { it.readBytes() }
                connection.disconnect()
            }.isSuccess
            if (completed) break
        }
    }

    private suspend fun <T> requestJson(path: String, parser: (String) -> T): T = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (host in hosts()) {
            try {
                val connection = open(host + path)
                val code = connection.responseCode
                if (code !in 200..299) {
                    connection.disconnect()
                    error("Radio Browser returned HTTP $code")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                return@withContext parser(body)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Radio Browser недоступен")
    }

    private fun hosts(): List<String> {
        discoveredHosts?.let { return it }
        val discovered = runCatching {
            InetAddress.getAllByName(DISCOVERY_HOST)
                .mapNotNull { address ->
                    address.canonicalHostName
                        .lowercase(Locale.ROOT)
                        .takeIf { it != address.hostAddress && it.endsWith(".api.radio-browser.info") }
                }
                .distinct()
                .map { "https://$it" }
        }.getOrDefault(emptyList())
        // Known healthy HTTPS endpoints lead; DNS-discovered mirrors remain automatic failover targets.
        return (FALLBACK_HOSTS + discovered).distinct().also { discoveredHosts = it }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 12_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "application/json")
        instanceFollowRedirects = true
    }

    companion object {
        private const val DISCOVERY_HOST = "all.api.radio-browser.info"
        private const val USER_AGENT = "AuraMusic/0.3 (Android; github.com/jafarovsamir89/Auramusic)"
        private val FALLBACK_HOSTS = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://fi1.api.radio-browser.info"
        )

        internal fun parseCountries(body: String, locale: Locale = Locale("ru")): List<RadioCountry> {
            val result = mutableListOf<RadioCountry>()
            val items = JSONArray(body)
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val code = item.optString("name").trim().uppercase(Locale.ROOT)
                if (code.length != 2) continue
                val name = Locale.Builder().setRegion(code).build().getDisplayCountry(locale)
                    .ifBlank { code }
                result += RadioCountry(code, name.replaceFirstChar { it.titlecase(locale) }, item.optInt("stationcount"))
            }
            return result.distinctBy(RadioCountry::code)
        }

        internal fun parseStations(body: String): List<Track> {
            val result = mutableListOf<Track>()
            val seenStreams = mutableSetOf<String>()
            val items = JSONArray(body)
            for (index in 0 until items.length()) {
                val station = items.getJSONObject(index)
                val stream = station.optString("url_resolved").trim()
                // AURA keeps cleartext traffic disabled; secure stations work reliably on modern Android.
                if (!stream.startsWith("https://") || !seenStreams.add(stream)) continue
                val id = station.optString("stationuuid").ifBlank { stream.hashCode().toString() }
                val name = station.optString("name").trim().ifBlank { "Internet Radio" }
                val tags = station.optString("tags").split(',').map(String::trim).filter(String::isNotBlank)
                val country = station.optString("country").trim()
                val codec = station.optString("codec").trim()
                val bitrate = station.optInt("bitrate").takeIf { it > 0 }
                val description = buildList {
                    if (country.isNotBlank()) add(country)
                    tags.take(1).forEach(::add)
                    if (codec.isNotBlank()) add(codec + bitrate?.let { " · $it kbps" }.orEmpty())
                }.joinToString(" · ").ifBlank { "Internet Radio" }
                result += Track(
                    id = id,
                    title = name,
                    artist = description,
                    artworkUrl = station.optString("favicon").takeIf { it.startsWith("https://") },
                    durationMs = null,
                    sourceId = "radio_browser",
                    // The durable HTTPS stream is intentionally stored here so Room playlists can restore it.
                    sourcePageUrl = stream,
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
}
