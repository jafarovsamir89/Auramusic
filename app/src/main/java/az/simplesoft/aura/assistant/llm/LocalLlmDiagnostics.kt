package az.simplesoft.aura.assistant.llm

/** Last local-Brain run, intentionally bounded to values safe for the debug screen. */
data class LocalLlmDiagnostics(
    val model: String,
    val quantization: String,
    val threads: Int,
    val ramBeforeMb: Long,
    val ramAfterMb: Long,
    val loadMs: Long,
    val promptTokens: Int,
    val outputTokens: Int,
    val tokensPerSecond: Double,
    val timeToFirstTokenMs: Long,
    val totalMs: Long,
    val route: String
)
