package az.simplesoft.aura.data.plugins.youtube

import android.net.Uri

object YouTubePlaybackIdentity {
    const val SCHEME = "aura-youtube"

    fun uri(videoId: String): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority("play")
        .appendPath(videoId)
        .build()

    fun videoId(uri: Uri?): String? = uri
        ?.takeIf { it.scheme == SCHEME }
        ?.lastPathSegment
        ?.takeIf(VIDEO_ID::matches)

    fun videoId(trackId: String): String? = trackId
        .removePrefix("youtube:")
        .takeIf(VIDEO_ID::matches)

    fun cacheKey(videoId: String): String = "youtube:$videoId"

    fun cacheKey(videoId: String, itag: Int): String = "youtube:$videoId:$itag"

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
}
