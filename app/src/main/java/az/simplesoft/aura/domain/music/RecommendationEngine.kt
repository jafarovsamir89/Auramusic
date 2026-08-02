package az.simplesoft.aura.domain.music

import az.simplesoft.aura.assistant.Mood
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.search.CandidateRankerV2
import az.simplesoft.aura.data.search.TrackIdentityResolver

data class RecommendationContext(
    val queue: List<Track>,
    val currentIndex: Int,
    val recentTracks: List<Track>,
    val likedTrackIds: Set<String>,
    val skippedTrackIds: Set<String> = emptySet(),
    val hourOfDay: Int,
    val carMode: Boolean = false
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val likedTracks: List<Track> get() = recentTracks.filter { it.id in likedTrackIds }
}

interface RecommendationEngine {
    suspend fun myMix(context: RecommendationContext, limit: Int = 16): List<Track>
    suspend fun continueListening(context: RecommendationContext, limit: Int = 16): List<Track>
    suspend fun similarTo(track: Track, context: RecommendationContext, limit: Int = 16): List<Track>
    suspend fun buildMoodQueue(mood: Mood, context: RecommendationContext, limit: Int = 16): List<Track>
    suspend fun extendQueue(context: RecommendationContext, limit: Int = 8): List<Track>
}

object EmptyRecommendationEngine : RecommendationEngine {
    override suspend fun myMix(context: RecommendationContext, limit: Int) = emptyList<Track>()
    override suspend fun continueListening(context: RecommendationContext, limit: Int) = emptyList<Track>()
    override suspend fun similarTo(track: Track, context: RecommendationContext, limit: Int) = emptyList<Track>()
    override suspend fun buildMoodQueue(mood: Mood, context: RecommendationContext, limit: Int) = emptyList<Track>()
    override suspend fun extendQueue(context: RecommendationContext, limit: Int) = emptyList<Track>()
}

