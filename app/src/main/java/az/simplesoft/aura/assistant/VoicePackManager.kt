package az.simplesoft.aura.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
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

enum class VoicePackStatus { NOT_INSTALLED, DOWNLOADING, VERIFYING, READY, CORRUPT, FAILED }

data class VoicePackMetadata(
    val id: String,
    val language: AssistantLanguage,
    val displayName: String,
    val sizeBytes: Long,
    val version: String,
    val license: String,
    val url: String,
    val sha256: String,
    val status: VoicePackStatus
)

data class VoicePackProgress(val downloadedBytes: Long = 0L, val totalBytes: Long = 0L) {
    val percent: Int get() = if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

interface VoiceEngine {
    fun speak(text: String, language: AssistantLanguage)
    fun stop()
    fun shutdown()
}

object VoiceSelectionPolicy {
    fun preferLocal(language: AssistantLanguage): Boolean = language != AssistantLanguage.ENGLISH
}

/** Explicit voice-pack lifecycle. `speak()` never calls install(). */
class VoicePackManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()
) {
    private val root = File(context.applicationContext.filesDir, "voice")
    private val mutableProgress = MutableStateFlow<Map<String, VoicePackProgress>>(emptyMap())
    val progress: StateFlow<Map<String, VoicePackProgress>> = mutableProgress.asStateFlow()
    private val installMutex = Mutex()

    fun packs(): List<VoicePackMetadata> = definitions().map { definition ->
        val file = File(File(root, definition.directory), definition.fileName)
        val sidecar = File(file.parentFile, "${definition.fileName}.sha256")
        val validModel = file.isFile && file.length() == definition.sizeBytes &&
            runCatching { file.sha256() == definition.sha256 }.getOrDefault(false)
        if (validModel && (!sidecar.isFile || sidecar.readText().trim() != definition.sha256)) {
            runCatching { writeSidecarAtomically(sidecar, definition.sha256) }
        }
        val status = when {
            !file.exists() && !sidecar.exists() -> VoicePackStatus.NOT_INSTALLED
            file.isFile && file.length() == definition.sizeBytes &&
                runCatching { file.sha256() == definition.sha256 && sidecar.readText().trim() == definition.sha256 }
                    .getOrDefault(false) -> VoicePackStatus.READY
            else -> VoicePackStatus.CORRUPT
        }
        definition.toMetadata(status)
    }

    suspend fun install(id: String) = installMutex.withLock { installInternal(id) }

    private suspend fun installInternal(id: String) = withContext(Dispatchers.IO) {
        val definition = definitions().firstOrNull { it.id == id } ?: error("Unknown voice pack: $id")
        val directory = File(root, definition.directory)
        require(directory.exists() || directory.mkdirs()) { "Unable to create voice directory" }
        require(directory.usableSpace >= (definition.sizeBytes * 1.20).toLong()) {
            "Not enough free space for voice pack"
        }
        val partial = File(directory, "${definition.fileName}.part")
        setProgress(id, VoicePackProgress(0L, definition.sizeBytes))
        try {
            var lastError: Throwable? = null
            for (attempt in 0 until MAX_RETRIES) {
                if (attempt > 0) partial.delete()
                try {
                    client.newCall(Request.Builder().url(definition.url).get().build()).execute().use { response ->
                        check(response.isSuccessful) { "Voice pack HTTP ${response.code}" }
                        val body = checkNotNull(response.body) { "Voice pack response is empty" }
                        val contentLength = body.contentLength()
                        check(contentLength <= 0L || contentLength == definition.sizeBytes) {
                            "Unexpected voice pack Content-Length $contentLength"
                        }
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
                                    setProgress(id, VoicePackProgress(downloaded, contentLength.takeIf { it > 0 } ?: definition.sizeBytes))
                                }
                            }
                        }
                    }
                    lastError = null
                    break
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            lastError?.let { throw it }
            check(partial.length() == definition.sizeBytes) { "Unexpected voice pack size ${partial.length()}" }
            check(partial.sha256() == definition.sha256) { "Voice pack checksum mismatch" }
            val model = File(directory, definition.fileName)
            Files.move(partial.toPath(), model.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            writeSidecarAtomically(File(directory, "${definition.fileName}.sha256"), definition.sha256)
            setProgress(id, VoicePackProgress(definition.sizeBytes, definition.sizeBytes))
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    fun delete(id: String): Boolean {
        val definition = definitions().firstOrNull { it.id == id } ?: return false
        setProgress(id, VoicePackProgress(totalBytes = definition.sizeBytes))
        return File(root, definition.directory).deleteRecursively()
    }

    private fun setProgress(id: String, value: VoicePackProgress) {
        mutableProgress.value = mutableProgress.value + (id to value)
    }

    private fun writeSidecarAtomically(target: File, value: String) {
        val partial = File(target.parentFile, "${target.name}.part")
        partial.writeText(value)
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun definitions() = listOf(
        Definition(
            id = "silero-ru-kseniya",
            language = AssistantLanguage.RUSSIAN,
            displayName = "Kseniya",
            sizeBytes = 142_264_026L,
            version = "v1",
            license = "MIT",
            url = "https://models.silero.ai/models/tts/ru/v1_kseniya_16000.jit",
            sha256 = "3d5359561e10dc27e9fe031197872f35857085fcfd1d1e36aaa624b01b3aa74f",
            directory = "silero-ru-kseniya",
            fileName = "v1_kseniya_16000.jit"
        ),
        Definition(
            id = "silero-az-local",
            language = AssistantLanguage.AZERBAIJANI,
            displayName = "Silero Azerbaijani",
            sizeBytes = 91_695_221L,
            version = "v5",
            license = "MIT",
            url = "https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.jit",
            sha256 = "d7d361caf78b8480bcd65a0c367af665a2bf6f06c8507306e3781dc7c6ce781b",
            directory = "silero-v5-cis-base",
            fileName = "v5_cis_base_nostress.jit"
        )
    )

    private data class Definition(
        val id: String,
        val language: AssistantLanguage,
        val displayName: String,
        val sizeBytes: Long,
        val version: String,
        val license: String,
        val url: String,
        val sha256: String,
        val directory: String,
        val fileName: String
    ) {
        fun toMetadata(status: VoicePackStatus) = VoicePackMetadata(
            id, language, displayName, sizeBytes, version, license, url, sha256, status
        )
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

    companion object { private const val MAX_RETRIES = 3 }
}
