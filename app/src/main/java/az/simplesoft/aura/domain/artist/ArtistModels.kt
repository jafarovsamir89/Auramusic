package az.simplesoft.aura.domain.artist

/** Match quality is intentionally explicit so diagnostics can explain why an artist won. */
enum class ArtistMatchType {
    EXACT_CANONICAL,
    EXACT_NORMALIZED,
    EXACT_ALIAS,
    EXACT_FOLDED,
    MORPHOLOGY,
    TRANSLITERATION,
    PREFIX,
    TOKEN,
    FUZZY
}

data class ArtistIndexEntry(
    val artistId: Long,
    val mbid: String?,
    val canonicalName: String,
    val sortName: String? = null,
    val country: String? = null,
    val type: String? = null,
    val disambiguation: String? = null,
    val aliases: List<String> = emptyList()
)

data class ArtistResolveContext(
    val preferredCountry: String? = null,
    val language: String? = null,
    val recentArtistIds: Set<Long> = emptySet(),
    val userAliases: Map<String, Long> = emptyMap()
)

data class ArtistCandidate(
    val artistId: Long,
    val mbid: String?,
    val canonicalName: String,
    val score: Float,
    val matchedAlias: String? = null,
    val matchType: ArtistMatchType,
    val country: String? = null,
    val type: String? = null,
    val disambiguation: String? = null
)

sealed interface ArtistResolveResult {
    val normalizedQuery: String
    val foldedQuery: String

    data class Resolved(
        val candidate: ArtistCandidate,
        override val normalizedQuery: String,
        override val foldedQuery: String,
        val alternatives: List<ArtistCandidate> = emptyList()
    ) : ArtistResolveResult

    data class Ambiguous(
        val candidates: List<ArtistCandidate>,
        override val normalizedQuery: String,
        override val foldedQuery: String
    ) : ArtistResolveResult

    data class NotFound(
        override val normalizedQuery: String,
        override val foldedQuery: String
    ) : ArtistResolveResult
}

interface ArtistResolver {
    suspend fun resolve(
        rawArtistName: String,
        context: ArtistResolveContext = ArtistResolveContext()
    ): ArtistResolveResult
}
