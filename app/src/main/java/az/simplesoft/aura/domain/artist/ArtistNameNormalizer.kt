package az.simplesoft.aura.domain.artist

import java.text.Normalizer
import java.util.Locale

data class NormalizedArtistName(val normalized: String, val folded: String)

/**
 * Keeps the display spelling intact while producing a conservative searchable key.
 * The same key is used by the offline builder (Python implementation mirrors this map).
 */
object ArtistNameNormalizer {
    private val punctuation = Regex("[\\u2018\\u2019\\u201B\\u2032\\u0060]")
    private val dashes = Regex("[\\u2010-\\u2015\\u2212]")
    private val noise = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")

    private val azFold = mapOf(
        'ə' to 'e', 'ı' to 'i', 'ö' to 'o', 'ü' to 'u',
        'ş' to 's', 'ç' to 'c', 'ğ' to 'g'
    )

    private val cyrillic = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(punctuation, "'")
        .replace(dashes, "-")
        .replace("'", "")
        .replace("-", " ")
        .replace(noise, " ")
        .replace(spaces, " ")
        .trim()

    fun folded(value: String): String {
        val normalized = normalize(value)
        val decomposed = Normalizer.normalize(normalized, Normalizer.Form.NFKD)
            .replace(Regex("\\p{Mn}+"), "")
        return buildString(decomposed.length) {
            decomposed.forEach { char ->
                when {
                    azFold.containsKey(char) -> append(azFold.getValue(char))
                    cyrillic.containsKey(char) -> append(cyrillic.getValue(char))
                    char.isLetterOrDigit() -> append(char)
                    char.isWhitespace() -> append(' ')
                    else -> append(' ')
                }
            }
        }.replace(spaces, " ").trim()
    }

    fun both(value: String): NormalizedArtistName = NormalizedArtistName(normalize(value), folded(value))

    fun transliterationVariants(value: String): Set<String> {
        val key = both(value)
        return setOf(key.normalized, key.folded).filter(String::isNotBlank).toSet()
    }
}
