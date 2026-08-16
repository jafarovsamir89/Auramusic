package az.simplesoft.aura.assistant

import android.content.Context
import android.util.Log
/**
 * Unified local voice seam. Russian can use the installed female Silero pack;
 * Azerbaijani deliberately uses the best real system az-AZ voice until a
 * properly trained local AZ pack is available. No fake language conversion.
 */
class AuraSpeechSynthesizer(context: Context) : VoiceEngine, AuraVoiceEngine {
    val voicePacks = VoicePackManager(context)
    private val russianSilero = SileroRussianVoiceEngine(context)
    private val textProcessor = SpeechTextProcessor()
    private val profile = AuraVoiceProfile()
    @Volatile private var engineMode = VoiceEngineMode.VERIFIED_SILERO

    override val id: String = "aura-local-voice"
    override val supportedLanguages: Set<AssistantLanguage> = AssistantLanguage.entries.toSet()
    override val isReady: Boolean
        get() = engineMode == VoiceEngineMode.VERIFIED_SILERO && russianSilero.isReady()

    override fun prepare() {
        if (engineMode == VoiceEngineMode.VERIFIED_SILERO) russianSilero.prepare()
    }

    fun setEngineMode(mode: VoiceEngineMode) { engineMode = mode }

    override fun speak(text: String, language: AssistantLanguage) {
        speak(text, language, VoiceStyle.NEUTRAL)
    }

    override fun speak(text: String, language: AssistantLanguage, style: VoiceStyle) {
        val processed = textProcessor.process(text, language)
        if (processed.isBlank()) return
        stop()
        when (language) {
            AssistantLanguage.RUSSIAN -> if (engineMode == VoiceEngineMode.VERIFIED_SILERO) {
                russianSilero.speak(processed, profileFor(style)) { Log.w("AuraVoice", "Russian Silero pack is unavailable; speech skipped") }
            } else {
                Log.w("AuraVoice", "Android TTS is disabled; speech skipped")
            }
            // No Android TTS fallback: native Gemini handles online RU/AZ/EN audio.
            AssistantLanguage.AZERBAIJANI,
            AssistantLanguage.ENGLISH -> Log.w("AuraVoice", "Offline voice is unavailable for $language; speech skipped")
        }
    }

    override fun stop() {
        russianSilero.stop()
    }

    override fun shutdown() {
        russianSilero.shutdown()
    }

    private fun profileFor(style: VoiceStyle): AuraVoiceProfile = when (style) {
        VoiceStyle.DRIVING -> profile.copy(speechRate = 1.02f, pitch = 1.0f)
        VoiceStyle.CALM -> profile.copy(speechRate = 0.88f, pitch = 1.0f)
        VoiceStyle.KIDS -> profile.copy(speechRate = 0.98f, pitch = 1.06f)
        VoiceStyle.QUESTION -> profile.copy(pitch = 1.04f)
        else -> profile
    }
}
