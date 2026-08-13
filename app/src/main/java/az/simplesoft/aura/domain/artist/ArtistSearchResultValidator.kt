package az.simplesoft.aura.domain.artist

import az.simplesoft.aura.data.providers.TrackCandidate

data class ArtistValidationResult(
    val accepted: List<TrackCandidate>,
    val rejected: List<TrackCandidate>,
    val scores: Map<String, Float>
)

/** Guardrail for provider metadata: an artist-specific request may never play an unrelated result. */
class ArtistSearchResultValidator {
    fun validate(requestedArtist: String, candidates: List<TrackCandidate>): ArtistValidationResult {
        val requested = ArtistNameNormalizer.folded(requestedArtist)
        if (requested.isBlank()) return ArtistValidationResult(emptyList(), candidates, emptyMap())
        val accepted = mutableListOf<TrackCandidate>()
        val rejected = mutableListOf<TrackCandidate>()
        val scores = linkedMapOf<String, Float>()
        candidates.forEach { candidate ->
            val score = artistSimilarity(requested, ArtistNameNormalizer.folded(candidate.artist))
            scores["${candidate.providerId}:${candidate.id}"] = score
            if (score >= ACCEPT_THRESHOLD) accepted += candidate else rejected += candidate
        }
        return ArtistValidationResult(accepted, rejected, scores)
    }

    private fun artistSimilarity(requested: String, actual: String): Float {
        if (requested == actual) return 1f
        if (actual.contains(requested) || requested.contains(actual) && actual.length >= 5) return 0.94f
        val left = requested.split(' ').toSet()
        val right = actual.split(' ').toSet()
        val overlap = if (left.isEmpty() || right.isEmpty()) 0f else left.intersect(right).size.toFloat() / maxOf(left.size, right.size)
        return overlap * 0.7f + editSimilarity(requested, actual) * 0.3f
    }

    private fun editSimilarity(a: String, b: String): Float {
        val previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val above = previous[j + 1]
                previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1, diagonal + if (a[i] == b[j]) 0 else 1)
                diagonal = above
            }
        }
        return (1f - previous[b.length].toFloat() / maxOf(a.length, b.length, 1)).coerceIn(0f, 1f)
    }

    companion object { private const val ACCEPT_THRESHOLD = 0.82f }
}
