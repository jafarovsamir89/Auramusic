package az.simplesoft.aura.assistant

data class AuraAiContext(
    val currentTrack: String? = null,
    val currentArtist: String? = null,
    val isPlaying: Boolean = false,
    val hourOfDay: Int,
    val carMode: Boolean = false,
    val queue: List<AssistantTrackContext> = emptyList(),
    val currentIndex: Int = 0,
    val lastSearchQuery: String? = null,
    val lastSearchResults: List<AssistantTrackContext> = emptyList(),
    val playlists: List<String> = emptyList(),
    val favoriteCount: Int = 0,
    val currentPlaylist: String? = null,
    val currentTrackLiked: Boolean = false,
    val lastIntent: String? = null
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
    private val remote: RemoteAssistantAdapter? = null,
    private val memory: CompactAssistantMemory,
    private val localAssistant: LocalAssistantEngine = LocalAssistantEngine(local),
    private val remoteEnabled: Boolean = false
) {
    suspend fun respond(input: String, context: AuraAiContext): AssistantReply {
        val memorySnapshot = memory.snapshot()
        val localDecision = localAssistant.decide(input, context.toAssistantContext(memorySnapshot))
        val localReply = AssistantReply(
            intent = localDecision.action ?: MusicIntent.Unknown,
            text = localDecision.reply,
            language = localDecision.language,
            route = when {
                localDecision.action != null -> AssistantRoute.LOCAL_ACTION
                localDecision.isUnresolved -> AssistantRoute.NEEDS_REASONING
                else -> AssistantRoute.LOCAL_CONVERSATION
            },
            memoryInsights = localDecision.memoryInsights,
            diagnostics = localDecision.diagnostics
        )
        val finalReply = if (!localDecision.isUnresolved) {
            localReply
        } else if (remoteEnabled && remote?.isAvailable == true) {
            runCatching {
                remote?.reason(input, context, memorySnapshot, localReply.language)
                    ?: error("Remote assistant is unavailable")
            }.getOrElse {
                localReply.copy(
                    text = fallbackText(localReply.language, unavailable = false),
                    route = AssistantRoute.LOCAL_CONVERSATION,
                    source = AssistantSource.FALLBACK,
                    diagnostics = localDecision.diagnostics
                )
            }
        } else {
            localReply.copy(
                text = fallbackText(localReply.language, unavailable = true),
                route = AssistantRoute.LOCAL_CONVERSATION,
                source = AssistantSource.FALLBACK,
                diagnostics = localDecision.diagnostics
            )
        }
        memory.record(input, finalReply)
        return finalReply
    }

    suspend fun memorySnapshot(): AssistantMemorySnapshot = memory.snapshot()
    suspend fun clearMemory() = memory.clear()

    private fun fallbackText(language: AssistantLanguage, unavailable: Boolean): String = when (language) {
        AssistantLanguage.RUSSIAN -> if (unavailable) {
            "Я пока не умею отвечать на это локально и не буду придумывать ответ."
        } else "Сейчас не удалось связаться с моим AI-мозгом. Попробуем ещё раз?"
        AssistantLanguage.AZERBAIJANI -> if (unavailable) {
            "Buna hələ yerli cavabım yoxdur və cavabı uydurmayacağam."
        } else "AI beynimlə indi əlaqə yaratmaq alınmadı. Bir daha yoxlayaq?"
        AssistantLanguage.ENGLISH -> if (unavailable) {
            "I don't have a local answer for that yet, and I won't make one up."
        } else "I couldn't reach my AI brain just now. Shall we try again?"
    }

    private fun AuraAiContext.toAssistantContext(memorySnapshot: AssistantMemorySnapshot) = AssistantContext(
        currentTrack = currentTrack?.let { AssistantTrackContext("current", it, currentArtist.orEmpty()) },
        isPlaying = isPlaying,
        queue = queue,
        currentIndex = currentIndex,
        lastSearchQuery = lastSearchQuery,
        lastSearchResults = lastSearchResults,
        playlists = playlists,
        favoriteCount = favoriteCount,
        currentPlaylist = currentPlaylist,
        currentTrackLiked = currentTrackLiked,
        lastIntent = lastIntent,
        hourOfDay = hourOfDay,
        carMode = carMode,
        memoryFacts = memorySnapshot.facts
    )
}
