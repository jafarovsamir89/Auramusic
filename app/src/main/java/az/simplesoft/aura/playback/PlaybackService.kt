package az.simplesoft.aura.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import az.simplesoft.aura.data.plugins.youtube.AuraYouTubePlaybackResolver
import az.simplesoft.aura.data.plugins.youtube.YouTubePlaybackIdentity
import az.simplesoft.aura.data.providers.AuraHttpClient
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playbackCache: SimpleCache? = null
    private lateinit var equalizerProcessor: AuraEqualizerAudioProcessor

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val httpClient = AuraHttpClient.create()
        val youtubeResolver = AuraYouTubePlaybackResolver(httpClient)
        val httpFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent(AURA_PLAYBACK_USER_AGENT)
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val cache = SimpleCache(
            File(cacheDir, "media3-audio"),
            LeastRecentlyUsedCacheEvictor(128L * 1024 * 1024),
            StandaloneDatabaseProvider(this)
        ).also { playbackCache = it }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val resolvingFactory = ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            val videoId = YouTubePlaybackIdentity.videoId(dataSpec.uri)
            if (videoId == null) {
                dataSpec.withAdditionalHeaders(PlaybackSourceRegistry.headersFor(dataSpec.uri.toString()))
            } else {
                val stream = youtubeResolver.resolve(videoId)
                dataSpec.buildUpon()
                    .setUri(android.net.Uri.parse(stream.url))
                    .setKey(YouTubePlaybackIdentity.cacheKey(videoId, stream.itag))
                    .build()
                    .withAdditionalHeaders(mapOf("User-Agent" to stream.userAgent))
            }
        }
        val dataSourceFactory = YouTubeRefreshingDataSourceFactory(resolvingFactory, youtubeResolver)
        equalizerProcessor = AuraEqualizerAudioProcessor()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setRenderersFactory(AuraRenderersFactory(this, equalizerProcessor))
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
        val callback = object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controllerInfo: MediaSession.ControllerInfo): ConnectionResult {
                val commands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_COMMAND_SET_EQUALIZER, Bundle.EMPTY))
                    .build()
                return ConnectionResult.accept(commands, ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            }

            @android.annotation.SuppressLint("WrongConstant")
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ) = if (customCommand.customAction == CUSTOM_COMMAND_SET_EQUALIZER) {
                val bands = args.getFloatArray(EXTRA_EQUALIZER_BANDS)
                if (bands != null && bands.size == EQUALIZER_BAND_COUNT) {
                    equalizerProcessor.setBands(bands)
                } else {
                    equalizerProcessor.setPreset(args.getString(EXTRA_EQUALIZER_PRESET, "flat"))
                }
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else {
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }
        mediaSession = MediaSession.Builder(this, player).setCallback(callback).build()
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
        playbackCache?.release()
        playbackCache = null
        super.onDestroy()
    }

    companion object {
        const val CUSTOM_COMMAND_SET_EQUALIZER = "aura.set_equalizer"
        const val EXTRA_EQUALIZER_PRESET = "preset"
        const val EXTRA_EQUALIZER_BANDS = "bands"
        const val EQUALIZER_BAND_COUNT = 5
        private const val AURA_PLAYBACK_USER_AGENT = "AuraMusic/0.2 (Android)"
    }
}
