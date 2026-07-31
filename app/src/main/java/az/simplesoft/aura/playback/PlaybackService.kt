package az.simplesoft.aura.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AuraMusic/0.2 (Android)")
            .setAllowCrossProtocolRedirects(true)
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val dataSourceFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            dataSpec.withAdditionalHeaders(PlaybackSourceRegistry.headersFor(dataSpec.uri.toString()))
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || player.playbackState == Player.STATE_IDLE || !player.playWhenReady) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
