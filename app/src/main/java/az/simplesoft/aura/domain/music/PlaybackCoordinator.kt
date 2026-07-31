package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.Track

interface PlaybackCoordinator {
    suspend fun play(queue: List<Track>, selected: Track, startPositionMs: Long = 0L)

    suspend fun replaceCurrent(track: Track, startPositionMs: Long, fadeDurationMs: Long = 250L)

    fun currentPositionMs(): Long

    fun currentQueue(): List<Track>
}
