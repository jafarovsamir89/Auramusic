package az.simplesoft.aura.assistant

import kotlin.math.max

/** Pure speech-activity gate used before opening the one-shot Android recognizer. */
internal class WakeWordVad(
    private val minimumRms: Float = 1_100f,
    private val minimumSpeechMs: Long = 320L,
    private val calibrationMs: Long = 500L
) {
    private var startedAt = 0L
    private var speechStartedAt = 0L
    private var triggered = false
    private var noiseTotal = 0.0
    private var noiseSamples = 0

    fun onRms(rms: Float, nowMs: Long): Boolean {
        if (startedAt == 0L) startedAt = nowMs
        // Only quiet frames are suitable for the noise-floor estimate. A user
        // saying "Аура" immediately after enabling the service must not be
        // mistaken for background noise during calibration.
        if (nowMs - startedAt <= calibrationMs && rms < minimumRms) {
            noiseTotal += rms
            noiseSamples++
        }
        val noiseFloor = if (noiseSamples == 0) 0f else (noiseTotal / noiseSamples).toFloat()
        val threshold = max(minimumRms, noiseFloor * 2.2f)
        if (rms >= threshold) {
            if (speechStartedAt == 0L) speechStartedAt = nowMs
            if (!triggered && nowMs - speechStartedAt >= minimumSpeechMs) {
                triggered = true
                return true
            }
        } else {
            speechStartedAt = 0L
        }
        return false
    }

    fun reset() {
        startedAt = 0L
        speechStartedAt = 0L
        triggered = false
        noiseTotal = 0.0
        noiseSamples = 0
    }
}
