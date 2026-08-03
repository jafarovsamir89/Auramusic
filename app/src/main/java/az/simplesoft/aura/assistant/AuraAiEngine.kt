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

/** The single seam used by UI for local commands, dialogue, and memory. */
class AuraAiEngine(
    private val local: LocalIntentEngine,
    private val companion: LocalCompanionEngine = LocalCompanionEngine(),
    private val memory: CompactAssistantMemory,
    private val dataBackedCompanion: DataBackedCompanionEngine? = null
) {
    suspend fun respond(input: String, context: AuraAiContext): AssistantReply {
        val detectedLanguage = AssistantLanguage.detect(input)
        val localReply = local.understand(input)
        val finalReply = if (localReply.intent != MusicIntent.Unknown) localReply else {
            (dataBackedCompanion?.respond(input, detectedLanguage)
                ?: companion.respond(input, context, memory.snapshot(), detectedLanguage))
                .copy(memoryInsights = localReply.memoryInsights + companion.extractMemory(input))
        }
        memory.record(input, finalReply)
        return finalReply
    }

    suspend fun memorySnapshot(): AssistantMemorySnapshot = memory.snapshot()
    suspend fun clearMemory() = memory.clear()
}
