package az.simplesoft.aura.data.plugins.youtube

object YouTubeSelectors {
    const val BASE_URL = "https://www.youtube.com"
    const val SEARCH_URL = "$BASE_URL/results"
    const val WATCH_URL = "$BASE_URL/watch"
    const val PLAYER_URL = "$BASE_URL/youtubei/v1/player?prettyPrint=false"
    const val NEXT_URL = "$BASE_URL/youtubei/v1/next?prettyPrint=false"
    const val INITIAL_DATA_MARKER = "var ytInitialData = "

    val RESTRICTED_HOSTS = setOf("consent.youtube.com", "www.google.com", "google.com")
}
