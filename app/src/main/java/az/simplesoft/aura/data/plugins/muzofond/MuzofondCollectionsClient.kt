package az.simplesoft.aura.data.plugins.muzofond

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import az.simplesoft.aura.data.providers.AuraHttpClient
import okhttp3.Request

/** Loads public Muzofond collections and their track metadata on demand. */
internal class MuzofondCollectionsClient(
    private val httpClient: OkHttpClient = AuraHttpClient.create(),
    private val parser: MuzofondCollectionsParser = MuzofondCollectionsParser()
) {
    suspend fun collections(limit: Int = 240) = fetch(MuzofondSelectors.COLLECTIONS_URL) { html ->
        parser.parseCollections(html, limit)
    }

    suspend fun genres(limit: Int = 48) = fetch(MuzofondSelectors.POPULAR_URL) { html ->
        parser.parseGenres(html, limit)
    }

    suspend fun collection(url: String, limit: Int = 50) = fetch(url) { html ->
        parser.parseCollection(html, url, limit)
    }

    private suspend fun <T> fetch(url: String, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.7")
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Muzofond collections HTTP ${response.code}" }
            parse(response.body?.string().orEmpty())
        }
    }

    private companion object {
        const val USER_AGENT = "AuraMusic/0.6 (Android)"
    }
}
