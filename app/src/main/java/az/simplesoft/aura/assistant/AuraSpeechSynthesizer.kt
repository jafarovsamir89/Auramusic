package az.simplesoft.aura.assistant

import android.content.Context
/**
 * Small voice seam used by the UI. Russian uses the local female Silero voice
 * (Kseniya); Azerbaijani uses its local Silero voice. Unsupported text or a
 * download/load failure falls back to the best system voice on the phone.
 */
class AuraSpeechSynthesizer(context: Context) {
    private val system = AndroidSystemVoiceEngine(context)
    private val silero = SileroVoiceEngine(context)
    private val russianSilero = SileroRussianVoiceEngine(context)

    fun speak(text: String, language: AssistantLanguage) {
        stop()
        when (language) {
            AssistantLanguage.RUSSIAN -> russianSilero.speak(text) { system.speak(text, language) }
            AssistantLanguage.AZERBAIJANI -> silero.speak(text) { system.speak(text, language) }
            AssistantLanguage.ENGLISH -> system.speak(text, language)
        }
    }

    fun stop() {
        silero.stop()
        russianSilero.stop()
        system.stop()
    }

    fun shutdown() {
        silero.shutdown()
        russianSilero.shutdown()
        system.shutdown()
    }
}
