package az.simplesoft.aura.domain.artist

import kotlin.math.max

/** Small built-in fallback and deterministic test adapter. Production builds prefer the SQLite asset. */
class InMemoryArtistResolver(
    entries: List<ArtistIndexEntry>,
    private val normalizer: ArtistNameNormalizer = ArtistNameNormalizer
) : ArtistResolver {
    private data class Indexed(val entry: ArtistIndexEntry, val value: String, val folded: String, val source: String?)

    private val indexed = entries.flatMap { entry ->
        buildList {
            add(Indexed(entry, normalizer.normalize(entry.canonicalName), normalizer.folded(entry.canonicalName), null))
            entry.aliases.forEach { alias ->
                add(Indexed(entry, normalizer.normalize(alias), normalizer.folded(alias), alias))
            }
        }
    }

    override suspend fun resolve(rawArtistName: String, context: ArtistResolveContext): ArtistResolveResult {
        val query = ArtistNameNormalizer.both(rawArtistName)
        if (query.normalized.isBlank()) return ArtistResolveResult.NotFound(query.normalized, query.folded)
        val queryForms = AzerbaijaniArtistMorphologyNormalizer.variants(rawArtistName)
        val scored = indexed.groupBy { it.entry.artistId }.mapNotNull { (_, values) ->
            values.mapNotNull { score(it, query, queryForms, context) }.maxByOrNull { it.first }
                ?.let { (score, indexedValue, type) ->
                    val entry = indexedValue.entry
                    val historyBoost = if (entry.artistId in context.recentArtistIds) 0.012 else 0.0
                    val regionBoost = if (!context.preferredCountry.isNullOrBlank() && entry.country.equals(context.preferredCountry, true)) 0.008 else 0.0
                    ArtistCandidate(entry.artistId, entry.mbid, entry.canonicalName, (score + historyBoost + regionBoost).coerceAtMost(1.0).toFloat(), indexedValue.source, type, entry.country, entry.type, entry.disambiguation)
                }
        }.sortedByDescending { it.score }

        val top = scored.firstOrNull() ?: return ArtistResolveResult.NotFound(query.normalized, query.folded)
        val second = scored.getOrNull(1)
        val margin = top.score - (second?.score ?: 0f)
        val highConfidence = top.score >= 0.90f && (second == null || margin >= 0.045f)
        if (highConfidence) return ArtistResolveResult.Resolved(top, query.normalized, query.folded, scored.drop(1).take(4))
        val plausible = scored.filter { it.score >= max(0.72f, top.score - 0.10f) }.take(5)
        return if (plausible.size > 1 || top.score >= 0.72f) {
            ArtistResolveResult.Ambiguous(plausible, query.normalized, query.folded)
        } else ArtistResolveResult.NotFound(query.normalized, query.folded)
    }

    private fun score(
        candidate: Indexed,
        query: NormalizedArtistName,
        forms: Set<String>,
        context: ArtistResolveContext
    ): Triple<Double, Indexed, ArtistMatchType>? {
        val userAlias = context.userAliases.entries.firstOrNull { (alias, id) ->
            id == candidate.entry.artistId && ArtistNameNormalizer.folded(alias) in forms
        }
        if (userAlias != null) return Triple(1.0, candidate.copy(source = userAlias.key), ArtistMatchType.EXACT_ALIAS)
        if (candidate.value == query.normalized) {
            return Triple(if (candidate.source == null) 1.0 else 0.99, candidate, if (candidate.source == null) ArtistMatchType.EXACT_CANONICAL else ArtistMatchType.EXACT_ALIAS)
        }
        if (candidate.value in forms) return Triple(0.98, candidate, if (candidate.source == null) ArtistMatchType.EXACT_NORMALIZED else ArtistMatchType.MORPHOLOGY)
        if (candidate.folded == query.folded || candidate.folded in forms) {
            return Triple(if (candidate.source == null) 0.97 else 0.96, candidate, if (candidate.folded == query.folded) ArtistMatchType.EXACT_FOLDED else ArtistMatchType.MORPHOLOGY)
        }
        if (candidate.folded.startsWith(query.folded) && query.folded.length >= 4) return Triple(0.90, candidate, ArtistMatchType.PREFIX)
        val similarity = tokenSimilarity(query.folded, candidate.folded)
        if (similarity >= 0.76) return Triple(0.70 + similarity * 0.23, candidate, if (similarity >= 0.88) ArtistMatchType.TOKEN else ArtistMatchType.FUZZY)
        return null
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        val a = left.split(' ').filter(String::isNotBlank).toSet()
        val b = right.split(' ').filter(String::isNotBlank).toSet()
        val overlap = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / max(a.size, b.size)
        val edit = normalizedEdit(left, right)
        return overlap * 0.65 + edit * 0.35
    }

    private fun normalizedEdit(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val above = previous[j + 1]
                previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1, diagonal + if (a[i] == b[j]) 0 else 1)
                diagonal = above
            }
        }
        return 1.0 - previous[b.length].toDouble() / max(a.length, b.length)
    }

    companion object {
        val default: InMemoryArtistResolver by lazy { InMemoryArtistResolver(seedEntries()) }

        fun seedEntries(): List<ArtistIndexEntry> = listOf(
            ArtistIndexEntry(1, "seed-aygun", "Aygün Kazımova", country = "AZ", aliases = listOf("Aygun Kazimova", "Aygün Kazimova", "Aygun Kazımova")),
            ArtistIndexEntry(2, "seed-roya", "Röya", country = "AZ", aliases = listOf("Roya")),
            ArtistIndexEntry(3, "seed-miri", "Miri Yusif", country = "AZ", aliases = listOf("Miri Yusuf")),
            ArtistIndexEntry(4, "seed-eyyub", "Eyyub Yaqubov", country = "AZ", aliases = listOf("Eyyub Yagubov")),
            ArtistIndexEntry(5, "seed-tunzale", "Tünzalə Ağayeva", country = "AZ", aliases = listOf("Tunzale Agayeva", "Tunzala Agayeva")),
            ArtistIndexEntry(6, "seed-brilliant", "Brilliant Dadaşova", country = "AZ", aliases = listOf("Brilliant Dadashova")),
            ArtistIndexEntry(7, "seed-teymur", "Teymur Əmrah", country = "AZ", aliases = listOf("Teymur Emrah")),
            ArtistIndexEntry(8, "seed-maksim", "МакSим", country = "RU", aliases = listOf("Максим", "Maksim")),
            ArtistIndexEntry(9, "seed-bilan", "Дима Билан", country = "RU", aliases = listOf("Dima Bilan")),
            ArtistIndexEntry(10, "seed-shatunov", "Юрий Шатунов", country = "RU", aliases = listOf("Yuriy Shatunov", "Yuri Shatunov")),
            ArtistIndexEntry(11, "seed-leps", "Григорий Лепс", country = "RU", aliases = listOf("Grigoriy Leps", "Grigory Leps")),
            ArtistIndexEntry(12, "seed-weeknd", "The Weeknd", country = "CA", aliases = listOf("The Weekend")),
            ArtistIndexEntry(13, "seed-linkin", "Linkin Park", country = "US", aliases = listOf("Linken Park")),
            ArtistIndexEntry(14, "seed-acdc", "AC/DC", country = "AU", aliases = listOf("ACDC", "AC DC")),
            ArtistIndexEntry(15, "seed-rem", "R.E.M.", country = "US", aliases = listOf("REM", "R E M")),
            ArtistIndexEntry(16, "seed-beyonce", "Beyoncé", country = "US", aliases = listOf("Beyonce")),
            ArtistIndexEntry(17, "seed-rihanna", "Rihanna", country = "BB"),
            ArtistIndexEntry(18, "seed-yusuf", "Yusuf Islam", country = "GB", aliases = listOf("Cat Stevens")),
            ArtistIndexEntry(19, "seed-ariana", "Ariana Grande", country = "US"),
            ArtistIndexEntry(20, "seed-adele", "Adele", country = "GB")
        )
    }
}