class PersonalRecommendationEngine(
    private val providerManager: ProviderManager,
    private val candidateRanker: CandidateRankerV2,
    private val identityResolver: TrackIdentityResolver,
    private val candidateResolver: (suspend (TrackCandidate) -> Track?)? = null
) : RecommendationEngine {

    override suspend fun myMix(context: RecommendationContext, limit: Int): List<Track> {
        val seeds = (context.likedTracks + context.recentTracks + listOfNotNull(context.currentTrack))
            .filter { it.isRecommendationSeed() }
            .distinctBy(Track::id)
            .take(MAX_SEEDS)
        val candidates = seeds.flatMap { related(it) }.ifEmpty {
            search(fallbackQuery(context), limit * 2)
        }
        return resolvePersonalized(candidates, context, limit)
    }

    override suspend fun continueListening(context: RecommendationContext, limit: Int): List<Track> {
        val remaining = context.queue
            .drop((context.currentIndex + 1).coerceAtLeast(0))
            .filter { it.isPlayable && !it.streamUrl.isNullOrBlank() }
        if (remaining.size >= limit) return remaining.take(limit)
        val additions = myMix(context, limit)
        return (remaining + additions)
            .distinctBy(Track::id)
            .take(limit)
    }

    override suspend fun similarTo(
        track: Track,
        context: RecommendationContext,
        limit: Int
    ): List<Track> = resolvePersonalized(related(track), context, limit)

    override suspend fun buildMoodQueue(
        mood: Mood,
        context: RecommendationContext,
        limit: Int
    ): List<Track> {
        val query = when (mood) {
            Mood.CALM -> "спокойная музыка chill mix"
            Mood.DRIVE -> "музыка в дорогу driving mix"
            Mood.FOCUS -> "музыка для концентрации focus mix"
            Mood.ENERGY -> "энергичная музыка workout mix"
            Mood.NIGHT -> "ночная музыка night drive mix"
            Mood.SAD -> "грустная музыка sad mix"
            Mood.HAPPY -> "весёлая музыка happy mix"
        }
        return resolvePersonalized(search(query, limit * 2), context, limit)
    }

    override suspend fun extendQueue(context: RecommendationContext, limit: Int): List<Track> {
        val seed = context.queue.lastOrNull { it.isRecommendationSeed() }
            ?: context.currentTrack
            ?: return myMix(context, limit)
        return similarTo(seed, context, limit)
            .filterNot { candidate -> context.queue.any { it.id == candidate.id } }
            .take(limit)
    }

    private suspend fun related(track: Track): List<TrackCandidate> =
        (providerManager.related(track) as? PluginResult.Success)?.value.orEmpty()

    private suspend fun search(query: String, limit: Int): List<TrackCandidate> =
        (providerManager.search(
            MusicSearchRequest(
                rawQuery = query,
                preferredProviderId = ONLINE_PROVIDER,
                limit = limit.coerceIn(1, 30),
                autoPlay = false
            )
        ) as? PluginResult.Success)?.value.orEmpty()

    private suspend fun resolvePersonalized(
        candidates: List<TrackCandidate>,
        context: RecommendationContext,
        limit: Int
    ): List<Track> {
        val excluded = context.skippedTrackIds + context.queue.map(Track::id)
        val ranked = candidateRanker.rank(
            MusicSearchRequest(rawQuery = context.currentTrack?.artist.orEmpty(), autoPlay = false),
            candidates
        ).asSequence()
            .map { it.candidate }
            .filter { it.providerId == ONLINE_PROVIDER }
            .filterNot { "$ONLINE_PROVIDER:${it.id}" in excluded }
            .distinctBy { "${it.providerId}:${it.id}" }
            .sortedByDescending { personalizedScore(it, context) }
            .toList()

        val deduplicated = ranked.fold(mutableListOf<TrackCandidate>()) { accepted, candidate ->
            if (accepted.none { identityResolver.areSame(it, candidate) }) accepted += candidate
            accepted
        }
        val selected = diversify(deduplicated, limit)
        return buildList {
            for (candidate in selected) {
                val track = candidateResolver?.invoke(candidate) ?: run {
                    val resolved = providerManager.resolve(candidate) as? PluginResult.Success
                    resolved?.value?.track
                }
                if (track == null) continue
                add(track)
                if (size >= limit) break
            }
        }
    }

    private fun personalizedScore(candidate: TrackCandidate, context: RecommendationContext): Double {
        val artist = candidate.artist.normalizedArtist()
        val likedArtists = context.likedTracks.map { it.artist.normalizedArtist() }
        val recentArtists = context.recentTracks.map { it.artist.normalizedArtist() }
        val currentArtist = context.currentTrack?.artist?.normalizedArtist()
        return candidate.confidence * 10.0 +
            likedArtists.count { it == artist } * 8.0 +
            recentArtists.count { it == artist } * 2.5 +
            (if (artist == currentArtist) 5.0 else 0.0) +
            (if (candidate.isOfficial) 1.5 else 0.0) +
            ((candidate.popularity ?: 0L).coerceAtMost(10_000_000L) / 10_000_000.0)
    }

    private fun diversify(candidates: List<TrackCandidate>, limit: Int): List<TrackCandidate> {
        val perArtist = mutableMapOf<String, Int>()
        return candidates.filter { candidate ->
            val artist = candidate.artist.normalizedArtist()
            val count = perArtist[artist] ?: 0
            if (count >= MAX_PER_ARTIST) false else {
                perArtist[artist] = count + 1
                true
            }
        }.take(limit)
    }

    private fun fallbackQuery(context: RecommendationContext): String = when {
        context.carMode -> "музыка в дорогу популярные песни"
        context.hourOfDay in 5..10 -> "музыка для доброго утра"
        context.hourOfDay in 22..23 || context.hourOfDay in 0..4 -> "спокойная ночная музыка"
        else -> "популярная музыка mix"
    }

    private fun Track.isRecommendationSeed(): Boolean =
        isPlayable && id != "aura-placeholder" && sourceId == ONLINE_PROVIDER

    private fun String.normalizedArtist(): String = lowercase().trim().replace(Regex("\\s+"), " ")

    companion object {
        private const val ONLINE_PROVIDER = "youtube"
        private const val MAX_SEEDS = 3
        private const val MAX_PER_ARTIST = 2
    }
}
