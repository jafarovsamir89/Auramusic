package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.TrackCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRankerV2Test {
    private val ranker = CandidateRankerV2()

    @Test
    fun officialOriginalRanksAboveUnrequestedVariants() {
        val request = MusicSearchRequest(rawQuery = "Linkin Park Numb", artist = "Linkin Park", title = "Numb")
        val candidates = listOf(
            candidate("live", "Numb Live in Texas"),
            candidate("cover", "Numb Cover"),
            candidate("official", "Numb", official = true)
        )

        val ranked = ranker.rank(request, candidates)

        assertEquals("official", ranked.first().candidate.id)
        assertTrue(ranked.first().score > ranked.last().score)
        assertTrue(ranked.first().reasons.contains("official"))
    }

    @Test
    fun requestedRemixIsNotPenalizedAsUnwanted() {
        val request = MusicSearchRequest(rawQuery = "Numb remix", title = "Numb remix")
        val result = ranker.score(request, candidate("remix", "Numb Remix"))

        assertTrue("remix" !in result.penalties)
    }

    @Test
    fun durationNearExpectedBeatsLargeMismatch() {
        val request = MusicSearchRequest(rawQuery = "Numb", title = "Numb")
        val near = candidate("near", "Numb", durationMs = 187_000)
        val far = candidate("far", "Numb", durationMs = 260_000)
        val context = RankingContext(expectedDurationMs = 185_000)

        val ranked = ranker.rank(request, listOf(far, near), context)

        assertEquals("near", ranked.first().candidate.id)
    }

    @Test
    fun semanticLullabyBeatsChillVariant() {
        val request = MusicSearchRequest(
            rawQuery = "колыбельная для ребёнка",
            semanticTags = setOf("lullaby", "bedtime", "nursery"),
            excludedTerms = setOf("chill", "lofi")
        )
        val ranked = ranker.rank(request, listOf(
            candidate("chill", "Chill Lofi Mix"),
            candidate("lullaby", "Lullaby Nursery Bedtime Song")
        ))

        assertEquals("lullaby", ranked.first().candidate.id)
        assertTrue(ranked.first().reasons.any { it.startsWith("semantic-match") })
        assertTrue(ranked.last().penalties.any { it.startsWith("semantic-excluded") })
    }

    private fun candidate(
        id: String,
        title: String,
        official: Boolean = false,
        durationMs: Long = 185_000
    ) = TrackCandidate(
        providerId = "youtube",
        id = id,
        title = title,
        artist = "Linkin Park",
        detailUrl = "https://example.test/$id",
        artworkUrl = "https://example.test/$id.jpg",
        durationMs = durationMs,
        bitrateKbps = 160,
        isOfficial = official
    )
}
