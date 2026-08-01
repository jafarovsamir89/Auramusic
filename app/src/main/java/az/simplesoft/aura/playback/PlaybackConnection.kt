package az.simplesoft.aura.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.youtube.YouTubePlaybackIdentity
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackConnection(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onPlaybackChanged(isPlaying: Boolean, isBuffering: Boolean)
        fun onTrackChanged(trackId: String)
        fun onPlaybackError(trackId: String?, message: String)
    }

    private val appContext = context.applicationContext
    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var pendingPlayback: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            listener.onPlaybackChanged(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(listener::onTrackChanged)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            listener.onPlaybackError(controller?.currentMediaItem?.mediaId, "Поток песни недоступен. Ищу свежий источник.")
        }
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess {
                    controller = it
                    it.addListener(playerListener)
                    listener.onPlaybackChanged(
                        isPlaying = it.isPlaying,
                        isBuffering = it.playbackState == Player.STATE_BUFFERING
                    )
                    pendingPlayback?.invoke()
                    pendingPlayback = null
                }.onFailure {
                    listener.onPlaybackError(null, "Не удалось подключить системный плеер.")
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun play(queue: List<Track>, selected: Track, startPositionMs: Long = 0L) = withController {
        val playable = queue.filter { !it.streamUrl.isNullOrBlank() }
        val index = playable.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        playable.filterNot { it.sourceId == "youtube" }
            .forEach { track -> PlaybackSourceRegistry.register(track.streamUrl.orEmpty(), track.requestHeaders) }
        it.setMediaItems(playable.map(::toMediaItem), index, startPositionMs.coerceAtLeast(0L))
        it.prepare()
        it.play()
    }

    fun replaceCurrent(track: Track, startPositionMs: Long) = withController { player ->
        val uri = track.streamUrl ?: return@withController
        if (track.sourceId != "youtube") PlaybackSourceRegistry.register(uri, track.requestHeaders)
        val index = player.currentMediaItemIndex.takeIf { it in 0 until player.mediaItemCount }
        if (index == null) {
            player.setMediaItem(toMediaItem(track), startPositionMs.coerceAtLeast(0L))
        } else {
            player.replaceMediaItem(index, toMediaItem(track))
            player.seekTo(index, startPositionMs.coerceAtLeast(0L))
        }
        player.prepare()
        player.play()
    }

    fun append(track: Track) {
        val uri = track.streamUrl ?: return
        if (track.sourceId != "youtube") PlaybackSourceRegistry.register(uri, track.requestHeaders)
        withController { it.addMediaItem(toMediaItem(track)) }
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun play() = withController(MediaController::play)
    fun pause() = withController(MediaController::pause)
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekToDefaultPosition(0) }
    fun previous() = withController { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekToDefaultPosition() }
    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }
    fun setRepeat(enabled: Boolean) = withController { it.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    fun durationMs(): Long = controller?.duration?.takeIf { it > 0L } ?: 0L

    fun release() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { pendingPlayback = { controller?.let(action) } }
    }

    private fun toMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply { track.artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.streamUrl)
            .apply {
                YouTubePlaybackIdentity.videoId(track.id)?.let { videoId ->
                    setCustomCacheKey(YouTubePlaybackIdentity.cacheKey(videoId))
                }
            }
            .setMediaMetadata(metadata)
            .build()
    }
}
