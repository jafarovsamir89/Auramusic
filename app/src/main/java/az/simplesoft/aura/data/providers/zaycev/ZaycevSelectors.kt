package az.simplesoft.aura.data.providers.zaycev

object ZaycevSelectors {
    const val SEARCH_PATH = "api/external/pages/search"
    const val FILE_META_PATH = "api/external/track/filezmeta"
    const val PLAY_PATH = "api/external/track/play"
    const val NEXT_DATA = "script#__NEXT_DATA__"
    const val TRACK_LINK = "a[href*=/pages/][href$=.shtml]"
    const val ARTIST_LINK = "a[href*=/artist/]"
    const val PLAY_BUTTON = "[data-qa^=listen-], button[aria-label*=Прослушать], button"

    const val BASE_URL = "https://zaycev.net/"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"
}
