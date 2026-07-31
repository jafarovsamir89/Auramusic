package az.simplesoft.aura.data.providers.zaycev

import android.content.Context
import az.simplesoft.aura.data.providers.MusicProvider
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.TrackCandidate

class ZaycevProvider(
    context: Context? = null,
    private val client: ZaycevSearchClient = ZaycevSearchClient(),
    private val searchParser: ZaycevSearchParser = ZaycevSearchParser(),
    private val trackParser: ZaycevTrackParser = ZaycevTrackParser(),
    private val playbackResolver: ZaycevPlaybackResolver = ZaycevPlaybackResolver(client),
    private val webResolver: ZaycevWebResolver? = context?.let(::ZaycevWebResolver)
) : MusicProvider {
    override val id: String = ID
    override val displayName: String = "Zaycev.net"
    override val priority: Int = 100

    override suspend fun search(request: MusicSearchRequest): ProviderResult<List<TrackCandidate>> = try {
        val api = client.search(request.query, request.limit)
        var candidates = searchParser.parseApiJson(api.body)
        var stage = "api-search"
        if (candidates.isEmpty()) {
            val html = client.searchHtml(request.query)
            candidates = searchParser.parseHtml(html.body, html.finalUrl)
            stage = "html-search"
        }
        if (request.autoPlay && candidates.size < 2) {
            val artist = candidates.firstOrNull()?.artist.orEmpty()
            if (artist.isNotBlank() && !artist.equals(request.query, ignoreCase = true)) {
                val related = runCatching {
                    searchParser.parseApiJson(client.search(artist, request.limit).body)
                }.getOrDefault(emptyList())
                candidates = (candidates + related).distinctBy(TrackCandidate::id)
                if (related.isNotEmpty()) stage += "+artist-queue"
            }
        }
        if (candidates.isEmpty()) {
            ProviderResult.Failure(
                ProviderFailureReason.NOT_FOUND,
                "По запросу ничего не найдено",
                diagnostics = ZaycevDiagnostics(stage, api.code).asMap()
            )
        } else {
            ProviderResult.Success(candidates, ZaycevDiagnostics(stage, api.code).asMap())
        }
    } catch (error: ZaycevSearchClient.HttpStatusException) {
        ProviderResult.Failure(
            ProviderFailureReason.NETWORK,
            "Поиск Zaycev вернул HTTP ${error.statusCode}",
            error,
            ZaycevDiagnostics("search", error.statusCode).asMap()
        )
    } catch (error: Throwable) {
        ProviderResult.Failure(
            ProviderFailureReason.PARSE,
            error.message ?: "Не удалось прочитать результаты Zaycev",
            error,
            ZaycevDiagnostics("search", detail = error.javaClass.simpleName).asMap()
        )
    }

    override suspend fun resolve(candidate: TrackCandidate): ProviderResult<PlayableSource> {
        val enriched = runCatching {
            trackParser.enrich(client.trackPage(candidate.detailUrl).body, candidate)
        }.getOrDefault(candidate)
        val direct = playbackResolver.resolve(enriched)
        if (direct is ProviderResult.Success) return direct
        runCatching { client.trackPage(candidate.detailUrl) }
        val retried = playbackResolver.resolve(enriched)
        if (retried is ProviderResult.Success || webResolver == null) return retried
        if (retried is ProviderResult.Failure && retried.reason == ProviderFailureReason.ACCESS_RESTRICTED) return retried
        return webResolver.resolve(enriched)
    }

    override suspend fun validate(source: PlayableSource): Boolean =
        client.validatePlayable(source.playbackUri.toString(), source.requestHeaders)

    companion object {
        const val ID = "zaycev"
    }
}
