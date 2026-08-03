package az.simplesoft.aura.assistant

/** Pure wake-word parsing, shared by the foreground microphone service and tests. */
internal object WakeWordMatcher {
    private val wakeWord = Regex("\\b(?:аура|aura)\\b", RegexOption.IGNORE_CASE)

    /** Returns null when the utterance did not invoke AURA, or text after the wake word otherwise. */
    fun commandAfterWakeWord(text: String): String? {
        val match = wakeWord.find(text.trim()) ?: return null
        return text.substring(match.range.last + 1)
            .trimStart { it.isWhitespace() || it == ',' || it == ':' || it == '—' || it == '-' }
            .trim()
    }
}
