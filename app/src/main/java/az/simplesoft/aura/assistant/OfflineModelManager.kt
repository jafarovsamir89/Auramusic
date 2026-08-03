package az.simplesoft.aura.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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

/** Explicit model lifecycle. Recognition never starts a hidden network download. */
class OfflineModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val appContext = context.applicationContext
    val metadata: OfflineModelMetadata = WHISPER_BASE_Q5
    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<OfflineModelState> = mutableState.asStateFlow()

    suspend fun download() = withContext(Dispatchers.IO) {
        if (isReady()) {
            mutableState.value = OfflineModelState.READY
            return@withContext
        }
        require(directory().usableSpace >= metadata.sizeBytes) { "Not enough free space for Whisper" }
        mutableState.value = OfflineModelState.DOWNLOADING
        val partial = File(directory(), "${metadata.fileName}.part")
        try {
            directory().mkdirs()
            client.newCall(Request.Builder().url(metadata.url).build()).execute().use { response ->
                check(response.isSuccessful) { "Whisper HTTP ${response.code}" }
                val body = checkNotNull(response.body) { "Whisper response is empty" }
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            mutableState.value = OfflineModelState.VERIFYING
            check(partial.length() == metadata.sizeBytes) { "Unexpected Whisper size ${partial.length()}" }
            check(partial.sha256() == metadata.sha256) { "Whisper checksum mismatch" }
            Files.move(partial.toPath(), modelFile().toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
        File(directory(), "${metadata.fileName}.part").delete()
        mutableState.value = OfflineModelState.NOT_INSTALLED
    }

    fun isReady(): Boolean = modelFile().let {
        it.isFile && it.length() == metadata.sizeBytes && runCatching { it.sha256() == metadata.sha256 }.getOrDefault(false)
    }

    fun requireReady(): File = modelFile().takeIf { isReady() }
        ?: error("Whisper model is not installed; download it from storage settings")

    private fun initialState(): OfflineModelState = if (isReady()) OfflineModelState.READY else OfflineModelState.NOT_INSTALLED
    private fun directory() = File(appContext.filesDir, "models/whisper")
    private fun modelFile() = File(directory(), metadata.fileName)

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
    }
}
