package az.simplesoft.aura.assistant

/** Kept as a tokenizer utility for compatibility tests; no Azerbaijani Silero model is shipped. */
internal object SileroAzerbaijaniTokenizer {
    private const val SYMBOLS = "|||!'+,-.:;?hабвгдежзийклмнопрстуфхцчшщъыьэюяёєіїјўґғҕҗҙқҝҡңҥҫүұҳҷҹһӑӗәӝӟӣӥӧөӯӱӳӵӏ—… "
    private val symbolIds = SYMBOLS.withIndex().associate { it.value to it.index.toLong() }
    private val latinToCyrillic = mapOf(
        'A' to 'А', 'a' to 'а', 'B' to 'Б', 'b' to 'б', 'C' to 'Ҹ', 'c' to 'ҹ',
        'Ç' to 'Ч', 'ç' to 'ч', 'D' to 'Д', 'd' to 'д', 'E' to 'Е', 'e' to 'е',
        'Ə' to 'Ә', 'ə' to 'ә', 'F' to 'Ф', 'f' to 'ф', 'G' to 'Ҝ', 'g' to 'ҝ',
        'Ğ' to 'Ғ', 'ğ' to 'ғ', 'H' to 'Һ', 'h' to 'һ', 'X' to 'Х', 'x' to 'х',
        'I' to 'Ы', 'ı' to 'ы', 'İ' to 'И', 'i' to 'и', 'J' to 'Ж', 'j' to 'ж',
        'K' to 'К', 'k' to 'к', 'Q' to 'Г', 'q' to 'г', 'L' to 'Л', 'l' to 'л',
        'M' to 'М', 'm' to 'м', 'N' to 'Н', 'n' to 'н', 'O' to 'О', 'o' to 'о',
        'Ö' to 'Ө', 'ö' to 'ө', 'P' to 'П', 'p' to 'п', 'R' to 'Р', 'r' to 'р',
        'S' to 'С', 's' to 'с', 'Ş' to 'Ш', 'ş' to 'ш', 'T' to 'Т', 't' to 'т',
        'U' to 'У', 'u' to 'у', 'Ü' to 'Ү', 'ü' to 'ү', 'V' to 'В', 'v' to 'в',
        'Y' to 'Ј', 'y' to 'ј', 'Z' to 'З', 'z' to 'з'
    )

    fun encode(text: String): LongArray {
        val body = text.asSequence()
            .map { latinToCyrillic[it] ?: it.lowercaseChar() }
            .mapNotNull(symbolIds::get)
            .toList()
        return longArrayOf(2L) + body.toLongArray() + longArrayOf(1L)
    }
}
