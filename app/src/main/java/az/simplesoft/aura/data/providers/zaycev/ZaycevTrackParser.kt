package az.simplesoft.aura.data.providers.zaycev

import az.simplesoft.aura.data.providers.TrackCandidate
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class ZaycevTrackParser {
    fun enrich(html: String, original: TrackCandidate): TrackCandidate {
        val document = Jsoup.parse(html, original.detailUrl)
        val raw = document.selectFirst(ZaycevSelectors.NEXT_DATA)?.data().orEmpty()
        val info = runCatching { findTrackInfo(JSONObject(raw), original.id) }.getOrNull()
        if (info != null) {
            return original.copy(
                title = info.optString("track").ifBlank { original.title },
                artist = info.optString("artistName").ifBlank { original.artist },
                artworkUrl = info.optString("bigImageWebp").ifBlank {
                    info.optString("imageWebp").ifBlank { info.optString("imageJpg") }
                }.ifBlank { original.artworkUrl },
                durationMs = info.optLong("durationTime").takeIf { it > 0 }?.times(1000L) ?: original.durationMs,
                bitrateKbps = info.optInt("bitrate").takeIf { it > 0 } ?: original.bitrateKbps
            )
        }
        val heading = document.selectFirst("h1")?.text().orEmpty()
        val parts = heading.split(" - ", limit = 2)
        return if (parts.size == 2) original.copy(artist = parts[0].trim(), title = parts[1].trim()) else original
    }

    private fun findTrackInfo(value: Any?, id: String): JSONObject? = when (value) {
        is JSONObject -> {
            if (value.optString("id") == id && value.has("track") && value.has("artistName")) value
            else value.keys().asSequence().mapNotNull { findTrackInfo(value.opt(it), id) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findTrackInfo(value.opt(it), id) }.firstOrNull()
        else -> null
    }
}
