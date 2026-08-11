package az.simplesoft.aura.assistant

/** Optional future provider. It is never constructed by the default ViewModel. */
class OpenRouterReasoningProvider(
    private val adapter: OpenRouterAssistantAdapter
) : ReasoningProvider {
    override val id: String = "openrouter"
    override val isAvailable: Boolean get() = adapter.isAvailable

    override suspend fun reason(request: AssistantRequest, context: AssistantContext): AssistantDecision {
        val reply = adapter.reason(
            input = request.originalText,
            context = context.toAuraContext(),
            memory = AssistantMemorySnapshot(facts = context.memoryFacts),
            language = request.language
        )
        val action = reply.intent.takeIf { it != MusicIntent.Unknown }
        val entities = action?.let { intentEntities(it) }.orEmpty()
        return AssistantDecision(
            intentId = action?.let { it::class.simpleName } ?: "UNRESOLVED",
            confidence = if (action == null) 0.0 else 0.9,
            entities = entities,
            language = reply.language,
            reply = reply.text,
            action = action,
            memoryInsights = reply.memoryInsights,
            diagnostics = DecisionDiagnostics(
                originalText = request.originalText,
                normalizedText = request.normalizedText,
                language = reply.language,
                selectedIntent = action?.let { it::class.simpleName },
                confidence = if (action == null) 0.0 else 0.9,
                entities = entities,
                reason = "remote-provider:$id"
            )
        )
    }

    private fun AssistantContext.toAuraContext() = AuraAiContext(
        currentTrack = currentTrack?.title,
        currentArtist = currentTrack?.artist,
        isPlaying = isPlaying,
        hourOfDay = hourOfDay,
        carMode = carMode,
        queue = queue,
        currentIndex = currentIndex,
        lastSearchQuery = lastSearchQuery,
        lastSearchResults = lastSearchResults,
        playlists = playlists,
        favoriteCount = favoriteCount,
        currentPlaylist = currentPlaylist,
        currentTrackLiked = currentTrackLiked,
        lastIntent = lastIntent
    )

    private fun intentEntities(intent: MusicIntent): List<AssistantEntity> = when (intent) {
        is MusicIntent.Search -> buildList {
            intent.artist?.let { add(AssistantEntity(AssistantEntityType.ARTIST, it)) }
            add(AssistantEntity(AssistantEntityType.TRACK, intent.query))
        }
        is MusicIntent.PlayPlaylist -> listOf(AssistantEntity(AssistantEntityType.PLAYLIST, intent.name))
        is MusicIntent.QueueTrack -> listOf(AssistantEntity(AssistantEntityType.TRACK, intent.query))
        else -> emptyList()
    }
}
