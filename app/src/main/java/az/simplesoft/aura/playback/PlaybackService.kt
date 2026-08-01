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
import az.simplesoft.aura.data.plugins.youtube.AuraYouTubePlaybackResolver
import az.simplesoft.aura.data.plugins.youtube.YouTubePlaybackIdentity
import java.io.File
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playbackCache: SimpleCache? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val httpClient = OkHttpClient()
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
        playbackCache?.release()
        playbackCache = null
        super.onDestroy()
    }

    companion object {
        private const val AURA_PLAYBACK_USER_AGENT = "AuraMusic/0.2 (Android)"
    }
}
