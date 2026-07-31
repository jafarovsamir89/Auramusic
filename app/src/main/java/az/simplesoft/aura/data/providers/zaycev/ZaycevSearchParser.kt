package az.simplesoft.aura.data.providers.zaycev

import az.simplesoft.aura.data.providers.TrackCandidate
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class ZaycevSearchParser {
    fun parseApiJson(body: String): List<TrackCandidate> {
        val tracks = JSONObject(body).optJSONObject("tracks") ?: return emptyList()
        return parseTracksObject(tracks)
    }

    fun parseHtml(body: String, baseUrl: String = ZaycevSelectors.BASE_URL): List<TrackCandidate> {
        val document = Jsoup.parse(body, baseUrl)
        document.selectFirst(ZaycevSelectors.NEXT_DATA)?.data()?.takeIf(String::isNotBlank)?.let { json ->
            runCatching { findTracksObject(JSONObject(json)) }.getOrNull()?.let { tracks ->
                parseTracksObject(tracks).takeIf(List<TrackCandidate>::isNotEmpty)?.let { return it }
            }
        }
        return document.select(ZaycevSelectors.TRACK_LINK).mapNotNull { link ->
            val id = TRACK_ID.find(link.attr("href"))?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val row = link.closest("li") ?: link.parent()
            val artist = row?.selectFirst(ZaycevSelectors.ARTIST_LINK)?.text().orEmpty()
            val title = link.text().replace(Regex("\\s*18\\+\\s*$"), "").trim()
            if (title.isBlank() || artist.isBlank()) null else TrackCandidate(
                providerId = ZaycevProvider.ID,
                id = id,
                title = title,
                artist = artist,
                detailUrl = link.absUrl("href").ifBlank { detailUrl(id) },
                artworkUrl = row?.selectFirst("img")?.absUrl("src"),
                durationMs = row?.selectFirst("time")?.text()?.let(::durationMs)
            )
        }.distinctBy(TrackCandidate::id)
    }

    private fun parseTracksObject(tracks: JSONObject): List<TrackCandidate> {
        val info = tracks.optJSONObject("tracksInfo") ?: return emptyList()
        val ids = tracks.optJSONArray("trackIds") ?: JSONArray(info.keys().asSequence().toList())
        return buildList {
            for (index in 0 until ids.length()) {
                val id = ids.get(index).toString()
                val item = info.optJSONObject(id) ?: continue
                if (!item.optBoolean("playbackEnabled", true) || item.optBoolean("notAvailable", false)) continue
                val title = item.optString("track").trim()
                val artist = item.optString("artistName").trim()
                if (title.isBlank() || artist.isBlank()) continue
                add(
                    TrackCandidate(
                        providerId = ZaycevProvider.ID,
                        id = id,
                        title = title,
                        artist = artist,
                        detailUrl = detailUrl(id),
                        artworkUrl = item.optString("imageWebp").ifBlank { item.optString("imageJpg") }.ifBlank { null },
                        durationMs = item.optString("duration").takeIf(String::isNotBlank)?.let(::durationMs),
                        bitrateKbps = item.optInt("bitrate").takeIf { it > 0 },
                        playbackToken = item.optString("streaming").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun findTracksObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> {
            if (value.has("tracksInfo") && value.has("trackIds")) value
            else value.keys().asSequence().mapNotNull { findTracksObject(value.opt(it)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findTracksObject(value.opt(it)) }.firstOrNull()
        else -> null
    }

    companion object {
        private val TRACK_ID = Regex("/pages/\\d+/(\\d+)\\.shtml")

        fun detailUrl(id: String): String =
            "${ZaycevSelectors.BASE_URL}pages/${id.dropLast(2).ifBlank { id }}/$id.shtml"

        fun durationMs(value: String): Long? {
            val parts = value.trim().split(':').mapNotNull(String::toLongOrNull)
            if (parts.size !in 2..3) return null
            return parts.fold(0L) { total, part -> total * 60 + part } * 1000L
        }
    }
}
