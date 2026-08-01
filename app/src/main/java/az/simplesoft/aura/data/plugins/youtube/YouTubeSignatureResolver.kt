package az.simplesoft.aura.data.plugins.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Accepts direct URLs and already-signed cipher payloads only.
 * JavaScript signature or `n` transformations are deliberately not guessed.
 */
class YouTubeSignatureResolver {
    fun resolve(format: YouTubeAudioFormat): String {
        format.directUrl?.let(::validateUrl)?.let { return it }
        val cipher = format.signatureCipher
            ?: throw YouTubePluginException(
                YouTubeFailureReason.SIGNATURE_UNSUPPORTED,
                "YouTube returned no direct or signed audio URL"
            )
        val parameters = ("https://aura.invalid/?$cipher").toHttpUrlOrNull()
            ?: throw unsupported()
        val streamUrl = parameters.queryParameter("url")?.toHttpUrlOrNull()
            ?: throw unsupported()
        if (!parameters.queryParameter("s").isNullOrBlank()) throw unsupported()
        val signature = parameters.queryParameter("sig") ?: parameters.queryParameter("signature")
        val signed = if (signature.isNullOrBlank()) {
            streamUrl
        } else {
            streamUrl.newBuilder()
                .setQueryParameter(parameters.queryParameter("sp") ?: "signature", signature)
                .build()
        }
        return validateUrl(signed.toString())
    }

    private fun validateUrl(value: String): String {
        val url = value.toHttpUrlOrNull()
            ?: throw YouTubePluginException(YouTubeFailureReason.PARSE_CHANGED, "Invalid stream URL")
        if (!url.isHttps) {
            throw YouTubePluginException(YouTubeFailureReason.ACCESS_RESTRICTED, "Non-HTTPS stream rejected")
        }
        return url.toString()
    }

    private fun unsupported() = YouTubePluginException(
        YouTubeFailureReason.SIGNATURE_UNSUPPORTED,
        "YouTube signature transformation is unsupported"
    )
}
