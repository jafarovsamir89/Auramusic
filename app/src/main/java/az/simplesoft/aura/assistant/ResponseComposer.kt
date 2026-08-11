package az.simplesoft.aura.assistant

/** Small, deterministic response variation layer. It keeps TTS replies short and avoids repetition. */
class ResponseComposer {
    private val lastTemplateByKey = mutableMapOf<String, Int>()

    @Synchronized
    fun choose(key: String, language: AssistantLanguage, variants: List<String>): String {
        if (variants.isEmpty()) return ""
        val previous = lastTemplateByKey[key]
        val next = variants.indices.firstOrNull { it != previous } ?: 0
        lastTemplateByKey[key] = next
        return variants[next]
    }

    fun greeting(language: AssistantLanguage, hour: Int, carMode: Boolean, isPlaying: Boolean): String {
        val key = "greeting:${language.name}"
        val variants = when (language) {
            AssistantLanguage.RUSSIAN -> when {
                hour < 12 -> listOf("Доброе утро. Что сегодня послушаем?", "Доброе утро! Продолжим музыку?")
                hour >= 18 -> listOf("Добрый вечер. Что включить?", "Привет! Сделаем этот вечер музыкальным?")
                else -> listOf("Привет! Что будем слушать?", "Рада тебя слышать. Включить музыку?")
            }
            AssistantLanguage.AZERBAIJANI -> listOf("Salam! Bu gün nə dinləyirik?", "Salam! Musiqini davam etdirək?")
            AssistantLanguage.ENGLISH -> listOf("Hi! What shall we listen to?", "Good to hear you. Want some music?")
        }
        val prefix = if (carMode) when (language) {
            AssistantLanguage.RUSSIAN -> "Я в автомобильном режиме. "
            AssistantLanguage.AZERBAIJANI -> "Avtomobil rejimindəyəm. "
            AssistantLanguage.ENGLISH -> "Car mode is on. "
        } else ""
        return prefix + choose(key, language, variants)
    }
}
