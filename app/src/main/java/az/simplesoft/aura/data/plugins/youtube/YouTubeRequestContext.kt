package az.simplesoft.aura.data.plugins.youtube

import java.util.Locale

data class YouTubeRequestContext(
    val language: String = Locale.getDefault().language.takeIf(String::isNotBlank) ?: "en",
    val countryCode: String = Locale.getDefault().country.takeIf(String::isNotBlank) ?: "US",
    val clientName: String = "ANDROID",
    val clientVersion: String = DEFAULT_ANDROID_CLIENT_VERSION,
    val androidSdkVersion: Int = 33,
    val userAgent: String = "com.google.android.youtube/$DEFAULT_ANDROID_CLIENT_VERSION (Linux; U; Android 13) gzip"
) {
    fun browserHeaders(): Map<String, String> = mapOf(
        "User-Agent" to BROWSER_USER_AGENT,
        "Accept-Language" to "$language-$countryCode,$language;q=0.9,en;q=0.7",
        "Accept" to "text/html,application/xhtml+xml"
    )

    fun playerHeaders(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "Content-Type" to "application/json"
    )

    companion object {
        const val DEFAULT_ANDROID_CLIENT_VERSION = "20.10.38"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
    }
}
