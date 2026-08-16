package az.simplesoft.aura.assistant.gemini

enum class GeminiSessionState {
    DISCONNECTED,
    CONNECTING,
    READY,
    USER_SPEAKING,
    MODEL_THINKING,
    MODEL_SPEAKING,
    TOOL_EXECUTING,
    RECONNECTING,
    ERROR
}

data class GeminiTokenUsage(
    val promptTokens: Long = 0,
    val responseTokens: Long = 0,
    val totalTokens: Long = 0,
    val thoughtsTokens: Long = 0,
    val cachedTokens: Long = 0,
    val toolUsePromptTokens: Long = 0,
    val inputAudioTokens: Long = 0,
    val inputTextTokens: Long = 0,
    val outputAudioTokens: Long = 0,
    val outputTextTokens: Long = 0
) {
    operator fun plus(other: GeminiTokenUsage) = copy(
        promptTokens = promptTokens + other.promptTokens,
        responseTokens = responseTokens + other.responseTokens,
        totalTokens = totalTokens + other.totalTokens,
        thoughtsTokens = thoughtsTokens + other.thoughtsTokens,
        cachedTokens = cachedTokens + other.cachedTokens,
        toolUsePromptTokens = toolUsePromptTokens + other.toolUsePromptTokens,
        inputAudioTokens = inputAudioTokens + other.inputAudioTokens,
        inputTextTokens = inputTextTokens + other.inputTextTokens,
        outputAudioTokens = outputAudioTokens + other.outputAudioTokens,
        outputTextTokens = outputTextTokens + other.outputTextTokens
    )

    val estimatedCostUsd: Double
        get() = (inputTextTokens * 0.75 + inputAudioTokens * 3.0 + outputTextTokens * 4.50 + outputAudioTokens * 12.0) / 1_000_000.0
}

data class GeminiDiagnostics(
    val model: String = GeminiLiveConfig.MODEL,
    val state: GeminiSessionState = GeminiSessionState.DISCONNECTED,
    val connected: Boolean = false,
    val sessionId: String? = null,
    val resumeAvailable: Boolean = false,
    val inputTranscription: String = "",
    val outputTranscription: String = "",
    val voice: String = GeminiLiveConfig.DEFAULT_VOICE,
    val thinkingLevel: String = "minimal",
    val lastTool: String? = null,
    val lastToolResult: String? = null,
    val speechEndMs: Long? = null,
    val firstAudioMs: Long? = null,
    val speechEndToFirstAudioMs: Long? = null,
    val connectMs: Long? = null,
    val setupMs: Long? = null,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val reconnectCount: Int = 0,
    val interruptionCount: Int = 0,
    val lastUsage: GeminiTokenUsage = GeminiTokenUsage(),
    val sessionUsage: GeminiTokenUsage = GeminiTokenUsage(),
    val lifetimeUsage: GeminiTokenUsage = GeminiTokenUsage(),
    val lastError: String? = null
)
