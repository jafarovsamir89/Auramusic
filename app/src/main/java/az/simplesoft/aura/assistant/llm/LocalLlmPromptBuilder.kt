package az.simplesoft.aura.assistant.llm

import az.simplesoft.aura.assistant.AssistantContext
import az.simplesoft.aura.assistant.AssistantLanguage
import az.simplesoft.aura.assistant.AssistantRequest

/** Keeps prompts compact and gives Qwen only the capabilities AURA can validate. */
class LocalLlmPromptBuilder {
    fun systemPrompt(language: AssistantLanguage): String = """
        Ты AURA — локальный музыкальный помощник. Отвечай кратко, естественно, без выдумок.
        Команда плеера -> type=action. Обычный вопрос или разговор -> type=conversation с прямым ответом.
        Не используй unresolved для обычного вопроса; только если запрос непонятен или требует недоступных данных.
        Верни ровно один JSON без markdown. type: action|conversation|clarification|unresolved.
        action: PLAY, PAUSE, NEXT, PREVIOUS, SEARCH_MUSIC, PLAY_SIMILAR, MY_MIX, LIKE, UNLIKE,
        QUEUE_NEXT, QUEUE_ADD, OPEN_QUEUE, PLAY_PLAYLIST, CREATE_PLAYLIST, VOLUME_UP, VOLUME_DOWN,
        REPEAT, SHUFFLE, NOW_PLAYING. Схемы: {"type":"action","action":"...","parameters":{},"reply":"..."};
        {"type":"conversation","reply":"..."}; {"type":"clarification","question":"..."}; {"type":"unresolved"}.
        Язык: ${language.tag}. Одно короткое предложение. /no_think
    """.trimIndent()

    fun userPrompt(request: AssistantRequest, context: AssistantContext, recentTurns: List<Pair<String, String>>): String {
        val turns = recentTurns.asSequence()
            .filterNot { (role, text) -> role.equals("AURA", ignoreCase = true) && text.isFallbackReply() }
            .toList()
            .takeLast(3)
            .joinToString("\n") { (role, text) ->
                "- $role: ${text.take(120)}"
        }.ifBlank { "- нет предыдущих реплик" }
        val queue = context.queue.take(3).joinToString(", ") { "${it.artist} — ${it.title}" }
            .ifBlank { "пусто" }
        val results = context.lastSearchResults.take(3).joinToString(", ") { "${it.artist} — ${it.title}" }
            .ifBlank { "нет" }
        return """
            Вопрос: ${request.originalText.take(300)}
            Нормализованный: ${request.normalizedText.take(300)}
            Состояние: трек=${context.currentTrack?.title ?: "нет"}; исполнитель=${context.currentTrack?.artist ?: "нет"}; играет=${context.isPlaying}; поиск=${context.lastSearchQuery ?: "нет"}; очередь=${context.queue.size}; плейлист=${context.currentPlaylist ?: "нет"}; авто=${context.carMode}
            Результаты поиска: $results
            Очередь: $queue
            Память: ${context.memoryFacts.take(3).joinToString("; ") { "${it.key}=${it.value}" }.ifBlank { "нет" }}
            Диалог:
            $turns
            /no_think
        """.trimIndent()
    }
}

private fun String.isFallbackReply(): Boolean = trim() in setOf(
    "Я пока не знаю ответа на это локально и не буду придумывать.",
    "Buna hələ yerli cavabım yoxdur və cavabı uydurmayacağam.",
    "I don't have a local answer for that yet, and I won't make one up.",
    "Секунду, я подумаю…",
    "Bir saniyə, düşünüm…",
    "One moment, let me think…"
)
