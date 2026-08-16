package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.Track

/** Chooses a station that can be started immediately from the radio catalogue. */
object RadioPlaybackSelector {
    fun firstPlayable(stations: List<Track>): Track? = stations.firstOrNull { station ->
        station.sourceId == "radio_browser" &&
            station.isPlayable &&
            !station.streamUrl.isNullOrBlank()
    }

    fun next(stations: List<Track>, currentId: String): Track? = step(stations, currentId, 1)

    fun previous(stations: List<Track>, currentId: String): Track? = step(stations, currentId, -1)

    private fun step(stations: List<Track>, currentId: String, delta: Int): Track? {
        val playable = stations.filter { it.sourceId == "radio_browser" && it.isPlayable && !it.streamUrl.isNullOrBlank() }
        if (playable.isEmpty()) return null
        val currentIndex = playable.indexOfFirst { it.id == currentId }
        val target = if (currentIndex < 0) 0 else (currentIndex + delta).mod(playable.size)
        return playable[target]
    }
}
