package az.simplesoft.aura.assistant

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

data class VoiceCaptureDiagnostics(
    val averageRms: Float,
    val peakRms: Float,
    val captureDurationMs: Long,
    val heardSpeech: Boolean,
    val sampleCount: Int,
    val backend: String,
    val transcriptionDurationMs: Long? = null
)

/**
 * Records one utterance locally and transcribes it with Whisper. The simple
 * endpoint detector keeps capture bounded for slower phones; it never sends
 * microphone data to a server.
 */
internal class WhisperSpeechRecognizer(
    context: Context,
    private val onCommand: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
    private val onNoSpeech: (String) -> Unit = {},
    private val onDiagnostics: (VoiceCaptureDiagnostics) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val engine = WhisperCppEngine(context)
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "aura-whisper-asr") }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recording = AtomicBoolean(false)
    @Volatile private var lastCaptureDiagnostics: VoiceCaptureDiagnostics? = null

    fun start() {
        if (!recording.compareAndSet(false, true)) return
        worker.execute {
            runCatching {
                Log.i(TAG, "whisper capture started")
                val pcm = captureUtterance()
                Log.i(TAG, "whisper capture finished: samples=${pcm.size}")
                val transcriptionStarted = System.currentTimeMillis()
                val transcript = engine.transcribe(pcm)
                Log.i(TAG, "whisper transcription finished: ms=${System.currentTimeMillis() - transcriptionStarted}, chars=${transcript.length}")
                lastCaptureDiagnostics?.copy(
                    transcriptionDurationMs = System.currentTimeMillis() - transcriptionStarted,
                    sampleCount = pcm.size
                )?.let(onDiagnostics)
                transcript
            }.onSuccess { transcript ->
                recording.set(false)
                mainHandler.post {
                    onState(false)
                    if (transcript.trim().isBlank()) onNoSpeech("Не услышала речь.") else onCommand(transcript)
                }
            }.onFailure { error ->
                Log.w(TAG, "whisper pipeline failed", error)
                recording.set(false)
                mainHandler.post {
                    onState(false)
                    if (error.message == "Speech was not detected") onNoSpeech("Не услышала речь.") else onFailure(error)
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

    @SuppressLint("MissingPermission")
    private fun captureUtterance(): FloatArray {
        check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required for offline recognition"
        }
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
        var speechStartedAt = 0L
        var sumSquares = 0.0
        var sampleCount = 0
        var readBlocks = 0
        var peakRms = 0f
        var noiseSum = 0.0
        var noiseSamples = 0
        try {
            recorder.startRecording()
            mainHandler.post { onState(true) }
            while (recording.get()) {
                val count = recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                for (index in 0 until count) captured += shortBuffer[index]
                val rms = rms(shortBuffer, count)
                val now = System.currentTimeMillis()
                sumSquares += rms.toDouble() * rms
                sampleCount += count
                readBlocks++
                peakRms = maxOf(peakRms, rms)
                if (now - startedAt <= NOISE_CALIBRATION_MS) {
                    noiseSum += rms
                    noiseSamples++
                }
                val noiseFloor = if (noiseSamples == 0) MINIMUM_THRESHOLD else (noiseSum / noiseSamples).toFloat()
                val speechThreshold = maxOf(MINIMUM_THRESHOLD, noiseFloor * NOISE_MULTIPLIER)
                if (rms >= speechThreshold) {
                    if (speechStartedAt == 0L) speechStartedAt = now
                }
                if (speechStartedAt != 0L && now - speechStartedAt >= MIN_SPEECH_MS) {
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
        lastCaptureDiagnostics = VoiceCaptureDiagnostics(
            averageRms = if (readBlocks == 0) 0f else sqrt(sumSquares / readBlocks).toFloat(),
            peakRms = peakRms,
            captureDurationMs = System.currentTimeMillis() - startedAt,
            heardSpeech = heardSpeech,
            sampleCount = sampleCount,
            backend = "Whisper"
        )
        lastCaptureDiagnostics?.let(onDiagnostics)
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
        private const val TAG = "AuraVoiceDiag"
        private const val SAMPLE_RATE = 16_000
        private const val MINIMUM_THRESHOLD = 0.008f
        private const val NOISE_MULTIPLIER = 2.5f
        private const val NOISE_CALIBRATION_MS = 400L
        private const val MIN_SPEECH_MS = 280L
        private const val WAIT_FOR_SPEECH_MS = 6_000L
        private const val END_SILENCE_MS = 1_000L
        private const val MAX_CAPTURE_MS = 16_000L
    }
}
