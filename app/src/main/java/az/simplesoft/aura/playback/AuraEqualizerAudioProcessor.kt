package az.simplesoft.aura.playback

import androidx.media3.common.C
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import androidx.media3.common.util.UnstableApi

/**
 * A real PCM EQ in the Media3 audio pipeline. Android's legacy AudioEffect API
 * is device-dependent and may silently attach to a different session; this
 * processor changes the samples that Aura actually sends to the speaker.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AuraEqualizerAudioProcessor : BaseAudioProcessor() {
    @Volatile private var requestedPreset = "flat"
    @Volatile private var requestedGains = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    @Volatile private var requestedVersion = 0L
    private var sampleRate = 44_100
    private var channelCount = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var activePreset = "flat"
    private var activeVersion = -1L
    private var filters = emptyArray<PeakingFilter>()

    fun setPreset(preset: String) {
        requestedPreset = preset.lowercase()
        requestedGains = gainsForPreset(requestedPreset)
        requestedVersion++
    }

    fun setBands(bands: FloatArray) {
        requestedPreset = "custom"
        requestedGains = bands.copyOf(BAND_COUNT).map { it.coerceIn(-12f, 12f) }.toFloatArray()
        requestedVersion++
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException("Aura EQ supports PCM 16-bit and float", inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        encoding = inputAudioFormat.encoding
        rebuildFilters(requestedPreset, requestedGains)
        activeVersion = requestedVersion
        return inputAudioFormat
    }

    override fun isActive(): Boolean = super.isActive()

    override fun onFlush() {
        filters.forEach(PeakingFilter::reset)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (activeVersion != requestedVersion) {
            rebuildFilters(requestedPreset, requestedGains)
            activeVersion = requestedVersion
        }
        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val sampleCount = input.remaining() / bytesPerSample
        val output = replaceOutputBuffer(sampleCount * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val channel = index % channelCount
            val sample = if (encoding == C.ENCODING_PCM_FLOAT) input.float else input.short.toFloat() / Short.MAX_VALUE
            val processed = filters.fold(sample) { value, filter -> filter.process(value, channel) }
                .coerceIn(-1f, 1f)
            if (encoding == C.ENCODING_PCM_FLOAT) output.putFloat(processed)
            else output.putShort((processed * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        output.flip()
    }

    private fun rebuildFilters(preset: String, requestedGains: FloatArray) {
        activePreset = preset
        val gains = requestedGains.copyOf(BAND_COUNT)
        val frequencies = floatArrayOf(60f, 230f, 910f, 3_600f, 12_000f)
        filters = frequencies.mapIndexed { index, frequency ->
            PeakingFilter(sampleRate, frequency, 1.0f, gains[index], channelCount)
        }.toTypedArray()
    }

    private fun gainsForPreset(preset: String): FloatArray = when (preset) {
        "bass" -> floatArrayOf(7f, 4f, 1f, -1f, -2f)
        "vocal" -> floatArrayOf(-2f, 1f, 4f, 3f, 0f)
        "rock" -> floatArrayOf(5f, 2f, -1f, 3f, 5f)
        "acoustic" -> floatArrayOf(2f, 3f, 2f, 1f, -1f)
        else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
    }

    private class PeakingFilter(
        sampleRate: Int,
        frequency: Float,
        q: Float,
        gainDb: Float,
        channels: Int
    ) {
        private val b0: Float
        private val b1: Float
        private val b2: Float
        private val a1: Float
        private val a2: Float
        private val x1 = FloatArray(channels)
        private val x2 = FloatArray(channels)
        private val y1 = FloatArray(channels)
        private val y2 = FloatArray(channels)

        init {
            val safeFrequency = frequency.coerceAtMost(sampleRate * .45f)
            val omega = (2.0 * PI * safeFrequency / sampleRate).toFloat()
            val alpha = sin(omega) / (2f * q)
            val a = 10f.pow(gainDb / 40f)
            val a0 = 1f + alpha / a
            b0 = (1f + alpha * a) / a0
            b1 = (-2f * cos(omega)) / a0
            b2 = (1f - alpha * a) / a0
            a1 = (-2f * cos(omega)) / a0
            a2 = (1f - alpha / a) / a0
        }

        fun process(input: Float, channel: Int): Float {
            val output = b0 * input + b1 * x1[channel] + b2 * x2[channel] - a1 * y1[channel] - a2 * y2[channel]
            x2[channel] = x1[channel]
            x1[channel] = input
            y2[channel] = y1[channel]
            y1[channel] = output
            return output
        }

        fun reset() {
            x1.fill(0f)
            x2.fill(0f)
            y1.fill(0f)
            y2.fill(0f)
        }
    }

    private companion object {
        const val BAND_COUNT = 5
    }
}
