package az.simplesoft.aura.assistant

data class AuraAiContext(
    val currentTrack: String? = null,
    val currentArtist: String? = null,
    val isPlaying: Boolean = false,
    val hourOfDay: Int,
    val carMode: Boolean = false
)

interface RemoteAssistantAdapter {
    val isAvailable: Boolean
    suspend fun reason(
        input: String,
        context: AuraAiContext,
        memory: AssistantMemorySnapshot,
        language: AssistantLanguage
    ): AssistantReply
}

/** The single seam used by UI: understand, remember and return one safe executable turn. */
class AuraAiEngine(
    private val local: LocalIntentEngine,
    private val remote: RemoteAssistantAdapter,
    private val memory: CompactAssistantMemory
) {
    suspend fun respond(input: String, context: AuraAiContext): AssistantReply {
        val localReply = local.understand(input)
        val finalReply = if (localReply.route != AssistantRoute.NEEDS_REASONING) {
            localReply
        } else if (remote.isAvailable) {
            runCatching {
                remote.reason(input, context, memory.snapshot(), localReply.language)
            }.getOrElse {
                localReply.copy(
                    text = fallbackText(localReply.language, unavailable = false),
                    route = AssistantRoute.LOCAL_CONVERSATION,
                    source = AssistantSource.FALLBACK
                )
            }
        } else {
            localReply.copy(
                text = fallbackText(localReply.language, unavailable = true),
                route = AssistantRoute.LOCAL_CONVERSATION,
                source = AssistantSource.FALLBACK
            )
        }
        memory.record(input, finalReply)
        return finalReply
    }

    suspend fun memorySnapshot(): AssistantMemorySnapshot = memory.snapshot()
    suspend fun clearMemory() = memory.clear()

    private fun fallbackText(language: AssistantLanguage, unavailable: Boolean): String = when (language) {
        AssistantLanguage.RUSSIAN -> if (unavailable) {
            "Я поняла, что это не поиск музыки. Для свободного разговора осталось подключить мой AI-мозг."
        } else "Сейчас не удалось связаться с моим AI-мозгом. Попробуем ещё раз?"
        AssistantLanguage.AZERBAIJANI -> if (unavailable) {
            "Bunun musiqi axtarışı olmadığını anladım. Sərbəst söhbət üçün AI beynimi qoşmaq qalıb."
        } else "AI beynimlə indi əlaqə yaratmaq alınmadı. Bir daha yoxlayaq?"
        AssistantLanguage.ENGLISH -> if (unavailable) {
            "I understood that this isn't a music search. My AI brain still needs to be connected for open conversation."
        } else "I couldn't reach my AI brain just now. Shall we try again?"
    }
}
