package az.simplesoft.aura.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class OfflineModelState { NOT_INSTALLED, DOWNLOADING, VERIFYING, READY, FAILED, DELETING }

data class OfflineModelMetadata(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val language: String,
    val purpose: String,
    val url: String
)

data class OfflineModelProgress(val downloadedBytes: Long = 0L, val totalBytes: Long = 0L) {
    val percent: Int get() = if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

/** Explicit model lifecycle. Recognition never starts a hidden network download. */
class OfflineModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()
) {
    private val appContext = context.applicationContext
    val metadata: OfflineModelMetadata = WHISPER_BASE_Q5
    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<OfflineModelState> = mutableState.asStateFlow()
    private val mutableProgress = MutableStateFlow(OfflineModelProgress(totalBytes = metadata.sizeBytes))
    val progress: StateFlow<OfflineModelProgress> = mutableProgress.asStateFlow()
    private val installMutex = Mutex()

    suspend fun download() = installMutex.withLock { downloadInternal() }

    private suspend fun downloadInternal() = withContext(Dispatchers.IO) {
        if (isReady()) {
            mutableState.value = OfflineModelState.READY
            return@withContext
        }
        require(directory().exists() || directory().mkdirs()) { "Unable to create Whisper directory" }
        val requiredSpace = (metadata.sizeBytes * 1.20).toLong()
        require(directory().usableSpace >= requiredSpace) { "Not enough free space for Whisper download" }
        mutableState.value = OfflineModelState.DOWNLOADING
        val partial = File(directory(), "${metadata.fileName}.part")
        try {
            var lastError: Throwable? = null
            for (attempt in 0 until MAX_RETRIES) {
                if (attempt > 0) partial.delete()
                try {
                    client.newCall(Request.Builder().url(metadata.url).build()).execute().use { response ->
                        check(response.isSuccessful) { "Whisper HTTP ${response.code}" }
                        val body = checkNotNull(response.body) { "Whisper response is empty" }
                        val contentLength = body.contentLength()
                        check(contentLength <= 0L || contentLength == metadata.sizeBytes) {
                            "Unexpected Whisper Content-Length $contentLength"
                        }
                        mutableProgress.value = OfflineModelProgress(0L, contentLength.takeIf { it > 0 } ?: metadata.sizeBytes)
                        partial.outputStream().buffered().use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var downloaded = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    downloaded += count
                                    mutableProgress.value = OfflineModelProgress(
                                        downloaded,
                                        contentLength.takeIf { it > 0 } ?: metadata.sizeBytes
                                    )
                                }
                            }
                        }
                    }
                    lastError = null
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            lastError?.let { throw it }
            mutableState.value = OfflineModelState.VERIFYING
            check(partial.length() == metadata.sizeBytes) { "Unexpected Whisper size ${partial.length()}" }
            check(partial.sha256() == metadata.sha256) { "Whisper checksum mismatch" }
            Files.move(partial.toPath(), modelFile().toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            writeSidecarAtomically()
            mutableProgress.value = OfflineModelProgress(metadata.sizeBytes, metadata.sizeBytes)
            mutableState.value = OfflineModelState.READY
        } catch (error: Throwable) {
            partial.delete()
            mutableState.value = OfflineModelState.FAILED
            throw error
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        mutableState.value = OfflineModelState.DELETING
        modelFile().delete()
        sidecarFile().delete()
        File(directory(), "${metadata.fileName}.sha256.part").delete()
        File(directory(), "${metadata.fileName}.part").delete()
        mutableProgress.value = OfflineModelProgress(totalBytes = metadata.sizeBytes)
        mutableState.value = OfflineModelState.NOT_INSTALLED
    }

    fun isReady(): Boolean {
        val model = modelFile()
        val valid = model.isFile && model.length() == metadata.sizeBytes &&
            runCatching { model.sha256() == metadata.sha256 }.getOrDefault(false)
        if (valid) runCatching { writeSidecarAtomically() }
        return valid
    }

    fun requireReady(): File = modelFile().takeIf { isReady() }
        ?: error("Whisper model is not installed; download it from storage settings")

    private fun initialState(): OfflineModelState = if (isReady()) OfflineModelState.READY else OfflineModelState.NOT_INSTALLED
    private fun directory() = File(appContext.filesDir, "models/whisper")
    private fun modelFile() = File(directory(), metadata.fileName)
    private fun sidecarFile() = File(directory(), "${metadata.fileName}.sha256")

    private fun writeSidecarAtomically() {
        val sidecar = sidecarFile()
        val partial = File(directory(), "${metadata.fileName}.sha256.part")
        partial.writeText(metadata.sha256)
        Files.move(partial.toPath(), sidecar.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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

    companion object {
        val WHISPER_BASE_Q5 = OfflineModelMetadata(
            id = "whisper-base-q5_1",
            fileName = "ggml-base-q5_1.bin",
            sizeBytes = 59_707_625L,
            sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            language = "ru,az,en",
            purpose = "offline speech recognition",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
        )
        private const val MAX_RETRIES = 3
    }
}
