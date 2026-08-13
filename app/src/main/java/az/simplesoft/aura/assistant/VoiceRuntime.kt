package az.simplesoft.aura.assistant

enum class VoiceSessionState {
    IDLE, WAKE_DETECTED, LISTENING, TRANSCRIBING, UNDERSTANDING, EXECUTING, SPEAKING, FOLLOW_UP_LISTENING
}

data class VoicePerformanceMetrics(
    val wakeWordDetectionMs: Long? = null,
    val speechStartMs: Long? = null,
    val speechEndMs: Long? = null,
    val asrDurationMs: Long? = null,
    val assistantDecisionMs: Long? = null,
    val actionExecutionMs: Long? = null,
    val ttsGenerationMs: Long? = null,
    val ttsFirstAudioMs: Long? = null,
    val fullTurnLatencyMs: Long? = null,
    val bargeInCount: Int = 0
)

/** Pure state guard used by UI/service integrations to prevent speaking over listening. */
class VoiceTurnStateMachine {
    var state: VoiceSessionState = VoiceSessionState.IDLE
        private set
    var metrics: VoicePerformanceMetrics = VoicePerformanceMetrics()
        private set

    fun transition(next: VoiceSessionState) {
        require(isAllowed(state, next)) { "Invalid voice transition: $state -> $next" }
        state = next
    }

    fun bargeIn() {
        if (state == VoiceSessionState.SPEAKING) {
            metrics = metrics.copy(bargeInCount = metrics.bargeInCount + 1)
            state = VoiceSessionState.LISTENING
        }
    }

    fun reset() {
        state = VoiceSessionState.IDLE
        metrics = VoicePerformanceMetrics()
    }

    private fun isAllowed(from: VoiceSessionState, to: VoiceSessionState): Boolean = when (from) {
        VoiceSessionState.IDLE -> to in setOf(VoiceSessionState.WAKE_DETECTED, VoiceSessionState.LISTENING)
        VoiceSessionState.WAKE_DETECTED -> to == VoiceSessionState.LISTENING || to == VoiceSessionState.IDLE
        VoiceSessionState.LISTENING -> to in setOf(VoiceSessionState.TRANSCRIBING, VoiceSessionState.IDLE)
        VoiceSessionState.TRANSCRIBING -> to in setOf(VoiceSessionState.UNDERSTANDING, VoiceSessionState.IDLE)
        VoiceSessionState.UNDERSTANDING -> to in setOf(VoiceSessionState.EXECUTING, VoiceSessionState.SPEAKING, VoiceSessionState.IDLE)
        VoiceSessionState.EXECUTING -> to in setOf(VoiceSessionState.SPEAKING, VoiceSessionState.FOLLOW_UP_LISTENING, VoiceSessionState.IDLE)
        VoiceSessionState.SPEAKING -> to in setOf(VoiceSessionState.FOLLOW_UP_LISTENING, VoiceSessionState.LISTENING, VoiceSessionState.IDLE)
        VoiceSessionState.FOLLOW_UP_LISTENING -> to in setOf(VoiceSessionState.LISTENING, VoiceSessionState.IDLE)
    }
}
