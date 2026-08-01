package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.TrackCandidate

/** Keeps provider-local candidates behind one stable queue/media identity for the current search. */
class UnifiedTrackSession {
    private val canonicalIdByCandidate = mutableMapOf<String, String>()
    private val alternativesByCanonicalId = mutableMapOf<String, List<TrackCandidate>>()

    fun replace(tracks: List<UnifiedTrack>): List<TrackCandidate> {
        clear()
        return tracks.mapNotNull { unified ->
            val primary = unified.alternatives.firstOrNull() ?: return@mapNotNull null
            val canonicalId = unified.metadata.id
            alternativesByCanonicalId[canonicalId] = unified.alternatives
            unified.alternatives.forEach { candidate ->
                canonicalIdByCandidate[candidate.key] = canonicalId
            }
            primary
        }
    }

    fun clear() {
        canonicalIdByCandidate.clear()
        alternativesByCanonicalId.clear()
    }

    fun canonicalId(candidate: TrackCandidate): String =
        canonicalIdByCandidate[candidate.key] ?: "${candidate.providerId}:${candidate.id}"

    fun canonicalize(candidate: TrackCandidate, resolved: Track): Track =
        resolved.copy(id = canonicalId(candidate))

    fun fallbackOrder(
        selected: TrackCandidate,
        primaryCandidates: List<TrackCandidate>,
        limit: Int
    ): List<TrackCandidate> {
        val canonicalId = canonicalId(selected)
        val sameTrack = alternativesByCanonicalId[canonicalId].orEmpty()
        return (listOf(selected) + sameTrack + primaryCandidates)
            .distinctBy { it.key }
            .take(limit.coerceAtLeast(1))
    }

    private val TrackCandidate.key: String get() = "$providerId:$id"
}
