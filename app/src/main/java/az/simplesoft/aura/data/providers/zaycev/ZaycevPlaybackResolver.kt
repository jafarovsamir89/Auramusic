package az.simplesoft.aura.data.providers.zaycev

import android.net.Uri
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.TrackCandidate

class ZaycevPlaybackResolver(private val client: ZaycevSearchClient) {
    suspend fun resolve(candidate: TrackCandidate): ProviderResult<PlayableSource> = try {
        val token = candidate.playbackToken ?: client.fileMeta(candidate.id).streamingToken
        val playback = client.playback(token, candidate.detailUrl)
        val headers = client.playbackHeaders(candidate.detailUrl, playback.url)
        if (!client.validatePlayable(playback.url, headers)) {
            ProviderResult.Failure(
                ProviderFailureReason.NOT_PLAYABLE,
                "Источник не прошёл проверку",
                diagnostics = ZaycevDiagnostics("validate").asMap()
            )
        } else {
            val duration = candidate.durationMs ?: playback.durationSeconds?.times(1000L)
            val track = Track(
                id = "zaycev:${candidate.id}",
                title = candidate.title,
                artist = candidate.artist,
                artworkUrl = candidate.artworkUrl,
                durationMs = duration,
                sourceId = ZaycevProvider.ID,
                sourcePageUrl = candidate.detailUrl,
                playbackType = PlaybackType.DIRECT_STREAM,
                streamUrl = playback.url,
                requestHeaders = headers,
                isPlayable = true
            )
            ProviderResult.Success(
                PlayableSource(
                    providerId = ZaycevProvider.ID,
                    track = track,
                    playbackUri = Uri.parse(playback.url),
                    requestHeaders = headers,
                    cookies = headers["Cookie"],
                    mimeType = "audio/mpeg",
                    expiresAt = System.currentTimeMillis() + 8 * 60_000L,
                    sourcePageUrl = candidate.detailUrl
                ),
                ZaycevDiagnostics("http-resolver", client.lastValidationStatus).asMap()
            )
        }
    } catch (error: ZaycevSearchClient.HttpStatusException) {
        ProviderResult.Failure(
            if (error.statusCode in listOf(401, 403, 423)) ProviderFailureReason.ACCESS_RESTRICTED else ProviderFailureReason.NETWORK,
            "Zaycev вернул HTTP ${error.statusCode}",
            error,
            ZaycevDiagnostics("play", error.statusCode).asMap()
        )
    } catch (error: Throwable) {
        ProviderResult.Failure(
            ProviderFailureReason.NETWORK,
            error.message ?: "Не удалось получить поток",
            error,
            ZaycevDiagnostics("play", detail = error.javaClass.simpleName).asMap()
        )
    }
}
