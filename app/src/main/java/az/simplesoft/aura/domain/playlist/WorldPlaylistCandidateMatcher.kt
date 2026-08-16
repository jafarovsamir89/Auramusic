package az.simplesoft.aura.domain.playlist

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.search.TrackMatcher

/** Selects only YouTube results that actually resemble the chart's artist and title. */
object WorldPlaylistCandidateMatcher {
    fun rank(item: WorldPlaylistItem, candidates: List<TrackCandidate>): List<TrackCandidate> {
        val request = MusicSearchRequest(
            rawQuery = "${item.artist} ${item.title}",
            artist = item.artist,
            title = item.title,
            autoPlay = false,
            preferredProviderId = "youtube",
            limit = candidates.size
        )
        return TrackMatcher.rank(request, candidates).filter { candidate ->
            TrackMatcher.similarity(item.title, candidate.title) >= TITLE_THRESHOLD &&
                TrackMatcher.similarity(item.artist, candidate.artist) >= ARTIST_THRESHOLD
        }
    }

    private const val TITLE_THRESHOLD = .55
    private const val ARTIST_THRESHOLD = .30
}
