package az.simplesoft.aura.playback

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.domain.music.PlaybackCoordinator

class PlaybackConnectionCoordinator(
    private val connection: PlaybackConnection,
    private val queueProvider: () -> List<Track>
) : PlaybackCoordinator {
    override suspend fun play(queue: List<Track>, selected: Track, startPositionMs: Long) {
        connection.play(queue, selected, startPositionMs)
    }

    override suspend fun replaceCurrent(track: Track, startPositionMs: Long, fadeDurationMs: Long) {
        connection.replaceCurrent(track, startPositionMs)
    }

    override fun currentPositionMs(): Long = connection.currentPositionMs()

    override fun currentQueue(): List<Track> = queueProvider()
}
