package az.simplesoft.aura.assistant.gemini

import az.simplesoft.aura.BuildConfig

data class GeminiCredential(val value: String, val ephemeral: Boolean = false)

interface GeminiAuthProvider {
    suspend fun getCredential(): GeminiCredential
}

class LocalDebugApiKeyProvider : GeminiAuthProvider {
    override suspend fun getCredential(): GeminiCredential {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        require(key.isNotBlank()) {
            "Gemini API key is missing. Add GEMINI_API_KEY=... to local.properties."
        }
        return GeminiCredential(key)
    }
}

class EphemeralTokenProvider(private val loader: suspend () -> String) : GeminiAuthProvider {
    override suspend fun getCredential(): GeminiCredential =
        GeminiCredential(loader().trim(), ephemeral = true).also {
            require(it.value.isNotBlank()) { "Gemini ephemeral token is empty" }
        }
}
