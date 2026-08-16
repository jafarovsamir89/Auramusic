package az.simplesoft.aura.assistant


data class AuraAiContext(
    val currentTrack: String? = null,
    val currentArtist: String? = null,
    val isPlaying: Boolean = false,
    val hourOfDay: Int,
    val carMode: Boolean = false,
    val queueSize: Int = 0,
    val queue: List<AssistantTrackContext> = emptyList(),
    val currentIndex: Int = 0,
    val lastSearchQuery: String? = null,
    val lastSearchResults: List<AssistantTrackContext> = emptyList(),
    val playlists: List<String> = emptyList(),
    val favoriteCount: Int = 0,
    val currentPlaylist: String? = null,
    val currentTrackLiked: Boolean = false,
    val lastIntent: String? = null,
    val recentTurns: List<Pair<String, String>> = emptyList()
)

/** One local entry point for UI and voice. Remote reasoning is intentionally absent by default. */
class AuraAiEngine(
    local: LocalIntentEngine,
    private val memory: CompactAssistantMemory,
    private val localAssistant: LocalAssistantEngine = LocalAssistantEngine(local)
) {
    private val localProvider: ReasoningProvider = LocalReasoningProvider(localAssistant)

    suspend fun respond(input: String, context: AuraAiContext): AssistantReply {
        val memorySnapshot = memory.snapshot()
        val normalized = TextNormalizer.normalize(input)
        val assistantContext = context.toAssistantContext(memorySnapshot)
        val localDecision = localProvider.reason(
            AssistantRequest(
                input,
                normalized.normalizedText,
                normalized.detectedLanguage,
                recentTurns = context.recentTurns
            ),
            assistantContext
        )
        val selectedDecision = localDecision
        val selectedNeedsReasoning = selectedDecision.isUnresolved || selectedDecision.action == MusicIntent.Unknown
        val selectedReply = AssistantReply(
            intent = selectedDecision.action ?: MusicIntent.Unknown,
            text = selectedDecision.reply,
            language = selectedDecision.language,
            route = when {
                selectedDecision.action != null && selectedDecision.action != MusicIntent.Unknown -> AssistantRoute.LOCAL_ACTION
                selectedNeedsReasoning -> AssistantRoute.NEEDS_REASONING
                else -> AssistantRoute.LOCAL_CONVERSATION
            },
            memoryInsights = selectedDecision.memoryInsights,
            source = AssistantSource.LOCAL,
            diagnostics = selectedDecision.diagnostics
        )
        val finalReply = if (selectedNeedsReasoning) {
            selectedReply.copy(
                text = fallbackText(selectedReply.language),
                route = AssistantRoute.LOCAL_CONVERSATION,
                source = AssistantSource.FALLBACK
            )
        } else selectedReply
        memory.record(input, finalReply)
        return finalReply
    }

    suspend fun memorySnapshot(): AssistantMemorySnapshot = memory.snapshot()
    suspend fun clearMemory() = memory.clear()

    private fun fallbackText(language: AssistantLanguage): String = when (language) {
        AssistantLanguage.RUSSIAN -> "Я пока не знаю ответа на это локально и не буду придумывать."
        AssistantLanguage.AZERBAIJANI -> "Buna hələ yerli cavabım yoxdur və cavabı uydurmayacağam."
        AssistantLanguage.ENGLISH -> "I don't have a local answer for that yet, and I won't make one up."
    }

    private fun AuraAiContext.toAssistantContext(memorySnapshot: AssistantMemorySnapshot) = AssistantContext(
        currentTrack = currentTrack?.let { AssistantTrackContext("current", it, currentArtist.orEmpty()) },
        isPlaying = isPlaying,
        queue = if (queue.isNotEmpty()) queue else List(queueSize) { AssistantTrackContext("queue:$it", "", "") },
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
