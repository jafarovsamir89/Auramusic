package az.simplesoft.aura.assistant

import android.content.Context
/**
 * Unified local voice seam. Russian can use the installed female Silero pack;
 * Azerbaijani deliberately uses the best real system az-AZ voice until a
 * properly trained local AZ pack is available. No fake language conversion.
 */
class AuraSpeechSynthesizer(context: Context) : VoiceEngine, AuraVoiceEngine {
    val voicePacks = VoicePackManager(context)
    private val system = AndroidSystemVoiceEngine(context)
    private val russianSilero = SileroRussianVoiceEngine(context)
    private val textProcessor = SpeechTextProcessor()
    private val profile = AuraVoiceProfile()

    override val id: String = "aura-local-voice"
    override val supportedLanguages: Set<AssistantLanguage> = AssistantLanguage.entries.toSet()
    override val isReady: Boolean get() = system.isReady

    override fun prepare() = Unit

    override fun speak(text: String, language: AssistantLanguage) {
        speak(text, language, VoiceStyle.NEUTRAL)
    }

    override fun speak(text: String, language: AssistantLanguage, style: VoiceStyle) {
        val processed = textProcessor.process(text, language)
        if (processed.isBlank()) return
        stop()
        when (language) {
            AssistantLanguage.RUSSIAN -> russianSilero.speak(processed) { system.speak(processed, language, profileFor(style)) }
            // The available Silero pack is Russian; never use it as fake Azerbaijani.
            AssistantLanguage.AZERBAIJANI -> system.speak(processed, language, profileFor(style))
            AssistantLanguage.ENGLISH -> system.speak(processed, language, profileFor(style))
        }
    }

    override fun stop() {
        russianSilero.stop()
        system.stop()
    }

    override fun shutdown() {
        russianSilero.shutdown()
        system.shutdown()
    }

    private fun profileFor(style: VoiceStyle): AuraVoiceProfile = when (style) {
        VoiceStyle.DRIVING -> profile.copy(speechRate = 1.02f, pitch = 1.0f)
        VoiceStyle.CALM -> profile.copy(speechRate = 0.88f, pitch = 1.0f)
        VoiceStyle.KIDS -> profile.copy(speechRate = 0.98f, pitch = 1.06f)
        VoiceStyle.QUESTION -> profile.copy(pitch = 1.04f)
        else -> profile
    }
}
