package az.simplesoft.aura.assistant

import kotlin.math.max

/** Small allocation-light fuzzy helpers for speech recognition mistakes. */
object FuzzyMatching {
    fun damerauLevenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val matrix = Array(left.length + 1) { IntArray(right.length + 1) }
        for (i in 0..left.length) matrix[i][0] = i
        for (j in 0..right.length) matrix[0][j] = j
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                )
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
                    matrix[i][j] = minOf(matrix[i][j], matrix[i - 2][j - 2] + 1)
                }
            }
        }
        return matrix[left.length][right.length]
    }

    fun similarity(left: String, right: String): Double {
        val a = TextNormalizer.normalizeForMatching(left)
        val b = TextNormalizer.normalizeForMatching(right)
        if (a.isBlank() && b.isBlank()) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        return 1.0 - damerauLevenshtein(a, b).toDouble() / max(a.length, b.length)
    }

    fun tokenSimilarity(left: String, right: String): Double {
        val a = TextNormalizer.normalizeForMatching(left).split(' ').filter(String::isNotBlank).toSet()
        val b = TextNormalizer.normalizeForMatching(right).split(' ').filter(String::isNotBlank).toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val matched = a.sumOf { token -> b.maxOf { similarity(token, it) }.coerceAtLeast(0.0) }
        return (matched / max(a.size, b.size)).coerceIn(0.0, 1.0)
    }

    fun characterNGramSimilarity(left: String, right: String, n: Int = 3): Double {
        fun grams(value: String): Set<String> {
            val normalized = TextNormalizer.normalizeForMatching(value).replace(" ", "_")
            if (normalized.length <= n) return setOf(normalized)
            return normalized.windowed(n).toSet()
        }
        val a = grams(left)
        val b = grams(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return (2.0 * a.intersect(b).size / (a.size + b.size)).coerceIn(0.0, 1.0)
    }

    fun combined(left: String, right: String): Double = (
        similarity(left, right) * 0.45 +
            tokenSimilarity(left, right) * 0.35 +
            characterNGramSimilarity(left, right) * 0.20
        ).coerceIn(0.0, 1.0)
}
