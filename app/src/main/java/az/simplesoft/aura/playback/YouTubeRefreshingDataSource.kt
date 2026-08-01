package az.simplesoft.aura.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import az.simplesoft.aura.data.plugins.youtube.AuraYouTubePlaybackResolver
import az.simplesoft.aura.data.plugins.youtube.YouTubePlaybackException
import az.simplesoft.aura.data.plugins.youtube.YouTubePlaybackIdentity
import az.simplesoft.aura.data.plugins.youtube.YouTubeRetryPolicy
import java.io.IOException
import kotlin.math.min

@UnstableApi
internal class YouTubeRefreshingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val resolver: AuraYouTubePlaybackResolver,
    private val retryPolicy: YouTubeRetryPolicy = YouTubeRetryPolicy(),
    private val sleeper: (Long) -> Unit = Thread::sleep
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RefreshingDataSource(
        upstreamFactory,
        resolver,
        retryPolicy,
        sleeper
    )

    private class RefreshingDataSource(
        private val factory: DataSource.Factory,
        private val resolver: AuraYouTubePlaybackResolver,
        private val retryPolicy: YouTubeRetryPolicy,
        private val sleeper: (Long) -> Unit
    ) : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var delegate = newDelegate()

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
            delegate.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            var attempt = 1
            var backoffMs = retryPolicy.initialDelayMs
            var refreshedExpiredUrl = false
            while (true) {
                try {
                    return delegate.open(dataSpec)
                } catch (error: IOException) {
                    val response = error.httpResponseError() ?: throw error
                    val videoId = YouTubePlaybackIdentity.videoId(dataSpec.uri)

                    if (response.responseCode == 403 && videoId != null && !refreshedExpiredUrl) {
                        resetDelegate()
                        resolver.invalidate(videoId)
                        refreshedExpiredUrl = true
                        continue
                    }
                    if (response.responseCode == 403 && videoId != null) {
                        throw YouTubePlaybackException.ExpiredStream(videoId, error)
                    }

                    val retryable = response.responseCode == 429 || response.responseCode in 500..599
                    if (retryable && attempt < retryPolicy.maxAttempts) {
                        val retryAfterMs = response.headerFields.entries
                            .firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                            ?.value?.firstOrNull()?.toLongOrNull()?.times(1_000L)
                        resetDelegate()
                        sleepBeforeRetry((retryAfterMs ?: backoffMs).coerceAtMost(retryPolicy.maxDelayMs))
                        backoffMs = min(backoffMs.coerceAtLeast(1L) * 2L, retryPolicy.maxDelayMs)
                        attempt++
                        continue
                    }

                    when {
                        response.responseCode == 429 -> throw YouTubePlaybackException.RateLimited(cause = error)
                        response.responseCode in 500..599 -> throw YouTubePlaybackException.ServerFailure(
                            response.responseCode,
                            error
                        )
                        else -> throw error
                    }
                }
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun getUri(): Uri? = delegate.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

        override fun close() = delegate.close()

        private fun resetDelegate() {
            runCatching { delegate.close() }
            delegate = newDelegate()
        }

        private fun sleepBeforeRetry(delayMs: Long) {
            if (delayMs <= 0) return
            try {
                sleeper(delayMs)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw YouTubePlaybackException.Transport(IOException("YouTube retry interrupted", error))
            }
        }

        private fun newDelegate(): DataSource = factory.createDataSource().also { dataSource ->
            listeners.forEach(dataSource::addTransferListener)
        }

        private fun Throwable.httpResponseError(): HttpDataSource.InvalidResponseCodeException? {
            var current: Throwable? = this
            while (current != null) {
                if (current is HttpDataSource.InvalidResponseCodeException) return current
                current = current.cause
            }
            return null
        }
    }
}
