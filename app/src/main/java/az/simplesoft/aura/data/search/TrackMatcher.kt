package az.simplesoft.aura.data.search

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.TrackCandidate
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

object TrackMatcher {
    fun rank(request: MusicSearchRequest, candidates: List<TrackCandidate>): List<TrackCandidate> =
        candidates.map { candidate ->
            val requestedTitle = request.title ?: request.query
            val requestedArtist = request.artist.orEmpty()
            val titleScore = similarity(requestedTitle, candidate.title)
            val artistScore = if (requestedArtist.isBlank()) {
                artistFromQueryScore(request.query, candidate.artist)
            } else similarity(requestedArtist, candidate.artist)
            val exactBonus = if (
                normalize(request.query) == normalize("${candidate.artist} ${candidate.title}") ||
                normalize(request.query) == normalize("${candidate.title} ${candidate.artist}")
            ) 0.12 else 0.0
            val durationScore = 1.0
            val providerReliability = if (candidate.providerId == "zaycev") 1.0 else 0.8
            candidate.copy(
                confidence = (
                    titleScore * 0.50 +
                        artistScore * 0.35 +
                        durationScore * 0.10 +
                        providerReliability * 0.05 +
                        exactBonus
                    ).coerceIn(0.0, 1.0)
            )
        }.sortedByDescending(TrackCandidate::confidence)

    fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        return max(basicSimilarity(a, b), basicSimilarity(transliterate(a), transliterate(b)))
    }

    private fun basicSimilarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val tokenScore = tokenSimilarity(a, b)
        val editScore = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        val containment = if (a.contains(b) || b.contains(a)) 0.94 else 0.0
        return max(max(containment, editScore), tokenScore * 0.58 + editScore * 0.42).coerceIn(0.0, 1.0)
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("\\b(feat|ft|при\\s+уч)\\.?\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun artistFromQueryScore(query: String, artist: String): Double {
        val normalizedQuery = normalize(query)
        val normalizedArtist = normalize(artist)
        return when {
            normalizedArtist.isBlank() -> 0.0
            normalizedQuery.contains(normalizedArtist) -> 1.0
            else -> tokenSimilarity(normalizedQuery, normalizedArtist)
        }
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        val left = a.split(' ').filter(String::isNotBlank).toSet()
        val right = b.split(' ').filter(String::isNotBlank).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun transliterate(value: String): String = buildString {
        value.forEach { char ->
            append(
                when (char) {
                    'а' -> "a"; 'б' -> "b"; 'в' -> "v"; 'г' -> "g"; 'д' -> "d"
                    'е' -> "e"; 'ж' -> "zh"; 'з' -> "z"; 'и' -> "i"; 'й' -> "y"
                    'к' -> "k"; 'л' -> "l"; 'м' -> "m"; 'н' -> "n"; 'о' -> "o"
                    'п' -> "p"; 'р' -> "r"; 'с' -> "s"; 'т' -> "t"; 'у' -> "u"
                    'ф' -> "f"; 'х' -> "h"; 'ц' -> "ts"; 'ч' -> "ch"; 'ш' -> "sh"
                    'щ' -> "sch"; 'ы' -> "y"; 'э' -> "e"; 'ю' -> "yu"; 'я' -> "ya"
                    'ь', 'ъ' -> ""
                    else -> char.toString()
                }
            )
        }
    }
}
