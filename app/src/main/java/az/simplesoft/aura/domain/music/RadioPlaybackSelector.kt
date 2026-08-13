package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.Track

/** Chooses a station that can be started immediately from the radio catalogue. */
object RadioPlaybackSelector {
    fun firstPlayable(stations: List<Track>): Track? = stations.firstOrNull { station ->
        station.sourceId == "radio_browser" &&
            station.isPlayable &&
            !station.streamUrl.isNullOrBlank()
    }
}
