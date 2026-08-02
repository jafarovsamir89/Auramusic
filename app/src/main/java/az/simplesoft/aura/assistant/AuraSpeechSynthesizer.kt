package az.simplesoft.aura.assistant

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

class AuraSpeechSynthesizer(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: Pair<String, AssistantLanguage>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine.setSpeechRate(0.96f)
            engine.setPitch(1.04f)
            pending?.also { (text, language) -> pending = null; speak(text, language) }
        }
    }

    fun speak(text: String, language: AssistantLanguage) {
        val safeText = text.trim().take(TextToSpeech.getMaxSpeechInputLength())
        if (safeText.isBlank()) return
        if (!ready) {
            pending = safeText to language
            return
        }
        val requested = when (language) {
            AssistantLanguage.RUSSIAN -> Locale("ru", "RU")
            AssistantLanguage.AZERBAIJANI -> Locale("az", "AZ")
            AssistantLanguage.ENGLISH -> Locale.US
        }
        val support = engine.isLanguageAvailable(requested)
        if (support >= TextToSpeech.LANG_AVAILABLE) engine.language = requested
        engine.speak(safeText, TextToSpeech.QUEUE_FLUSH, Bundle(), "aura:${UUID.randomUUID()}")
    }

    fun stop() = engine.stop()

    fun shutdown() {
        pending = null
        engine.stop()
        engine.shutdown()
    }
}
