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

    fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onState: (Boolean) -> Unit, onSpeechEnd: () -> Unit = {}, onSpeechStart: () -> Unit = {}) {
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
            record.startRecording()
            onState(true)
            try {
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
                        if (rms > 700.0) {
                            if (!speaking) onSpeechStart()
                            speaking = true
                            silentMs = 0
                        } else if (speaking) {
                            silentMs += GeminiLiveConfig.INPUT_CHUNK_MS
                            if (silentMs >= 400) {
                                speaking = false
                                onSpeechEnd()
                            }
                        }
                        onChunk(chunk)
                    }
                }
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
}
