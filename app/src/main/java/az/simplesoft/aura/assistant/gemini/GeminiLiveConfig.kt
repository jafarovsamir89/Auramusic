package az.simplesoft.aura.assistant.gemini

object GeminiLiveConfig {
    const val MODEL = "gemini-3.1-flash-live-preview"
    const val DEFAULT_VOICE = "Kore"
    val VOICES = listOf("Kore", "Aoede", "Leda", "Zephyr")
    const val INPUT_RATE = 16_000
    const val OUTPUT_RATE = 24_000
    const val INPUT_CHUNK_MS = 20
    const val WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
}
