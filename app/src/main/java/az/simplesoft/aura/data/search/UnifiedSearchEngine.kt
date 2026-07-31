package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.providers.MusicProvider
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.TrackCandidate
import java.util.concurrent.ConcurrentHashMap

class UnifiedSearchEngine(private val providers: List<MusicProvider>) {
    private data class CacheEntry<T>(val value: T, val expiresAt: Long)
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<TrackCandidate>>>()
    private val sourceCache = ConcurrentHashMap<String, CacheEntry<PlayableSource>>()

    suspend fun search(request: MusicSearchRequest): ProviderResult<List<TrackCandidate>> {
        val key = "${request.query.lowercase()}|${request.artist.orEmpty().lowercase()}|${request.limit}"
        searchCache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
            return ProviderResult.Success(it.value, mapOf("cache" to "hit"))
        }
        var lastFailure: ProviderResult.Failure? = null
        providers.asSequence()
            .filter { request.preferredProviderId == null || it.id == request.preferredProviderId }
            .sortedByDescending(MusicProvider::priority)
            .forEach { provider ->
            when (val result = provider.search(request)) {
                is ProviderResult.Success -> {
                    val ranked = TrackMatcher.rank(request, result.value).take(request.limit)
                    if (ranked.isNotEmpty()) {
                        searchCache[key] = CacheEntry(ranked, System.currentTimeMillis() + SEARCH_TTL_MS)
                        return ProviderResult.Success(ranked, result.diagnostics + ("provider" to provider.id))
                    }
                }
                is ProviderResult.Failure -> lastFailure = result
            }
        }
        return lastFailure ?: ProviderResult.Failure(ProviderFailureReason.NOT_FOUND, "Треки не найдены")
    }

    suspend fun resolve(candidate: TrackCandidate): ProviderResult<PlayableSource> {
        val provider = providers.firstOrNull { it.id == candidate.providerId }
            ?: return ProviderResult.Failure(ProviderFailureReason.NOT_FOUND, "Провайдер не найден")
        sourceCache[candidate.id]?.takeIf { entry ->
            val safeExpiry = entry.value.expiresAt?.minus(30_000L) ?: entry.expiresAt
            entry.expiresAt > System.currentTimeMillis() && safeExpiry > System.currentTimeMillis()
        }?.let {
            if (provider.validate(it.value)) return ProviderResult.Success(it.value, mapOf("cache" to "hit"))
            sourceCache.remove(candidate.id)
        }

        return when (val result = provider.resolve(candidate)) {
            is ProviderResult.Success -> result.also {
                val expiry = it.value.expiresAt ?: System.currentTimeMillis() + SOURCE_TTL_MS
                sourceCache[candidate.id] = CacheEntry(it.value, expiry)
            }
            is ProviderResult.Failure -> result
        }
    }

    companion object {
        private const val SEARCH_TTL_MS = 2 * 60 * 60_000L
        private const val SOURCE_TTL_MS = 8 * 60_000L
    }
}
