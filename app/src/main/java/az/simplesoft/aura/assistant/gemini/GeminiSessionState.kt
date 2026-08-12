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
    val lastError: String? = null
)
