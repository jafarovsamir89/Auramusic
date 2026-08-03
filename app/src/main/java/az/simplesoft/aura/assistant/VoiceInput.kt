package az.simplesoft.aura.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RecognitionBackend {
    data object Whisper : RecognitionBackend
    data object AndroidOnDevice : RecognitionBackend
    data object AndroidSystem : RecognitionBackend
    data object Unavailable : RecognitionBackend
}

sealed interface VoiceInputState {
    data object Idle : VoiceInputState
    data object CheckingAvailability : VoiceInputState
    data object Listening : VoiceInputState
    data object Processing : VoiceInputState
    data class TranscriptReady(val text: String) : VoiceInputState
    data class PermissionRequired(val permission: String) : VoiceInputState
    data class ModelRequired(val modelId: String) : VoiceInputState
    data class NoSpeech(val message: String) : VoiceInputState
    data class Failed(val message: String, val cause: Throwable? = null) : VoiceInputState
}

interface VoiceInputController {
    val state: StateFlow<VoiceInputState>
    val backend: StateFlow<RecognitionBackend>
    fun start()
    fun stop()
    fun destroy()
}

object VoiceRecognitionPolicy {
    fun select(context: Context, whisperReady: Boolean): RecognitionBackend {
        if (whisperReady) return RecognitionBackend.Whisper
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return RecognitionBackend.Unavailable
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            RecognitionBackend.AndroidOnDevice
        } else {
            RecognitionBackend.AndroidSystem
        }
    }
}

class DefaultVoiceInputController(
    context: Context,
    private val onTranscript: (String) -> Unit,
    private val onDiagnostics: (VoiceCaptureDiagnostics) -> Unit = {}
) : VoiceInputController {
    private val appContext = context.applicationContext
    private val modelManager = OfflineModelManager(appContext)
    private val mutableState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    private val mutableBackend = MutableStateFlow<RecognitionBackend>(RecognitionBackend.Unavailable)
    private val active = AtomicBoolean(false)
    private var whisper: WhisperSpeechRecognizer? = null
    private var system: OfflineSpeechRecognizer? = null
    private var fallbackUsed = false

    override val state: StateFlow<VoiceInputState> = mutableState.asStateFlow()
    override val backend: StateFlow<RecognitionBackend> = mutableBackend.asStateFlow()

    override fun start() {
        if (!active.compareAndSet(false, true)) return
        fallbackUsed = false
        mutableState.value = VoiceInputState.CheckingAvailability
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            active.set(false)
            mutableState.value = VoiceInputState.PermissionRequired(Manifest.permission.RECORD_AUDIO)
            return
        }
        val selected = VoiceRecognitionPolicy.select(appContext, modelManager.isReady())
        Log.i(TAG, "voice start: backend=$selected whisperReady=${selected == RecognitionBackend.Whisper}")
        mutableBackend.value = selected
        when (selected) {
            RecognitionBackend.Whisper -> startWhisper()
            RecognitionBackend.AndroidOnDevice -> startSystem(preferOnDevice = true)
            RecognitionBackend.AndroidSystem -> startSystem(preferOnDevice = false)
            RecognitionBackend.Unavailable -> fail("Системное распознавание недоступно. Установи Whisper в диагностике.")
        }
    }

    override fun stop() {
        whisper?.stop()
        system?.stop()
        active.set(false)
        mutableState.value = VoiceInputState.Idle
    }

    override fun destroy() {
        whisper?.destroy()
        system?.destroy()
        whisper = null
        system = null
        active.set(false)
    }

    private fun startWhisper() {
        system?.destroy()
        system = null
        whisper?.destroy()
        whisper = WhisperSpeechRecognizer(
            context = appContext,
            onCommand = ::completeTranscript,
            onState = { listening ->
                mutableState.value = if (listening) VoiceInputState.Listening else VoiceInputState.Processing
            },
            onNoSpeech = { message -> noSpeech(message) },
            onFailure = { error ->
                if (!fallbackUsed && VoiceRecognitionPolicy.select(appContext, false) != RecognitionBackend.Unavailable) {
                    fallbackUsed = true
                    startSystem(preferOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext))
                } else {
                    fail("Не удалось распознать речь.", error)
                }
            },
            onDiagnostics = onDiagnostics
        )
        runCatching { whisper?.start() }
            .onFailure { fail("Не удалось запустить локальный Whisper.", it) }
    }

    private fun startSystem(preferOnDevice: Boolean) {
        Log.i(TAG, "voice fallback: preferOnDevice=$preferOnDevice")
        whisper?.stop()
        system?.destroy()
        mutableBackend.value = if (preferOnDevice) RecognitionBackend.AndroidOnDevice else RecognitionBackend.AndroidSystem
        system = OfflineSpeechRecognizer(
            context = appContext,
            preferOnDevice = preferOnDevice,
            onCommand = ::completeTranscript,
            onState = { listening ->
                mutableState.value = if (listening) VoiceInputState.Listening else VoiceInputState.Processing
            },
            onNoSpeech = { message -> noSpeech(message) },
            onFailure = { error -> fail("Системное распознавание недоступно.", error) },
            onTerminal = {}
        )
        runCatching { system?.start() }
            .onFailure { fail("Не удалось открыть микрофон.", it) }
    }

    private fun completeTranscript(text: String) {
        val transcript = text.trim()
        Log.i(TAG, "voice transcript received: chars=${transcript.length}")
        if (transcript.isBlank()) {
            noSpeech("Не услышала речь.")
            return
        }
        mutableState.value = VoiceInputState.TranscriptReady(transcript)
        active.set(false)
        onTranscript(transcript)
    }

    private fun noSpeech(message: String) {
        active.set(false)
        mutableState.value = VoiceInputState.NoSpeech(message)
    }

    private fun fail(message: String, cause: Throwable? = null) {
        Log.w(TAG, "voice failed: $message", cause)
        active.set(false)
        mutableState.value = VoiceInputState.Failed(message, cause)
    }

    private companion object {
        const val TAG = "AuraVoiceDiag"
    }
}
