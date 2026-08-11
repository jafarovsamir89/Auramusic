package az.simplesoft.aura.assistant

enum class VoiceStyle {
    NEUTRAL, FRIENDLY, CONFIRMATION, QUESTION, CALM, DRIVING, KIDS, IMPORTANT
}

enum class ResponseVerbosity { SILENT, SHORT, NORMAL }

data class AuraVoiceProfile(
    val speechRate: Float = 0.94f,
    val pitch: Float = 1.02f,
    val volume: Float = 1.0f,
    val sentencePauseMs: Long = 180L,
    val commaPauseMs: Long = 90L,
    val questionIntonation: Boolean = true,
    val confirmationStyle: VoiceStyle = VoiceStyle.CONFIRMATION,
    val carModeStyle: VoiceStyle = VoiceStyle.DRIVING,
    val quietStyle: VoiceStyle = VoiceStyle.CALM,
    val kidsStyle: VoiceStyle = VoiceStyle.KIDS
)

object SpeechResponsePolicy {
    fun verbosity(intent: MusicIntent, carMode: Boolean): ResponseVerbosity = when {
        intent == MusicIntent.Next || intent == MusicIntent.Previous -> ResponseVerbosity.SHORT
        carMode -> ResponseVerbosity.SHORT
        else -> ResponseVerbosity.NORMAL
    }
}

interface AuraVoiceEngine {
    val id: String
    val supportedLanguages: Set<AssistantLanguage>
    val isReady: Boolean

    /** Preparation is deliberately non-blocking; large packs are installed explicitly. */
    fun prepare()

    fun speak(text: String, language: AssistantLanguage, style: VoiceStyle = VoiceStyle.NEUTRAL)
    fun stop()
}

/** Removes markup and prepares short, pronounceable text without changing the UI label. */
class SpeechTextProcessor(
    private val pronunciation: PronunciationDictionary = PronunciationDictionary()
) {
    fun process(text: String, language: AssistantLanguage): String {
        val withoutUrls = text.replace(Regex("https?://\\S+|www\\.\\S+"), " ссылку ")
        val withoutEmoji = withoutUrls.replace(Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]"), "")
        val safe = withoutEmoji
            .replace(Regex("[{}\\[\\]<>*_#@~|^=]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
        return pronunciation.apply(safe, language)
            .replace(Regex("([.!?])\\s*"), "$1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/** Small curated dictionary; display text stays untouched and only TTS text is rewritten. */
class PronunciationDictionary {
    private val russian = linkedMapOf(
        "Aygün Kazımova" to "Айгюн Кязимова",
        "Aygun Kazimova" to "Айгюн Кязимова",
        "Röya" to "Ройя",
        "Roya" to "Ройя",
        "Miri Yusif" to "Мири Юсиф",
        "Linkin Park" to "Линкин Парк",
        "The Weeknd" to "Зе Уикенд",
        "МакSим" to "Максим",
        "Rammstein" to "Рамштайн"
    )

    fun apply(text: String, language: AssistantLanguage): String {
        if (language == AssistantLanguage.AZERBAIJANI) return text
        return (if (language == AssistantLanguage.RUSSIAN) russian else russian)
            .entries.fold(text) { result, (name, spoken) ->
                result.replace(name, spoken, ignoreCase = true)
            }
    }
}
