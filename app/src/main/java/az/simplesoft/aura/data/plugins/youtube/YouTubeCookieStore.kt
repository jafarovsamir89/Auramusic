package az.simplesoft.aura.data.plugins.youtube

/** Anonymous MVP storage. Account cookies are disabled and never included in diagnostics. */
class YouTubeCookieStore(
    private val enabled: Boolean = false
) {
    private val values = linkedMapOf<String, String>()

    @Synchronized
    fun update(name: String, value: String) {
        if (enabled && name in ALLOWED_ANONYMOUS_COOKIES && value.isNotBlank()) values[name] = value
    }

    @Synchronized
    fun requestHeader(): String? = if (!enabled) null else values.entries
        .joinToString("; ") { (name, value) -> "$name=$value" }
        .takeIf(String::isNotBlank)

    @Synchronized
    fun clear() = values.clear()

    companion object {
        private val ALLOWED_ANONYMOUS_COOKIES = setOf("CONSENT", "SOCS", "PREF")
    }
}
