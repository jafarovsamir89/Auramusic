package az.simplesoft.aura.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Keeps one AudioRecord open while waiting for speech. This prevents the
 * system recognizer from repeatedly opening/closing the microphone on silence.
 */
internal class WakeWordAudioGate(private val context: Context) {
    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun start(scope: CoroutineScope, onSpeech: () -> Unit, onFailure: (Throwable) -> Unit = {}) {
        if (job != null) return
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required"
        }
        val bytesPerChunk = SAMPLE_RATE * 2 * CHUNK_MS / 1000
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(bytesPerChunk * 4)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Wake-word microphone could not be initialized" }
        recorder = record
        job = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bytesPerChunk / 2)
            val vad = WakeWordVad()
            var delivered = false
            var speechDetected = false
            try {
                record.startRecording()
                while (isActive && !delivered) {
                    val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) continue
                    val rms = rms(buffer, count)
                    if (vad.onRms(rms, System.currentTimeMillis())) {
                        delivered = true
                        speechDetected = true
                        stopRecorder(record)
                    }
                }
            } catch (error: Throwable) {
                if (isActive) onFailure(error)
            } finally {
                stopRecorder(record)
                recorder = null
                job = null
            }
            // Do this only after the recorder and the gate job are fully released.
            // Otherwise the next beginListening() can observe a stale job and never
            // re-arm the wake-word loop after the first detected speech segment.
            if (speechDetected) onSpeech()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder?.let(::stopRecorder)
        recorder = null
    }

    private fun stopRecorder(record: AudioRecord) {
        runCatching { record.stop() }
        runCatching { record.release() }
    }

    private fun rms(buffer: ShortArray, count: Int): Float {
        var sum = 0.0
        for (index in 0 until count) {
            val sample = buffer[index] / Short.MAX_VALUE.toDouble()
            sum += sample * sample
        }
        return (sqrt(sum / count.coerceAtLeast(1)) * Short.MAX_VALUE).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_MS = 20
    }
}
