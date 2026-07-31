package az.simplesoft.aura.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Использует системный Android SpeechRecognizer с EXTRA_PREFER_OFFLINE.
 * На телефонах с установленным офлайн-языковым пакетом работает без сети.
 *
 * Для гарантированно автономной работы интерфейс можно заменить VoskRecognizer,
 * не меняя LocalIntentEngine.
 */
class OfflineSpeechRecognizer(
    context: Context,
    private val onText: (String) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onState(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onState(false)
            override fun onError(error: Int) = onState(false)
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onText)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onResults(results: Bundle?) {
                onState(false)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onText)
            }
        })
    }

    fun start() {
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        )
    }

    fun stop() = recognizer.stopListening()
    fun destroy() = recognizer.destroy()
}
