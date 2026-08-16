package az.simplesoft.aura.assistant

/** Immutable representation of one speech/text turn. Search-safe text keeps the user's names intact. */
data class NormalizedAssistantInput(
    val originalText: String,
    val normalizedText: String,
    val searchSafeText: String,
    val tokens: List<String>,
    val detectedLanguage: AssistantLanguage
)

object TextNormalizer {
    private val apostrophes = Regex("[’'ʻ`]")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    /**
     * Normalizes only the matching representation. The searchSafeText is deliberately kept
     * close to the spoken phrase so artist/song names such as Röya and МакSим survive intact.
     */
    fun normalize(raw: String): NormalizedAssistantInput {
        val original = raw.trim()
        val searchSafe = whitespace.replace(original, " ")
        val normalized = normalizeForMatching(searchSafe)
        return NormalizedAssistantInput(
            originalText = raw,
            normalizedText = normalized,
            searchSafeText = searchSafe,
            tokens = normalized.split(' ').filter(String::isNotBlank),
            detectedLanguage = AssistantLanguage.detect(normalized)
        )
    }

    fun normalizeForMatching(raw: String): String {
        val lowered = raw.lowercase()
            .replace('ё', 'е')
            .replace(apostrophes, "")
            .map(::foldAzerbaijaniLetter)
            .joinToString("")
        val cleaned = whitespace.replace(punctuation.replace(lowered, " "), " ").trim()
        return cleaned.split(' ').joinToString(" ") { token ->
            when (token) {
                "nobeti" -> "novbeti"
                "mahnisi" -> "mahnisi"
                else -> token
            }
        }
    }

    private fun foldAzerbaijaniLetter(char: Char): Char = when (char) {
        'ə' -> 'e'
        'ı' -> 'i'
        'ö' -> 'o'
        'ü' -> 'u'
        'ş' -> 's'
        'ç' -> 'c'
        'ğ' -> 'g'
        else -> char
    }
}
