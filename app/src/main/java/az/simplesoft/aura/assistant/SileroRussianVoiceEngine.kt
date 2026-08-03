package az.simplesoft.aura.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Offline Russian female voice: Silero V1 "Kseniya" at 16 kHz.
 * The pack is downloaded once into private app storage and checksum-verified.
 */
internal class SileroRussianVoiceEngine(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
) {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aura-silero-ru-tts").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utterance = AtomicLong(0L)
    private val audioLock = Any()

    @Volatile private var module: Module? = null
    @Volatile private var currentAudio: AudioTrack? = null

    fun speak(text: String, onFailure: () -> Unit) {
        val safeText = text.trim().take(MAX_TEXT_CHARS)
        if (safeText.isBlank()) return
        val requestId = utterance.incrementAndGet()
        worker.execute {
            runCatching {
                val tokenIds = SileroRussianTokenizer.encode(safeText)
                require(tokenIds.size > 2) { "Text has no supported Russian symbols" }
                val model = cachedVerifiedModel() ?: error("Russian voice pack is not installed")
                val loaded = module ?: Module.load(model.absolutePath).also { module = it }
                val textTensor = Tensor.fromBlob(tokenIds, longArrayOf(1, tokenIds.size.toLong()))
                // Silero V1 single-speaker JIT exposes one text-tensor input.
                val output = loaded.forward(IValue.from(textTensor))
                val audio = output.toTuple().first().toTensor().dataAsFloatArray
                require(audio.isNotEmpty()) { "Silero returned empty audio" }
                Log.i(TAG, "Russian female voice generated ${audio.size} samples")
                if (utterance.get() == requestId) play(audio, requestId)
            }.onFailure { error ->
                Log.w(TAG, "Russian Silero voice failed; using Android TTS", error)
                if (utterance.get() == requestId) mainHandler.post {
                    if (utterance.get() == requestId) onFailure()
                }
            }
        }
    }

    fun stop() {
        utterance.incrementAndGet()
        synchronized(audioLock) {
            currentAudio?.let { track ->
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.stop() }
                runCatching { track.release() }
            }
            currentAudio = null
        }
    }

    fun prepare(onComplete: (Throwable?) -> Unit = {}) {
        worker.execute {
            val error = runCatching { ensureModel() }.exceptionOrNull()
            mainHandler.post { onComplete(error) }
        }
    }

    fun shutdown() {
        stop()
        worker.shutdownNow()
        runCatching { module?.destroy() }
        module = null
    }

    private fun ensureModel(): File {
        cachedVerifiedModel()?.let { return it }
        val directory = File(appContext.filesDir, "voice/silero-ru-kseniya")
        check(directory.exists() || directory.mkdirs()) { "Cannot create Russian voice directory" }
        val model = File(directory, MODEL_FILE_NAME)
        val verified = File(directory, "$MODEL_FILE_NAME.sha256")
        val partial = File(directory, "$MODEL_FILE_NAME.part")
        runCatching { partial.delete() }
        Log.i(TAG, "Downloading local Russian female voice pack")
        client.newCall(Request.Builder().url(MODEL_URL).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Silero download HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Silero download returned no body" }
            partial.outputStream().buffered().use { output -> body.byteStream().use { it.copyTo(output) } }
        }
        check(partial.length() == MODEL_SIZE_BYTES) { "Unexpected Silero model size: ${partial.length()}" }
        check(partial.sha256() == MODEL_SHA256) { "Silero model checksum mismatch" }
        Files.move(partial.toPath(), model.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        verified.writeText(MODEL_SHA256)
        return model
    }

    private fun cachedVerifiedModel(): File? {
        val directory = File(appContext.filesDir, "voice/silero-ru-kseniya")
        val model = File(directory, MODEL_FILE_NAME)
        val verified = File(directory, "$MODEL_FILE_NAME.sha256")
        return model.takeIf { it.isFile && it.length() == MODEL_SIZE_BYTES && verified.readTextOrEmpty() == MODEL_SHA256 }
    }

    private fun play(samples: FloatArray, requestId: Long) {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minBuffer > 0) { "PCM float playback is unavailable" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(minBuffer, samples.size * Float.SIZE_BYTES))
            .build()
        synchronized(audioLock) {
            if (utterance.get() != requestId) {
                track.release()
                return
            }
            currentAudio = track
        }
        try {
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == samples.size) { "AudioTrack wrote $written of ${samples.size} samples" }
            track.notificationMarkerPosition = samples.size - 1
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(completed: AudioTrack) {
                    synchronized(audioLock) { if (currentAudio === completed) currentAudio = null }
                    runCatching { completed.stop() }
                    completed.release()
                }
                override fun onPeriodicNotification(track: AudioTrack) = Unit
            }, mainHandler)
            track.play()
        } catch (error: Throwable) {
            synchronized(audioLock) { if (currentAudio === track) currentAudio = null }
            runCatching { track.release() }
            throw error
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.readTextOrEmpty(): String = runCatching { readText().trim() }.getOrDefault("")

    private companion object {
        const val TAG = "AuraSileroRu"
        const val MODEL_URL = "https://models.silero.ai/models/tts/ru/v1_kseniya_16000.jit"
        const val MODEL_FILE_NAME = "v1_kseniya_16000.jit"
        const val MODEL_SIZE_BYTES = 142_264_026L
        const val MODEL_SHA256 = "3d5359561e10dc27e9fe031197872f35857085fcfd1d1e36aaa624b01b3aa74f"
        const val SAMPLE_RATE = 16_000
        const val MAX_TEXT_CHARS = 500
    }
}

internal object SileroRussianTokenizer {
    private const val SYMBOLS = "_~абвгдеёжзийклмнопрстуфхцчшщъыьэюя +.,!?…:;–"
    private val symbolIds = SYMBOLS.withIndex().associate { it.value to it.index.toLong() }

    fun encode(text: String): LongArray {
        val body = text.asSequence().map { it.lowercaseChar() }.mapNotNull(symbolIds::get).toList()
        return longArrayOf(symbolIds.getValue('_')) + body.toLongArray() + longArrayOf(symbolIds.getValue('~'))
    }
}
