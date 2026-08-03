package az.simplesoft.aura.assistant

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Small, multilingual, on-device Whisper.cpp runtime. The model is never bundled into the APK. */
internal class WhisperCppEngine(
    context: Context,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
) : AutoCloseable {
    private val modelManager = OfflineModelManager(context, client)
    private var nativeHandle = 0L

    val modelState: StateFlow<OfflineModelState> get() = modelManager.state
    val modelMetadata: OfflineModelMetadata get() = modelManager.metadata

    /** Called by explicit storage/setup UI, never from transcribe(). */
    suspend fun prepareModel() = modelManager.download()

    @Synchronized
    fun transcribe(samples: FloatArray): String {
        check(samples.isNotEmpty()) { "No audio was captured" }
        if (nativeHandle == 0L) nativeHandle = nativeCreate(modelManager.requireReady().absolutePath)
        return nativeTranscribe(nativeHandle, samples, "auto").trim()
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) nativeDestroy(nativeHandle)
        nativeHandle = 0L
    }

    private external fun nativeCreate(modelPath: String): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String): String
    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("aura_whisper")
        }
    }
}
