package az.simplesoft.aura.data.plugins.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubePlayerParserTest {
    private val parser = YouTubePlayerParser()

    @Test
    fun parsesOnlyAudioFormatsAndMetadata() {
        val player = parser.parse(resource("youtube/player.json"))

        assertEquals("kXYiU_JCYtU", player.videoId)
        assertEquals("Numb", player.title)
        assertEquals("Linkin Park", player.artist)
        assertEquals(187_000L, player.durationMs)
        assertEquals(2007, player.year)
        assertEquals(listOf(140, 251), player.formats.map(YouTubeAudioFormat::itag))
    }

    @Test
    fun mapsLoginAndAgeRestrictionsToTypedFailures() {
        val login = failure("LOGIN_REQUIRED", "Sign in to confirm you're not a bot")
        val age = failure("UNPLAYABLE", "This video may be inappropriate for some users. Confirm your age")

        assertEquals(YouTubeFailureReason.LOGIN_REQUIRED, login.reason)
        assertEquals(YouTubeFailureReason.AGE_RESTRICTED, age.reason)
    }

    @Test
    fun rejectsDrmBeforeSelectingFormats() {
        val fixture = resource("youtube/player.json")
            .replace("\"streamingData\": {", "\"drmFamilies\":[\"WIDEVINE\"],\"streamingData\": {")
        val error = runCatching { parser.parse(fixture) }.exceptionOrNull() as YouTubePluginException

        assertEquals(YouTubeFailureReason.DRM, error.reason)
    }

    private fun failure(status: String, reason: String): YouTubePluginException =
        runCatching {
            parser.parse("""{"playabilityStatus":{"status":"$status","reason":"$reason"}}""")
        }.exceptionOrNull() as YouTubePluginException

    private fun resource(path: String): String = checkNotNull(javaClass.classLoader?.getResource(path)).readText()
}
