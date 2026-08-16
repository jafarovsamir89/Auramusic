package az.simplesoft.aura.assistant.gemini

import android.content.Context

/** Small local-only counter. It never stores audio, prompts, or the API key. */
class GeminiUsageStore(context: Context) {
    private val preferences = context.getSharedPreferences("aura_gemini_usage", Context.MODE_PRIVATE)

    @Synchronized
    fun read(): GeminiTokenUsage = GeminiTokenUsage(
        promptTokens = preferences.getLong("prompt", 0),
        responseTokens = preferences.getLong("response", 0),
        totalTokens = preferences.getLong("total", 0),
        thoughtsTokens = preferences.getLong("thoughts", 0),
        cachedTokens = preferences.getLong("cached", 0),
        toolUsePromptTokens = preferences.getLong("tool", 0),
        inputAudioTokens = preferences.getLong("input_audio", 0),
        inputTextTokens = preferences.getLong("input_text", 0),
        outputAudioTokens = preferences.getLong("output_audio", 0),
        outputTextTokens = preferences.getLong("output_text", 0)
    )

    @Synchronized
    fun add(value: GeminiTokenUsage): GeminiTokenUsage {
        val next = read() + value
        preferences.edit()
            .putLong("prompt", next.promptTokens)
            .putLong("response", next.responseTokens)
            .putLong("total", next.totalTokens)
            .putLong("thoughts", next.thoughtsTokens)
            .putLong("cached", next.cachedTokens)
            .putLong("tool", next.toolUsePromptTokens)
            .putLong("input_audio", next.inputAudioTokens)
            .putLong("input_text", next.inputTextTokens)
            .putLong("output_audio", next.outputAudioTokens)
            .putLong("output_text", next.outputTextTokens)
            .apply()
        return next
    }

    fun clear() = preferences.edit().clear().apply()
}
