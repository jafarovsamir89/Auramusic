package az.simplesoft.aura.assistant.llm

import az.simplesoft.aura.assistant.AssistantContext
import az.simplesoft.aura.assistant.AssistantLanguage
import az.simplesoft.aura.assistant.AssistantRequest

/** Keeps prompts compact and gives Qwen only the capabilities AURA can validate. */
class LocalLlmPromptBuilder {
    fun systemPrompt(language: AssistantLanguage): String = """
        Ты AURA — локальный персональный музыкальный помощник. /no_think
        Отвечай кратко, естественно и без канцелярита. Не выдумывай факты.
        Главная специализация: музыка, очередь, плейлисты и короткий разговор.
        Возвращай ТОЛЬКО один JSON-объект без markdown и без комментариев.
        Допустимые type: action, conversation, clarification, unresolved.
        Допустимые action: PLAY, PAUSE, NEXT, PREVIOUS, SEARCH_MUSIC, PLAY_SIMILAR,
        MY_MIX, LIKE, UNLIKE, QUEUE_NEXT, QUEUE_ADD, OPEN_QUEUE, PLAY_PLAYLIST,
        CREATE_PLAYLIST, VOLUME_UP, VOLUME_DOWN, REPEAT, SHUFFLE, NOW_PLAYING.
        Никаких других инструментов. Модель не выполняет действия сама.
        Для action используй {"type":"action","action":"...","parameters":{},"reply":"..."}.
        Для conversation используй {"type":"conversation","reply":"..."}.
        Для clarification используй {"type":"clarification","question":"..."}.
        Для unresolved используй {"type":"unresolved"}.
        Язык ответа: ${language.tag}. Голосовой ответ обычно одно предложение.
    """.trimIndent()

    fun userPrompt(request: AssistantRequest, context: AssistantContext, recentTurns: List<Pair<String, String>>): String {
        val turns = recentTurns.takeLast(6).joinToString("\n") { (role, text) ->
            "- $role: ${text.take(220)}"
        }.ifBlank { "- нет предыдущих реплик" }
        val queue = context.queue.take(5).joinToString(", ") { "${it.artist} — ${it.title}" }
            .ifBlank { "пусто" }
        val results = context.lastSearchResults.take(5).joinToString(", ") { "${it.artist} — ${it.title}" }
            .ifBlank { "нет" }
        return """
            Запрос пользователя: ${request.originalText.take(500)}
            Нормализованный текст: ${request.normalizedText.take(500)}
            Контекст:
            current_track=${context.currentTrack?.title ?: "нет"}
            current_artist=${context.currentTrack?.artist ?: "нет"}
            is_playing=${context.isPlaying}
            last_search=${context.lastSearchQuery ?: "нет"}
            last_search_results=$results
            queue=$queue
            queue_size=${context.queue.size}
            current_playlist=${context.currentPlaylist ?: "нет"}
            last_intent=${context.lastIntent ?: "нет"}
            memory=${context.memoryFacts.take(5).joinToString("; ") { "${it.key}=${it.value}" }.ifBlank { "нет" }}
            car_mode=${context.carMode}
            последние реплики:
            $turns
        """.trimIndent()
    }
}
