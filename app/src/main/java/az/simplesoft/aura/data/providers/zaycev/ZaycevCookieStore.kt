package az.simplesoft.aura.data.providers.zaycev

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class ZaycevCookieStore : CookieJar {
    private val cookies = ConcurrentHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            if (cookie.expiresAt > now) this.cookies[key(cookie)] = cookie
        }
        this.cookies.entries.removeIf { it.value.expiresAt <= now }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.entries.removeIf { it.value.expiresAt <= now }
        return cookies.values.filter { it.matches(url) }
    }

    fun headerFor(url: HttpUrl): String = loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }

    private fun key(cookie: Cookie) = "${cookie.domain}|${cookie.path}|${cookie.name}"
}
