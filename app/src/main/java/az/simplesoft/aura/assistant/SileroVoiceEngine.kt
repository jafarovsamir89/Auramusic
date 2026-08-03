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

internal class SileroVoiceEngine(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
) {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aura-silero-tts").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utterance = AtomicLong(0L)
    private val audioLock = Any()

    @Volatile private var module: Module? = null
    @Volatile private var currentAudio: AudioTrack? = null

    init {
        worker.execute {
            cachedVerifiedModel()?.let { model ->
                runCatching { Module.load(model.absolutePath) }
                    .onSuccess {
                        module = it
                        Log.i(TAG, "Silero voice preloaded")
                    }
                    .onFailure { Log.w(TAG, "Silero preload failed; lazy retry will be used", it) }
            }
        }
    }

    fun speak(text: String, onFailure: () -> Unit) {
        val safeText = text.trim().take(MAX_TEXT_CHARS)
        if (safeText.isBlank()) return
        val requestId = utterance.incrementAndGet()
        Log.i(TAG, "Silero request queued (${safeText.length} chars)")
        worker.execute {
            runCatching {
                val startedAt = System.currentTimeMillis()
                val tokenIds = SileroAzerbaijaniTokenizer.encode(safeText)
                require(tokenIds.size > 2) { "Text has no supported Azerbaijani symbols" }
                val model = cachedVerifiedModel() ?: error("Azerbaijani voice pack is not installed")
                val loaded = module ?: Module.load(model.absolutePath).also { module = it }
                val textTensor = Tensor.fromBlob(tokenIds, longArrayOf(1, tokenIds.size.toLong()))
                val speakerTensor = Tensor.fromBlob(longArrayOf(AZERBAIJANI_SPEAKER_ID), longArrayOf(1))
                val audio = loaded.forward(IValue.from(textTensor), IValue.from(speakerTensor))
                    .toTensor()
                    .dataAsFloatArray
                require(audio.isNotEmpty()) { "Silero returned empty audio" }
                Log.i(TAG, "Silero generated ${audio.size} samples in ${System.currentTimeMillis() - startedAt} ms")
                if (utterance.get() == requestId) play(audio, requestId)
            }.onFailure { error ->
                Log.w(TAG, "Local Silero voice failed; using Android TTS", error)
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
        val directory = voiceDirectory()
        check(directory.exists() || directory.mkdirs()) { "Cannot create Silero voice directory" }
        val model = File(directory, MODEL_FILE_NAME)
        val verified = File(directory, "$MODEL_FILE_NAME.sha256")
        if (model.isFile && model.length() == MODEL_SIZE_BYTES) {
            if (verified.readTextOrEmpty() == MODEL_SHA256) return model
            if (model.sha256() == MODEL_SHA256) {
                verified.writeText(MODEL_SHA256)
                return model
            }
        }
        if (model.exists()) model.delete()
        if (verified.exists()) verified.delete()

        val partial = File(directory, "$MODEL_FILE_NAME.part")
        if (partial.exists()) partial.delete()
        Log.i(TAG, "Downloading local Azerbaijani voice pack")
        val request = Request.Builder().url(MODEL_URL).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Silero download HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Silero download returned no body" }
            partial.outputStream().buffered().use { output -> body.byteStream().use { it.copyTo(output) } }
        }
        check(partial.length() == MODEL_SIZE_BYTES) { "Unexpected Silero model size: ${partial.length()}" }
        check(partial.sha256() == MODEL_SHA256) { "Silero model checksum mismatch" }
        Files.move(
            partial.toPath(),
            model.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        verified.writeText(MODEL_SHA256)
        Log.i(TAG, "Silero voice pack downloaded and verified")
        return model
    }

    private fun cachedVerifiedModel(): File? {
        val directory = voiceDirectory()
        val model = File(directory, MODEL_FILE_NAME)
        val verified = File(directory, "$MODEL_FILE_NAME.sha256")
        return model.takeIf {
            it.isFile && it.length() == MODEL_SIZE_BYTES && verified.readTextOrEmpty() == MODEL_SHA256
        }
    }

    private fun voiceDirectory() = File(appContext.filesDir, "voice/silero-v5-cis-base")

    private fun play(samples: FloatArray, requestId: Long) {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        check(minBuffer > 0) { "PCM float playback is unavailable" }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
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
                    synchronized(audioLock) {
                        if (currentAudio === completed) currentAudio = null
                    }
                    runCatching { completed.stop() }
                    completed.release()
                }

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            }, mainHandler)
            track.play()
        } catch (error: Throwable) {
            synchronized(audioLock) {
                if (currentAudio === track) currentAudio = null
            }
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

    companion object {
        private const val TAG = "AuraSilero"
        private const val MODEL_URL = "https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.jit"
        private const val MODEL_FILE_NAME = "v5_cis_base_nostress.jit"
        private const val MODEL_SIZE_BYTES = 91_695_221L
        private const val MODEL_SHA256 = "d7d361caf78b8480bcd65a0c367af665a2bf6f06c8507306e3781dc7c6ce781b"
        private const val SAMPLE_RATE = 48_000
        private const val AZERBAIJANI_SPEAKER_ID = 0L
        private const val MAX_TEXT_CHARS = 500
    }
}

internal object SileroAzerbaijaniTokenizer {
    private const val SYMBOLS = "|||!'+,-.:;?hабвгдежзийклмнопрстуфхцчшщъыьэюяёєіїјўґғҕҗҙқҝҡңҥҫүұҳҷҹһӑӗәӝӟӣӥӧөӯӱӳӵӏ—… "
    private val symbolIds = SYMBOLS.withIndex().associate { it.value to it.index.toLong() }
    private val latinToCyrillic = mapOf(
        'A' to 'А', 'a' to 'а', 'B' to 'Б', 'b' to 'б', 'C' to 'Ҹ', 'c' to 'ҹ',
        'Ç' to 'Ч', 'ç' to 'ч', 'D' to 'Д', 'd' to 'д', 'E' to 'Е', 'e' to 'е',
        'Ə' to 'Ә', 'ə' to 'ә', 'F' to 'Ф', 'f' to 'ф', 'G' to 'Ҝ', 'g' to 'ҝ',
        'Ğ' to 'Ғ', 'ğ' to 'ғ', 'H' to 'Һ', 'h' to 'һ', 'X' to 'Х', 'x' to 'х',
        'I' to 'Ы', 'ı' to 'ы', 'İ' to 'И', 'i' to 'и', 'J' to 'Ж', 'j' to 'ж',
        'K' to 'К', 'k' to 'к', 'Q' to 'Г', 'q' to 'г', 'L' to 'Л', 'l' to 'л',
        'M' to 'М', 'm' to 'м', 'N' to 'Н', 'n' to 'н', 'O' to 'О', 'o' to 'о',
        'Ö' to 'Ө', 'ö' to 'ө', 'P' to 'П', 'p' to 'п', 'R' to 'Р', 'r' to 'р',
        'S' to 'С', 's' to 'с', 'Ş' to 'Ш', 'ş' to 'ш', 'T' to 'Т', 't' to 'т',
        'U' to 'У', 'u' to 'у', 'Ü' to 'Ү', 'ü' to 'ү', 'V' to 'В', 'v' to 'в',
        'Y' to 'Ј', 'y' to 'ј', 'Z' to 'З', 'z' to 'з'
    )

    fun encode(text: String): LongArray {
        val body = text.asSequence()
            .map { latinToCyrillic[it] ?: it.lowercaseChar() }
            .mapNotNull(symbolIds::get)
            .toList()
        return longArrayOf(2L) + body.toLongArray() + longArrayOf(1L)
    }
}
