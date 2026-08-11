package az.simplesoft.aura.assistant.llm

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

enum class BrainModelState { NOT_INSTALLED, DOWNLOADING, VERIFYING, READY, FAILED, DELETING }

data class BrainModelMetadata(
    val id: String,
    val displayName: String,
    val version: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val quantization: String,
    val parameters: String,
    val languages: String,
    val license: String,
    val url: String
)

data class BrainModelProgress(val downloadedBytes: Long = 0L, val totalBytes: Long = 0L) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

/** Explicit Brain Pack lifecycle. The assistant never downloads a model during a reply. */
class LocalLlmModelManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.MINUTES)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()
) {
    private val root = File(context.applicationContext.filesDir, "models/brain")
    private val mutableStates = MutableStateFlow<Map<String, BrainModelState>>(emptyMap())
    private val mutableProgress = MutableStateFlow<Map<String, BrainModelProgress>>(emptyMap())
    private val installMutex = Mutex()

    val states: StateFlow<Map<String, BrainModelState>> = mutableStates.asStateFlow()
    val progress: StateFlow<Map<String, BrainModelProgress>> = mutableProgress.asStateFlow()

    fun models(): List<BrainModelMetadata> = definitions()

    fun state(id: String): BrainModelState = mutableStates.value[id] ?: inspect(id).first

    fun progress(id: String): BrainModelProgress = mutableProgress.value[id]
        ?: BrainModelProgress(totalBytes = definition(id).sizeBytes)

    fun isReady(id: String = DEFAULT_MODEL_ID): Boolean = inspect(id).first == BrainModelState.READY

    fun modelFile(id: String = DEFAULT_MODEL_ID): File = File(root, definition(id).fileName)

    suspend fun install(id: String) = installMutex.withLock { installInternal(definition(id)) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val metadata = definition(id)
        setState(id, BrainModelState.DELETING)
        File(root, metadata.fileName).delete()
        File(root, "${metadata.fileName}.sha256").delete()
        File(root, "${metadata.fileName}.part").delete()
        setProgress(id, BrainModelProgress(totalBytes = metadata.sizeBytes))
        setState(id, BrainModelState.NOT_INSTALLED)
    }

    private suspend fun installInternal(metadata: BrainModelMetadata) = withContext(Dispatchers.IO) {
        require(root.exists() || root.mkdirs()) { "Unable to create Brain Pack directory" }
        require(root.usableSpace >= (metadata.sizeBytes * 1.20).toLong()) {
            "Not enough free space for Brain Pack"
        }
        val partial = File(root, "${metadata.fileName}.part")
        setState(metadata.id, BrainModelState.DOWNLOADING)
        setProgress(metadata.id, BrainModelProgress(totalBytes = metadata.sizeBytes))
        try {
            var lastError: Throwable? = null
            for (attempt in 0 until MAX_RETRIES) {
                if (attempt > 0) partial.delete()
                try {
                    client.newCall(Request.Builder().url(metadata.url).get().build()).execute().use { response ->
                        check(response.isSuccessful) { "Brain Pack HTTP ${response.code}" }
                        val body = checkNotNull(response.body) { "Brain Pack response is empty" }
                        val contentLength = body.contentLength()
                        check(contentLength <= 0L || contentLength == metadata.sizeBytes) {
                            "Unexpected Brain Pack Content-Length $contentLength"
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
                                    setProgress(
                                        metadata.id,
                                        BrainModelProgress(downloaded, contentLength.takeIf { it > 0 } ?: metadata.sizeBytes)
                                    )
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
                    if (attempt + 1 == MAX_RETRIES) throw error
                }
            }
            setState(metadata.id, BrainModelState.VERIFYING)
            check(partial.length() == metadata.sizeBytes) { "Unexpected Brain Pack size ${partial.length()}" }
            check(partial.sha256() == metadata.sha256) { "Brain Pack checksum mismatch" }
            val target = File(root, metadata.fileName)
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            writeSidecar(File(root, "${metadata.fileName}.sha256"), metadata.sha256)
            setProgress(metadata.id, BrainModelProgress(metadata.sizeBytes, metadata.sizeBytes))
            setState(metadata.id, BrainModelState.READY)
        } catch (error: Throwable) {
            partial.delete()
            setState(metadata.id, BrainModelState.FAILED)
            throw error
        }
    }

    private fun inspect(id: String): Pair<BrainModelState, File?> {
        val metadata = definition(id)
        val model = File(root, metadata.fileName)
        val sidecar = File(root, "${metadata.fileName}.sha256")
        val ready = model.isFile && model.length() == metadata.sizeBytes &&
            runCatching { model.sha256() == metadata.sha256 && sidecar.readText().trim() == metadata.sha256 }
                .getOrDefault(false)
        val state = when {
            ready -> BrainModelState.READY
            model.exists() || sidecar.exists() -> BrainModelState.FAILED
            else -> BrainModelState.NOT_INSTALLED
        }
        if (mutableStates.value[id] != state) setState(id, state)
        return state to model.takeIf { ready }
    }

    private fun definition(id: String): BrainModelMetadata = definitions().firstOrNull { it.id == id }
        ?: error("Unknown Brain Pack: $id")

    private fun definitions() = listOf(
        BrainModelMetadata(
            id = "qwen3-0.6b-q8",
            displayName = "AURA Brain — Lite (Qwen3 0.6B)",
            version = "Qwen3",
            fileName = "Qwen3-0.6B-Q8_0.gguf",
            sizeBytes = 639_446_688L,
            sha256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
            quantization = "Q8_0",
            parameters = "0.6B",
            languages = "ru, az, en",
            license = "Apache-2.0",
            url = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf"
        ),
        BrainModelMetadata(
            id = "qwen3-1.7b-q8-benchmark",
            displayName = "Benchmark only (Qwen3 1.7B)",
            version = "Qwen3",
            fileName = "Qwen3-1.7B-Q8_0.gguf",
            sizeBytes = 1_834_426_016L,
            sha256 = "061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a",
            quantization = "Q8_0",
            parameters = "1.7B",
            languages = "ru, az, en",
            license = "Apache-2.0",
            url = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf"
        )
    )

    private fun setState(id: String, state: BrainModelState) {
        mutableStates.value = mutableStates.value + (id to state)
    }

    private fun setProgress(id: String, progress: BrainModelProgress) {
        mutableProgress.value = mutableProgress.value + (id to progress)
    }

    private fun writeSidecar(target: File, value: String) {
        val partial = File(target.parentFile, "${target.name}.part")
        partial.writeText(value)
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
        const val DEFAULT_MODEL_ID = "qwen3-0.6b-q8"
        private const val MAX_RETRIES = 3
    }
}
