package az.simplesoft.aura.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Small, multilingual, on-device Whisper.cpp runtime. The model is never bundled into the APK. */
internal class WhisperCppEngine(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var nativeHandle = 0L

    @Synchronized
    fun transcribe(samples: FloatArray): String {
        check(samples.isNotEmpty()) { "No audio was captured" }
        if (nativeHandle == 0L) nativeHandle = nativeCreate(ensureModel().absolutePath)
        return nativeTranscribe(nativeHandle, samples, "auto").trim()
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) nativeDestroy(nativeHandle)
        nativeHandle = 0L
    }

    private fun ensureModel(): File {
        val directory = File(appContext.filesDir, "models/whisper")
        check(directory.exists() || directory.mkdirs()) { "Cannot create Whisper model directory" }
        val model = File(directory, MODEL_FILE_NAME)
        if (model.isFile && model.length() == MODEL_SIZE_BYTES && model.sha256() == MODEL_SHA256) return model

        val partial = File(directory, "$MODEL_FILE_NAME.part")
        if (partial.exists()) partial.delete()
        client.newCall(Request.Builder().url(MODEL_URL).get().build()).execute().use { response ->
            check(response.isSuccessful) { "Whisper model download HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Whisper model download returned no body" }
            partial.outputStream().buffered().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        check(partial.length() == MODEL_SIZE_BYTES) { "Unexpected Whisper model size" }
        check(partial.sha256() == MODEL_SHA256) { "Whisper model checksum mismatch" }
        Files.move(partial.toPath(), model.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return model
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private external fun nativeCreate(modelPath: String): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String): String
    private external fun nativeDestroy(handle: Long)

    companion object {
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
        private const val MODEL_FILE_NAME = "ggml-base-q5_1.bin"
        private const val MODEL_SIZE_BYTES = 59_707_625L
        private const val MODEL_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"

        init {
            System.loadLibrary("aura_whisper")
        }
    }
}
