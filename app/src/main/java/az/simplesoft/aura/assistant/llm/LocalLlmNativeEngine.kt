package az.simplesoft.aura.assistant.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

data class LocalLlmInferenceResult(
    val text: String,
    val outputTokens: Int,
    val timeToFirstTokenMs: Long,
    val totalInferenceMs: Long
)

/** Thin single-threaded JNI wrapper around the official llama.cpp Android binding. */
internal class LocalLlmNativeEngine(context: Context) : AutoCloseable {
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private var initialized = false
    private var modelLoaded = false
    private var systemPromptReady = false

    suspend fun load(model: File): Long = withContext(dispatcher) {
        ensureInitialized()
        check(model.isFile && model.canRead()) { "Brain Pack is not readable" }
        unloadInternal()
        val started = System.currentTimeMillis()
        check(nativeLoad(model.absolutePath) == 0) { "llama.cpp could not load model" }
        check(nativePrepare() == 0) { "llama.cpp could not prepare context" }
        modelLoaded = true
        systemPromptReady = false
        System.currentTimeMillis() - started
    }

    suspend fun complete(systemPrompt: String, userPrompt: String, maxTokens: Int): LocalLlmInferenceResult =
        withContext(dispatcher) {
            ensureInitialized()
            check(modelLoaded) { "Brain Pack is not loaded" }
            if (!systemPromptReady) {
                check(nativeProcessSystemPrompt(systemPrompt) == 0) { "Could not process system prompt" }
                systemPromptReady = true
            }
            val started = System.currentTimeMillis()
            check(nativeProcessUserPrompt(userPrompt, maxTokens.coerceIn(32, 256)) == 0) {
                "Could not process user prompt"
            }
            val output = StringBuilder()
            var firstTokenAt: Long? = null
            while (true) {
                val token = nativeGenerateNextToken() ?: break
                if (token.isNotEmpty()) {
                    firstTokenAt = firstTokenAt ?: System.currentTimeMillis()
                    output.append(token)
                }
            }
            val finished = System.currentTimeMillis()
            val text = output.toString().trim()
            LocalLlmInferenceResult(
                text = text,
                outputTokens = text.split(Regex("\\s+")).count { it.isNotBlank() },
                timeToFirstTokenMs = firstTokenAt?.minus(started) ?: (finished - started),
                totalInferenceMs = finished - started
            )
        }

    fun generate(systemPrompt: String, userPrompt: String, maxTokens: Int = 160): Flow<String> = flow {
        emit(complete(systemPrompt, userPrompt, maxTokens).text)
    }.flowOn(dispatcher)

    suspend fun benchmark(): String = withContext(dispatcher) {
        ensureInitialized()
        check(modelLoaded) { "Brain Pack is not loaded" }
        nativeBenchModel(32, 32, 128, 1)
    }

    fun systemInfo(): String = runCatching { nativeSystemInfo() }.getOrDefault("unavailable")

    fun unload() {
        runBlocking(dispatcher) { unloadInternal() }
    }

    override fun close() {
        runBlocking(dispatcher) {
            unloadInternal()
            if (initialized) nativeShutdown()
            initialized = false
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            check(nativeLibDir.isNotBlank()) { "Native library directory is empty" }
            nativeInit(nativeLibDir)
            initialized = true
            Log.i(TAG, "llama.cpp initialized: ${nativeSystemInfo()}")
        }
    }

    private fun unloadInternal() {
        if (modelLoaded) {
            nativeUnload()
            modelLoaded = false
            systemPromptReady = false
        }
    }

    private external fun nativeInit(nativeLibDir: String)
    private external fun nativeLoad(modelPath: String): Int
    private external fun nativePrepare(): Int
    private external fun nativeSystemInfo(): String
    private external fun nativeBenchModel(pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun nativeProcessSystemPrompt(systemPrompt: String): Int
    private external fun nativeProcessUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun nativeGenerateNextToken(): String?
    private external fun nativeUnload()
    private external fun nativeShutdown()

    private companion object {
        const val TAG = "AuraLocalLlm"

        init {
            System.loadLibrary("aura_llama")
        }
    }
}
