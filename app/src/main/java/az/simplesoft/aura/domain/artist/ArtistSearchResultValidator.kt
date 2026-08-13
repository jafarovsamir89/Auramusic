package az.simplesoft.aura.domain.artist

import az.simplesoft.aura.data.providers.TrackCandidate

data class ArtistValidationResult(
    val accepted: List<TrackCandidate>,
    val rejected: List<TrackCandidate>,
    val scores: Map<String, Float>
)

/** Guardrail for provider metadata: an artist-specific request may never play an unrelated result. */
class ArtistSearchResultValidator {
    fun validate(
        requestedArtist: String,
        candidates: List<TrackCandidate>,
        allowUnattributed: Boolean = false
    ): ArtistValidationResult {
        val requested = ArtistNameNormalizer.folded(requestedArtist)
        if (requested.isBlank()) return ArtistValidationResult(emptyList(), candidates, emptyMap())
        val requestedForms = (ArtistQueryVariants.forSearch(requestedArtist) + requested)
            .map(ArtistNameNormalizer::folded)
            .filter(String::isNotBlank)
            .distinct()
        val accepted = mutableListOf<TrackCandidate>()
        val rejected = mutableListOf<TrackCandidate>()
        val scores = linkedMapOf<String, Float>()
        candidates.forEach { candidate ->
            val declaredArtist = ArtistNameNormalizer.folded(candidate.artist)
            val channelArtist = ArtistNameNormalizer.folded(candidate.channel.orEmpty())
            val declaredScore = requestedForms.maxOf { artistSimilarity(it, declaredArtist) }
            val channelScore = requestedForms.maxOf { artistSimilarity(it, channelArtist) }
            val titleScore = requestedForms.maxOf { titleEvidence(it, candidate.title) }
            val hasConflict = listOf(declaredArtist, channelArtist)
                .filter { it.isNotBlank() && !isGenericMetadata(it) }
                .any { requestedForms.maxOf { form -> artistSimilarity(form, it) } < CONFLICT_THRESHOLD }
            val unattributedEvidence = allowUnattributed &&
                listOf(declaredArtist, channelArtist).all { it.isBlank() || isGenericMetadata(it) } &&
                titleScore >= TITLE_EVIDENCE_THRESHOLD
            val score = maxOf(declaredScore, channelScore, titleScore)
            scores["${candidate.providerId}:${candidate.id}"] = score
            if ((!hasConflict && score >= ACCEPT_THRESHOLD) || unattributedEvidence) accepted += candidate else rejected += candidate
        }
        return ArtistValidationResult(accepted, rejected, scores)
    }

    private fun titleEvidence(requested: String, title: String): Float {
        val actual = ArtistNameNormalizer.folded(title)
        return if (actual.contains(requested)) TITLE_EVIDENCE_THRESHOLD else artistSimilarity(requested, actual) * .75f
    }

    private fun isGenericMetadata(value: String): Boolean = value in GENERIC_METADATA

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

    companion object {
        private const val ACCEPT_THRESHOLD = 0.82f
        private const val CONFLICT_THRESHOLD = 0.45f
        private const val TITLE_EVIDENCE_THRESHOLD = 0.92f
        private val GENERIC_METADATA = setOf("youtube", "youtube music", "music", "official audio", "official artist channel")
    }
}
