package az.simplesoft.aura.assistant.gemini

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GeminiAudioInput(private val context: Context) {
    private var job: Job? = null
    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    fun start(
        scope: CoroutineScope,
        onChunk: (ByteArray) -> Unit,
        onState: (Boolean) -> Unit,
        onSpeechEnd: () -> Unit = {},
        onSpeechStart: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        if (job != null) return
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required"
        }
        val bytesPerChunk = GeminiLiveConfig.INPUT_RATE * 2 * GeminiLiveConfig.INPUT_CHUNK_MS / 1000
        val min = AudioRecord.getMinBufferSize(
            GeminiLiveConfig.INPUT_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(bytesPerChunk * 4)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(GeminiLiveConfig.INPUT_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(min)
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
        }
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(record.audioSessionId)?.also { it.enabled = true }
        }
        recorder = record
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bytesPerChunk)
            var silentMs = 0
            var speaking = false
            var speechStartedAt = 0L
            var noiseFloor = 180.0
            try {
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start recording" }
                onState(true)
                while (isActive) {
                    val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count > 0) {
                        val chunk = buffer.copyOf(count)
                        var energy = 0.0
                        var index = 0
                        while (index + 1 < chunk.size) {
                            val sample = ((chunk[index + 1].toInt() shl 8) or (chunk[index].toInt() and 0xff)).toShort().toInt()
                            energy += sample * sample.toDouble()
                            index += 2
                        }
                        val rms = kotlin.math.sqrt(energy / (chunk.size / 2).coerceAtLeast(1))
                        // Calibrate against the first chunks instead of using one
                        // device-specific magic value. A minimum gate prevents
                        // quiet handset noise from opening a turn by itself.
                        val threshold = maxOf(650.0, noiseFloor * 2.4)
                        if (!speaking) noiseFloor = (noiseFloor * 0.95) + (rms * 0.05)
                        if (rms > threshold) {
                            if (!speaking) onSpeechStart()
                            speaking = true
                            if (speechStartedAt == 0L) speechStartedAt = System.currentTimeMillis()
                            silentMs = 0
                        } else if (speaking) {
                            silentMs += GeminiLiveConfig.INPUT_CHUNK_MS
                            val utteranceMs = System.currentTimeMillis() - speechStartedAt
                            if (silentMs >= SILENCE_END_MS || utteranceMs >= MAX_UTTERANCE_MS) {
                                speaking = false
                                speechStartedAt = 0L
                                silentMs = 0
                                onSpeechEnd()
                            }
                        }
                        onChunk(chunk)
                    }
                }
            } catch (error: Throwable) {
                if (isActive) onError(error)
            } finally {
                onState(false)
                runCatching { record.stop() }
                runCatching { record.release() }
                runCatching { echoCanceler?.release() }
                runCatching { noiseSuppressor?.release() }
                runCatching { automaticGainControl?.release() }
                echoCanceler = null
                noiseSuppressor = null
                automaticGainControl = null
                recorder = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { recorder?.stop() }
    }

    private companion object {
        const val SILENCE_END_MS = 500
        const val MAX_UTTERANCE_MS = 20_000L
    }
}
