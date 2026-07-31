package az.simplesoft.aura.domain.music

import az.simplesoft.aura.assistant.Mood
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.search.CandidateRankerV2
import az.simplesoft.aura.data.search.RankingContext
import az.simplesoft.aura.data.search.TrackIdentityResolver
import az.simplesoft.aura.data.search.UnifiedTrack
import kotlin.math.abs

class MusicBrain(
    private val providerManager: ProviderManager,
    private val candidateRanker: CandidateRankerV2,
    private val identityResolver: TrackIdentityResolver,
    private val recommendationEngine: RecommendationEngine,
    private val playbackCoordinator: PlaybackCoordinator
) {
    private data class ResolvedAlternative(
        val candidate: TrackCandidate,
        val source: PlayableSource
    )

    private val alternativesByTrackId = mutableMapOf<String, List<TrackCandidate>>()

    suspend fun search(request: MusicSearchRequest): SearchOutcome =
        when (val result = providerManager.search(request)) {
            is PluginResult.Success -> {
                val reliability = providerManager.stats().mapValues { (_, value) -> value.successRate }
                val ranked = candidateRanker.rank(
                    request,
                    result.value,
                    RankingContext(providerReliability = reliability)
                )
                val unified = identityResolver.unify(ranked)
                SearchOutcome.Success(unified, result.diagnostics)
            }
            is PluginResult.Failure -> SearchOutcome.Failure(result.message)
        }

    suspend fun searchAndPlay(request: MusicSearchRequest): PlaybackOutcome {
        val search = search(request)
        if (search !is SearchOutcome.Success || search.tracks.isEmpty()) {
            return PlaybackOutcome.Failure((search as? SearchOutcome.Failure)?.message ?: "Track not found")
        }
        return playUnified(search.tracks.first(), 0L, replacing = false)
    }

    suspend fun playSimilar(track: Track): PlaybackOutcome {
        val related = providerManager.related(track)
        if (related !is PluginResult.Success || related.value.isEmpty()) {
            return searchAndPlay(
                MusicSearchRequest(rawQuery = track.artist, artist = track.artist, autoPlay = true)
            )
        }
        val request = MusicSearchRequest(rawQuery = track.artist, artist = track.artist, autoPlay = true)
        val unified = identityResolver.unify(candidateRanker.rank(request, related.value))
        return unified.firstOrNull()?.let { playUnified(it, 0L, replacing = false) }
            ?: PlaybackOutcome.Failure("No related tracks")
    }

    suspend fun continueListening(): PlaybackOutcome {
        val queue = recommendationEngine.continueListening()
        val first = queue.firstOrNull() ?: return PlaybackOutcome.Failure("No listening history")
        playbackCoordinator.play(queue, first)
        return PlaybackOutcome.Started(first)
    }

    suspend fun buildMoodQueue(mood: Mood): List<Track> = recommendationEngine.buildMoodQueue(mood)

    suspend fun recoverPlayback(failedTrack: Track): PlaybackOutcome {
        val position = playbackCoordinator.currentPositionMs()
        val alternatives = alternativesByTrackId[failedTrack.id].orEmpty()
        val compatible = alternatives.filter { candidate ->
            durationCompatible(failedTrack.durationMs, candidate.durationMs) &&
                identityResolver.identityFor(candidate).variants.isEmpty()
        }
        val resolved = resolveFirst(compatible)
        if (resolved != null) {
            playbackCoordinator.replaceCurrent(resolved.source.track, position)
            alternativesByTrackId[resolved.source.track.id] = alternatives.filterNot {
                it.providerId == resolved.candidate.providerId && it.id == resolved.candidate.id
            }
            return PlaybackOutcome.Started(resolved.source.track, position, compatible.size - 1)
        }

        val request = MusicSearchRequest(
            rawQuery = "${failedTrack.artist} ${failedTrack.title}",
            artist = failedTrack.artist,
            title = failedTrack.title,
            autoPlay = true
        )
        val fresh = search(request)
        val replacement = (fresh as? SearchOutcome.Success)?.tracks?.firstOrNull()
            ?: return PlaybackOutcome.Failure("No alternative source")
        val sameRecording = replacement.alternatives.firstOrNull()?.let { candidate ->
            identityResolver.areSame(failedTrack.toCandidate(), candidate) &&
                durationCompatible(failedTrack.durationMs, candidate.durationMs)
        } == true
        return playUnified(replacement, if (sameRecording) position else 0L, replacing = true)
    }

    private suspend fun playUnified(
        unified: UnifiedTrack,
        positionMs: Long,
        replacing: Boolean
    ): PlaybackOutcome {
        val resolved = resolveFirst(unified.alternatives)
            ?: return PlaybackOutcome.Failure("No playable source")
        val source = resolved.source
        alternativesByTrackId[source.track.id] = unified.alternatives.filterNot {
            it.providerId == resolved.candidate.providerId && it.id == resolved.candidate.id
        }
        if (replacing) playbackCoordinator.replaceCurrent(source.track, positionMs)
        else playbackCoordinator.play(listOf(source.track), source.track, positionMs)
        return PlaybackOutcome.Started(
            source.track,
            resumedAtMs = positionMs,
            alternativesAvailable = (unified.alternatives.size - 1).coerceAtLeast(0)
        )
    }

    private suspend fun resolveFirst(candidates: List<TrackCandidate>): ResolvedAlternative? {
        candidates.forEach { candidate ->
            when (val resolved = providerManager.resolve(candidate)) {
                is PluginResult.Success -> return ResolvedAlternative(candidate, resolved.value)
                is PluginResult.Failure -> Unit
            }
        }
        return null
    }

    private fun durationCompatible(left: Long?, right: Long?): Boolean =
        left == null || right == null || abs(left - right) <= 10_000L

    private fun Track.toCandidate(): TrackCandidate = TrackCandidate(
        providerId = sourceId,
        id = id.substringAfter(':', id),
        title = title,
        artist = artist,
        detailUrl = sourcePageUrl,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        popularity = popularity?.toLong(),
        year = year
    )
}
