package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.TrackCandidate
import kotlin.math.abs

data class RankingContext(
    val expectedDurationMs: Long? = null,
    val providerReliability: Map<String, Double> = emptyMap(),
    val preferredProviderIds: Set<String> = emptySet(),
    val playbackHistoryBoost: Map<String, Double> = emptyMap()
)

data class RankedCandidate(
    val candidate: TrackCandidate,
    val score: Double,
    val reasons: List<String>,
    val penalties: List<String>
)

class CandidateRankerV2 {
    fun rank(
        request: MusicSearchRequest,
        candidates: List<TrackCandidate>,
        context: RankingContext = RankingContext()
    ): List<RankedCandidate> = candidates.map { score(request, it, context) }
        .sortedWith(compareByDescending<RankedCandidate>(RankedCandidate::score).thenBy { it.candidate.id })

    fun score(
        request: MusicSearchRequest,
        candidate: TrackCandidate,
        context: RankingContext = RankingContext()
    ): RankedCandidate {
        val requestedTitle = request.title ?: request.rawQuery
        val requestedArtist = request.artist.orEmpty()
        val titleSimilarity = TrackMatcher.similarity(requestedTitle, candidate.title)
        val artistSimilarity = if (requestedArtist.isBlank()) {
            artistInsideQuery(request.rawQuery, candidate.artist)
        } else {
            TrackMatcher.similarity(requestedArtist, candidate.artist)
        }
        val durationSimilarity = durationSimilarity(context.expectedDurationMs, candidate.durationMs)
        val sourceQuality = ((candidate.bitrateKbps ?: 128).toDouble() / 256.0).coerceIn(.35, 1.0)
        val reliability = context.providerReliability[candidate.providerId]?.coerceIn(0.0, 1.0) ?: .75
        val normalizedQuery = TrackMatcher.normalize(request.rawQuery)
        val normalizedCandidate = TrackMatcher.normalize("${candidate.artist} ${candidate.title}")
        val exactMusicMatch = if (
            normalizedQuery == normalizedCandidate ||
            normalizedQuery == TrackMatcher.normalize("${candidate.title} ${candidate.artist}")
        ) 1.0 else .0
        val artwork = if (candidate.artworkUrl.isNullOrBlank()) .0 else 1.0
        val preference = if (candidate.providerId in context.preferredProviderIds) 1.0 else .0
        val history = context.playbackHistoryBoost["${candidate.providerId}:${candidate.id}"]
            ?.coerceIn(0.0, 1.0) ?: .0
        val official = if (candidate.isOfficial) .025 else .0

        val reasons = buildList {
            if (titleSimilarity >= .9) add("title-exact")
            if (artistSimilarity >= .9) add("artist-exact")
            if (exactMusicMatch == 1.0) add("full-query-exact")
            if (candidate.isOfficial) add("official")
            if (artwork == 1.0) add("artwork")
        }
        val penalties = variantPenalties(request, candidate)
        val penaltyScore = penalties.sumOf(::penaltyFor).coerceAtMost(.40)
        val score = (
            titleSimilarity * .32 +
                artistSimilarity * .26 +
                durationSimilarity * .10 +
                sourceQuality * .09 +
                reliability * .08 +
                exactMusicMatch * .08 +
                artwork * .02 +
                preference * .03 +
                history * .02 +
                official -
                penaltyScore
            ).coerceIn(0.0, 1.0)

        return RankedCandidate(candidate.copy(confidence = score), score, reasons, penalties)
    }

    private fun artistInsideQuery(query: String, artist: String): Double {
        val normalizedQuery = TrackMatcher.normalize(query)
        val normalizedArtist = TrackMatcher.normalize(artist)
        return if (normalizedArtist.isNotBlank() && normalizedQuery.contains(normalizedArtist)) 1.0
        else TrackMatcher.similarity(query, artist)
    }

    private fun durationSimilarity(expected: Long?, actual: Long?): Double {
        if (expected == null || actual == null || expected <= 0L || actual <= 0L) return .5
        val difference = abs(expected - actual).toDouble()
        return (1.0 - difference / 30_000.0).coerceIn(0.0, 1.0)
    }

    private fun variantPenalties(request: MusicSearchRequest, candidate: TrackCandidate): List<String> {
        val requestText = TrackMatcher.normalize(request.rawQuery)
        val candidateText = TrackMatcher.normalize("${candidate.title} ${candidate.channel.orEmpty()}")
        return VARIANTS.mapNotNull { (label, markers) ->
            val candidateHas = markers.any(candidateText::contains)
            val requested = markers.any(requestText::contains)
            label.takeIf { candidateHas && !requested }
        }
    }

    private fun penaltyFor(label: String): Double = when (label) {
        "short", "teaser", "interview", "reaction" -> .18
        "cover", "karaoke", "instrumental" -> .16
        "live", "remix", "slowed", "reverb", "lyrics-video" -> .12
        else -> .10
    }

    companion object {
        private val VARIANTS = listOf(
            "live" to listOf("live", "концерт", "живое исполнение"),
            "cover" to listOf("cover", "кавер"),
            "remix" to listOf("remix", "ремикс"),
            "slowed" to listOf("slowed", "замедленная"),
            "reverb" to listOf("reverb"),
            "karaoke" to listOf("karaoke", "караоке"),
            "instrumental" to listOf("instrumental", "инструментал"),
            "reaction" to listOf("reaction", "реакция"),
            "lyrics-video" to listOf("lyrics video", "lyric video", "текст песни"),
            "short" to listOf("shorts", "short"),
            "teaser" to listOf("teaser", "тизер"),
            "interview" to listOf("interview", "интервью")
        )
    }
}
