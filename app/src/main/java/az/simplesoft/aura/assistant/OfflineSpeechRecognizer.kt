package az.simplesoft.aura.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Uses the system Android SpeechRecognizer with EXTRA_PREFER_OFFLINE. The flag
 * is only a preference: the installed recognizer may still use its own network
 * service. WhisperSpeechRecognizer is the truthful fully-local path.
 *
 * Для гарантированно автономной работы интерфейс можно заменить VoskRecognizer,
 * не меняя LocalIntentEngine.
 */
class OfflineSpeechRecognizer(
    context: Context,
    private val onPartialText: (String) -> Unit = {},
    private val onCommand: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onNoSpeech: (String) -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
    private val onTerminal: () -> Unit = {},
    private val preferOnDevice: Boolean = false
) {
    private val commandGate = VoiceCommandGate()
    private val recognizer = createRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onState(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onState(false)
            override fun onError(error: Int) {
                onState(false)
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    onNoSpeech("No speech was detected")
                } else {
                    onFailure(IllegalStateException("Android recognizer error=$error"))
                }
                onTerminal()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onPartialText)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onResults(results: Bundle?) {
                onState(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isBlank()) onNoSpeech("No speech was detected")
                else commandGate.onFinal(text)?.let(onCommand)
                onTerminal()
            }
            override fun onLanguageDetection(results: Bundle) = Unit
        })
    }

    fun start() {
        commandGate.reset()
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, true)
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                        arrayListOf("ru-RU", "az-AZ", "en-US")
                    )
                }
            }
        )
    }

    fun stop() = recognizer.stopListening()
    fun destroy() = recognizer.destroy()

    private fun createRecognizer(context: Context): SpeechRecognizer {
        return if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }
}
