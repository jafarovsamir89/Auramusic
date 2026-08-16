package az.simplesoft.aura.assistant

/** Turns recognizer callbacks into exactly one executable command per session. */
internal class VoiceCommandGate {
    private var delivered = false

    /** Partial hypotheses are UI-only; executing them repeats user commands. */
    fun onPartial(text: String): String? = null

    fun onFinal(text: String): String? {
        if (delivered) return null
        return text.normalizedOrNull()?.also { delivered = true }
    }

    fun reset() {
        delivered = false
    }
}

private fun String.normalizedOrNull(): String? = trim().takeIf(String::isNotBlank)
