package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.ArtistArtworkLookup
import az.simplesoft.aura.data.providers.TrackCandidate
import kotlin.math.abs

data class TrackIdentity(
    val normalizedTitle: String,
    val normalizedArtist: String,
    val durationBucketSeconds: Int?,
    val album: String? = null,
    val isrc: String? = null,
    val musicBrainzId: String? = null,
    val variants: Set<String> = emptySet()
)

data class UnifiedTrack(
    val identity: TrackIdentity,
    val metadata: Track,
    val alternatives: List<TrackCandidate>,
    val score: Double
)

class TrackIdentityResolver(
    private val titleThreshold: Double = .88,
    private val artistThreshold: Double = .86,
    private val durationToleranceMs: Long = 10_000L
) {
    fun identityFor(candidate: TrackCandidate): TrackIdentity = TrackIdentity(
        normalizedTitle = normalizedBaseTitle(candidate.title),
        normalizedArtist = TrackMatcher.normalize(candidate.artist),
        durationBucketSeconds = candidate.durationMs?.let { milliseconds ->
            (((milliseconds / 1000L) + 2L) / 5L * 5L).toInt()
        },
        album = candidate.album?.let(TrackMatcher::normalize),
        isrc = candidate.isrc?.uppercase(),
        musicBrainzId = candidate.musicBrainzId,
        variants = variants(candidate.title)
    )

    fun areSame(left: TrackCandidate, right: TrackCandidate): Boolean {
        if (!left.isrc.isNullOrBlank() && left.isrc.equals(right.isrc, ignoreCase = true)) return true
        if (!left.musicBrainzId.isNullOrBlank() && left.musicBrainzId == right.musicBrainzId) return true
        val leftIdentity = identityFor(left)
        val rightIdentity = identityFor(right)
        if (leftIdentity.variants != rightIdentity.variants) return false
        if (TrackMatcher.similarity(leftIdentity.normalizedTitle, rightIdentity.normalizedTitle) < titleThreshold) return false
        if (TrackMatcher.similarity(leftIdentity.normalizedArtist, rightIdentity.normalizedArtist) < artistThreshold) return false
        val leftDuration = left.durationMs
        val rightDuration = right.durationMs
        return leftDuration == null || rightDuration == null || abs(leftDuration - rightDuration) <= durationToleranceMs
    }

    fun unify(ranked: List<RankedCandidate>): List<UnifiedTrack> {
        val groups = mutableListOf<MutableList<RankedCandidate>>()
        ranked.forEach { item ->
            val group = groups.firstOrNull { existing -> areSame(existing.first().candidate, item.candidate) }
            if (group == null) groups += mutableListOf(item) else group += item
        }
        return groups.map { group ->
            val best = group.maxBy(RankedCandidate::score)
            val candidate = best.candidate
            UnifiedTrack(
                identity = identityFor(candidate),
                metadata = Track(
                    id = "${candidate.providerId}:${candidate.id}",
                    title = candidate.title,
                    artist = candidate.artist,
                    artworkUrl = group.asSequence()
                        .map { it.candidate.artworkUrl }
                        .firstOrNull(ArtistArtworkLookup::isUsable),
                    durationMs = candidate.durationMs,
                    sourceId = candidate.providerId,
                    sourcePageUrl = candidate.detailUrl,
                    playbackType = PlaybackType.DIRECT_STREAM,
                    isPlayable = true,
                    popularity = candidate.popularity?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                    year = candidate.year
                ),
                alternatives = group.sortedByDescending(RankedCandidate::score).map(RankedCandidate::candidate),
                score = best.score
            )
        }.sortedByDescending(UnifiedTrack::score)
    }

    private fun normalizedBaseTitle(title: String): String {
        val normalized = TrackMatcher.normalize(title)
        val withoutDecorations = BASE_DECORATIONS.fold(normalized) { value, marker ->
            value.replace(Regex("\\b${Regex.escape(marker)}\\b"), " ")
        }
        return VARIANT_MARKERS.fold(withoutDecorations) { value, marker ->
            value.replace(Regex("\\b${Regex.escape(marker)}\\b"), " ")
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun variants(title: String): Set<String> {
        val normalized = TrackMatcher.normalize(title)
        return VARIANT_MARKERS.filter(normalized::contains).toSet()
    }

    companion object {
        private val BASE_DECORATIONS = listOf(
            "official music video", "official video", "official audio", "music video", "audio"
        )
        private val VARIANT_MARKERS = setOf(
            "live", "концерт", "cover", "кавер", "remix", "ремикс", "slowed",
            "reverb", "karaoke", "караоке", "instrumental", "инструментал",
            "acoustic", "акустика", "extended"
        )
    }
}
