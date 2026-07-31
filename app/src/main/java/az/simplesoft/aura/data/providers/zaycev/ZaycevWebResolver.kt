package az.simplesoft.aura.data.providers.zaycev

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.TrackCandidate
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Последний, скрытый fallback. Он воспроизводит только обычное публичное действие
 * «Слушать» и наблюдает уже выданный сайтом media request. CAPTCHA, вход и реклама
 * здесь не автоматизируются.
 */
class ZaycevWebResolver(private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(candidate: TrackCandidate): ProviderResult<PlayableSource> =
        suspendCancellableCoroutine { continuation ->
            val main = Handler(Looper.getMainLooper())
            main.post {
                val completed = AtomicBoolean(false)
                val webView = WebView(context.applicationContext)
                fun finish(result: ProviderResult<PlayableSource>) {
                    if (completed.compareAndSet(false, true)) {
                        webView.stopLoading()
                        webView.destroy()
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
                val timeout = Runnable {
                    finish(ProviderResult.Failure(ProviderFailureReason.TIMEOUT, "Скрытый веб-резолвер не получил поток"))
                }
                continuation.invokeOnCancellation { main.post { webView.destroy() } }
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.settings.userAgentString = ZaycevSelectors.USER_AGENT
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url.startsWith(ZaycevSelectors.BASE_URL)) {
                            view.evaluateJavascript(
                                "document.querySelector('[data-qa^=\\\"listen-\\\"]')?.click()",
                                null
                            )
                        }
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url.toString()
                        if (url.startsWith("https://dl.zaycev.net/track_auth/")) {
                            val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                            val headers = buildMap {
                                put("User-Agent", ZaycevSelectors.USER_AGENT)
                                put("Referer", candidate.detailUrl)
                                cookie.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
                            }
                            val track = Track(
                                id = "zaycev:${candidate.id}",
                                title = candidate.title,
                                artist = candidate.artist,
                                artworkUrl = candidate.artworkUrl,
                                durationMs = candidate.durationMs,
                                sourceId = ZaycevProvider.ID,
                                sourcePageUrl = candidate.detailUrl,
                                playbackType = PlaybackType.DIRECT_STREAM,
                                streamUrl = url,
                                requestHeaders = headers
                            )
                            main.post {
                                finish(
                                    ProviderResult.Success(
                                        PlayableSource(
                                            providerId = ZaycevProvider.ID,
                                            track = track,
                                            playbackUri = request.url,
                                            requestHeaders = headers,
                                            cookies = headers["Cookie"],
                                            mimeType = "audio/mpeg",
                                            expiresAt = System.currentTimeMillis() + 5 * 60_000L,
                                            sourcePageUrl = candidate.detailUrl
                                        ),
                                        ZaycevDiagnostics("hidden-webview").asMap()
                                    )
                                )
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                main.postDelayed(timeout, 10_000L)
                webView.loadUrl(candidate.detailUrl)
            }
        }
}
