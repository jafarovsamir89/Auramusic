package az.simplesoft.aura.assistant

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID

internal class AndroidSystemVoiceEngine(context: Context) : TextToSpeech.OnInitListener {
    private companion object { const val TAG = "AuraTTS" }
    private val engine = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: Pair<String, AssistantLanguage>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        engine.setSpeechRate(0.98f)
        engine.setPitch(1.01f)
        pending?.also { (text, language) ->
            pending = null
            speak(text, language)
        }
    }

    val isReady: Boolean get() = ready

    fun speak(text: String, language: AssistantLanguage) {
        val safeText = text.trim().take(TextToSpeech.getMaxSpeechInputLength())
        if (safeText.isBlank()) return
        if (!ready) {
            pending = safeText to language
            return
        }
        val requested = language.locale()
        val support = engine.isLanguageAvailable(requested)
        if (support < TextToSpeech.LANG_AVAILABLE) {
            Log.w(TAG, "language unavailable: requested=${requested.toLanguageTag()} support=$support; speech skipped")
            return
        }
        val selected = selectBestVoice(requested)
        selected?.let { engine.voice = it } ?: run { engine.language = requested }
        Log.i(
            TAG,
            "speak language=${requested.toLanguageTag()} voice=${selected?.name ?: "default"} " +
                "quality=${selected?.quality ?: -1} latency=${selected?.latency ?: -1} " +
                "network=${selected?.isNetworkConnectionRequired ?: false}"
        )
        engine.speak(safeText, TextToSpeech.QUEUE_FLUSH, Bundle(), "aura:${UUID.randomUUID()}")
    }

    fun speak(text: String, language: AssistantLanguage, profile: AuraVoiceProfile) {
        if (ready) {
            engine.setSpeechRate(profile.speechRate)
            engine.setPitch(profile.pitch)
        }
        speak(text, language)
    }

    fun stop() {
        pending = null
        if (ready) engine.stop()
    }

    fun shutdown() {
        pending = null
        engine.stop()
        engine.shutdown()
    }

    private fun selectBestVoice(locale: Locale): Voice? = engine.voices
        ?.asSequence()
        ?.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
        ?.maxWithOrNull(
            compareBy<Voice> { it.quality }
                .thenBy { !it.isNetworkConnectionRequired }
                .thenBy { -it.latency }
        )

    private fun AssistantLanguage.locale() = when (this) {
        AssistantLanguage.RUSSIAN -> Locale("ru", "RU")
        AssistantLanguage.AZERBAIJANI -> Locale("az", "AZ")
        AssistantLanguage.ENGLISH -> Locale.US
    }
}
