package az.simplesoft.aura.assistant

data class AuraAiContext(
    val currentTrack: String? = null,
    val currentArtist: String? = null,
    val isPlaying: Boolean = false,
    val hourOfDay: Int,
    val carMode: Boolean = false,
    val queueSize: Int = 0,
    val playlists: List<String> = emptyList()
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

/**
 * The single seam used by UI: ask the online agent for one safe executable turn,
 * remember it, and fall back to deterministic local commands only when the agent is unavailable.
 */
class AuraAiEngine(
    private val local: LocalIntentEngine,
    private val remote: RemoteAssistantAdapter,
    private val memory: CompactAssistantMemory
) {
    suspend fun respond(input: String, context: AuraAiContext): AssistantReply {
        val detectedLanguage = AssistantLanguage.detect(input)
        val finalReply = if (remote.isAvailable) {
            runCatching {
                remote.reason(input, context, memory.snapshot(), detectedLanguage)
            }.getOrElse { error ->
                runCatching {
                    android.util.Log.w(
                        "AuraAi",
                        "Remote agent failed: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}"
                    )
                }
                localFallback(input, detectedLanguage, remoteFailed = true)
            }
        } else {
            localFallback(input, detectedLanguage, remoteFailed = false)
        }
        memory.record(input, finalReply)
        return finalReply
    }

    suspend fun memorySnapshot(): AssistantMemorySnapshot = memory.snapshot()
    suspend fun clearMemory() = memory.clear()

    private fun localFallback(
        input: String,
        language: AssistantLanguage,
        remoteFailed: Boolean
    ): AssistantReply {
        val localReply = local.understand(input)
        if (localReply.intent != MusicIntent.Unknown) {
            return localReply.copy(source = AssistantSource.FALLBACK)
        }
        return localReply.copy(
            text = fallbackText(language, remoteFailed),
            language = language,
            route = AssistantRoute.LOCAL_CONVERSATION,
            source = AssistantSource.FALLBACK
        )
    }

    private fun fallbackText(language: AssistantLanguage, remoteFailed: Boolean): String = when (language) {
        AssistantLanguage.RUSSIAN -> if (remoteFailed) {
            "Не удалось связаться с моим AI-мозгом. Простые команды плеера всё ещё работают."
        } else "AI-мозг не настроен. Пока доступны только простые команды плеера."
        AssistantLanguage.AZERBAIJANI -> if (remoteFailed) {
            "AI beynimlə əlaqə alınmadı. Sadə pleyer əmrləri hələ də işləyir."
        } else "AI beynim qurulmayıb. Hələlik yalnız sadə pleyer əmrləri işləyir."
        AssistantLanguage.ENGLISH -> if (remoteFailed) {
            "I couldn't reach my AI brain. Basic player commands still work."
        } else "My AI brain isn't configured. Only basic player commands are available for now."
    }
}
