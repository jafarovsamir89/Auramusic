package az.simplesoft.aura.assistant

import android.content.Context
/**
 * Small voice seam used by the UI. Azerbaijani prefers the local Silero voice;
 * every other language, download/load failure, or unsupported device falls back
 * to the best Android system voice available on the phone.
 */
class AuraSpeechSynthesizer(context: Context) {
    private val system = AndroidSystemVoiceEngine(context)
    private val silero = SileroVoiceEngine(context)

    fun speak(text: String, language: AssistantLanguage) {
        stop()
        if (language == AssistantLanguage.AZERBAIJANI) {
            silero.speak(text) { system.speak(text, language) }
        } else {
            system.speak(text, language)
        }
    }

    fun stop() {
        silero.stop()
        system.stop()
    }

    fun shutdown() {
        silero.shutdown()
        system.shutdown()
    }
}
