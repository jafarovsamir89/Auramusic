package az.simplesoft.aura.domain.artist

/** Removes only known Azerbaijani case/postposition endings from a final token. */
object AzerbaijaniArtistMorphologyNormalizer {
    private val suffixes = listOf("ile", "nın", "nin", "nun", "nün", "dan", "dən", "tan", "dən", "ya", "yə", "nı", "ni", "nu", "nü")
        .sortedByDescending(String::length)

    fun baseForm(value: String): String {
        val normalized = ArtistNameNormalizer.normalize(value)
        val parts = normalized.split(' ').filter(String::isNotBlank).toMutableList()
        val last = parts.lastOrNull() ?: return normalized
        if (ArtistNameNormalizer.folded(last) == "ile" && parts.size > 1) {
            parts.removeAt(parts.lastIndex)
            return parts.joinToString(" ")
        }
        val foldedLast = ArtistNameNormalizer.folded(last)
        val suffix = suffixes.firstOrNull { foldedLast.endsWith(ArtistNameNormalizer.folded(it)) }
            ?: return normalized
        val stem = foldedLast.removeSuffix(ArtistNameNormalizer.folded(suffix))
        if (stem.length < 4) return normalized
        parts[parts.lastIndex] = stem
        return parts.joinToString(" ")
    }

    fun variants(value: String): Set<String> {
        val original = ArtistNameNormalizer.both(value)
        val base = baseForm(value)
        return setOf(original.normalized, original.folded, ArtistNameNormalizer.normalize(base), ArtistNameNormalizer.folded(base))
            .filter(String::isNotBlank)
            .toSet()
    }
}
