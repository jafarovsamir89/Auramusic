package az.simplesoft.aura.assistant

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Records one utterance locally and transcribes it with Whisper. The simple
 * endpoint detector keeps capture bounded for slower phones; it never sends
 * microphone data to a server.
 */
internal class WhisperSpeechRecognizer(
    context: Context,
    private val onCommand: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onFailure: (Throwable) -> Unit = {}
) {
    private val engine = WhisperCppEngine(context)
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "aura-whisper-asr") }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recording = AtomicBoolean(false)

    fun start() {
        if (!recording.compareAndSet(false, true)) return
        worker.execute {
            runCatching {
                val pcm = captureUtterance()
                engine.transcribe(pcm)
            }.onSuccess { transcript ->
                recording.set(false)
                mainHandler.post {
                    onState(false)
                    transcript.trim().takeIf(String::isNotBlank)?.let(onCommand)
                }
            }.onFailure { error ->
                recording.set(false)
                mainHandler.post {
                    onState(false)
                    onFailure(error)
                }
            }
        }
    }

    fun stop() {
        recording.set(false)
    }

    fun destroy() {
        stop()
        worker.shutdownNow()
        engine.close()
    }

    private fun captureUtterance(): FloatArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "Microphone PCM capture is unavailable" }
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
        val shortBuffer = ShortArray(minBuffer / 2)
        val captured = ArrayList<Short>(SAMPLE_RATE * 4)
        var heardSpeech = false
        val startedAt = System.currentTimeMillis()
        var lastSpeechAt = startedAt
        try {
            recorder.startRecording()
            mainHandler.post { onState(true) }
            while (recording.get()) {
                val count = recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                for (index in 0 until count) captured += shortBuffer[index]
                val rms = rms(shortBuffer, count)
                val now = System.currentTimeMillis()
                if (rms >= SPEECH_RMS_THRESHOLD) {
                    heardSpeech = true
                    lastSpeechAt = now
                }
                if (!heardSpeech && now - startedAt >= WAIT_FOR_SPEECH_MS) break
                if (heardSpeech && now - lastSpeechAt >= END_SILENCE_MS) break
                if (now - startedAt >= MAX_CAPTURE_MS) break
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        check(heardSpeech) { "Speech was not detected" }
        return FloatArray(captured.size) { index -> captured[index] / Short.MAX_VALUE.toFloat() }
    }

    private fun rms(values: ShortArray, count: Int): Float {
        var sum = 0.0
        for (index in 0 until count) {
            val sample = values[index] / Short.MAX_VALUE.toDouble()
            sum += sample * sample
        }
        return sqrt(sum / count).toFloat()
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val SPEECH_RMS_THRESHOLD = 0.015f
        private const val WAIT_FOR_SPEECH_MS = 6_000L
        private const val END_SILENCE_MS = 1_000L
        private const val MAX_CAPTURE_MS = 16_000L
    }
}
