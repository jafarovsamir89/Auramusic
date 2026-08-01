package az.simplesoft.aura.data.plugins.youtube

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AuraYouTubePlayerClientTest {
    @Test
    fun parsesDirectAudioFormatsAndPreservesClientUserAgent() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(playerResponse(VIDEO_ID)))
            val client = AuraYouTubePlayerClient(
                httpClient = okhttp3.OkHttpClient(),
                endpoint = server.url("/player").toString(),
                profiles = listOf(AuraYouTubePlayerClient.IOS)
            )

            val result = client.player(VIDEO_ID)

            assertEquals("IOS", result.profile.name)
            assertEquals(listOf(140, 251), result.formats.map(YouTubePlayerFormat::itag))
            assertTrue(server.takeRequest().getHeader("User-Agent")!!.startsWith("com.google.ios.youtube/"))
        }
    }

    @Test
    fun fallsBackToNextProfileWhenFirstReturnsNoDirectAudio() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(noFormatsResponse(VIDEO_ID)))
            server.enqueue(MockResponse().setBody(playerResponse(VIDEO_ID)))
            val client = AuraYouTubePlayerClient(
                httpClient = okhttp3.OkHttpClient(),
                endpoint = server.url("/player").toString(),
                profiles = listOf(AuraYouTubePlayerClient.ANDROID, AuraYouTubePlayerClient.IOS)
            )

            val result = client.player(VIDEO_ID)

            assertEquals("IOS", result.profile.name)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun rejectsMismatchedVideoId() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(playerResponse("aaaaaaaaaaa")))
            val client = AuraYouTubePlayerClient(
                httpClient = okhttp3.OkHttpClient(),
                endpoint = server.url("/player").toString(),
                profiles = listOf(AuraYouTubePlayerClient.IOS)
            )

            val error = runCatching { client.player(VIDEO_ID) }.exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains("video ID mismatch"))
        }
    }

    @Test
    fun retriesRateLimitAndServerFailureWithBoundedBackoff() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setBody(playerResponse(VIDEO_ID)))
            val delays = mutableListOf<Long>()
            val client = AuraYouTubePlayerClient(
                httpClient = okhttp3.OkHttpClient(),
                endpoint = server.url("/player").toString(),
                profiles = listOf(AuraYouTubePlayerClient.IOS),
                retryPolicy = YouTubeRetryPolicy(maxAttempts = 3, initialDelayMs = 10, maxDelayMs = 40),
                sleeper = delays::add
            )

            val result = client.player(VIDEO_ID)

            assertEquals("IOS", result.profile.name)
            assertEquals(3, server.requestCount)
            assertEquals(listOf(10L, 20L), delays)
        }
    }

    @Test
    fun exposesTypedRateLimitAfterRetriesAreExhausted() {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(429)) }
            val client = AuraYouTubePlayerClient(
                httpClient = okhttp3.OkHttpClient(),
                endpoint = server.url("/player").toString(),
                profiles = listOf(AuraYouTubePlayerClient.IOS),
                retryPolicy = YouTubeRetryPolicy(maxAttempts = 2, initialDelayMs = 0, maxDelayMs = 0)
            )

            assertThrows(YouTubePlaybackException.RateLimited::class.java) {
                client.player(VIDEO_ID)
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun classifiesLoginGeoAgeAndUnsupportedCipherFailures() {
        val cases = listOf(
            """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm"}}""" to
                YouTubePlaybackException.LoginRequired::class.java,
            """{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Not available in your country"}}""" to
                YouTubePlaybackException.GeoBlocked::class.java,
            """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Age verification required"}}""" to
                YouTubePlaybackException.AgeRestricted::class.java,
            cipherOnlyResponse(VIDEO_ID) to YouTubePlaybackException.UnsupportedCipher::class.java
        )

        cases.forEach { (body, expectedType) ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                val client = AuraYouTubePlayerClient(
                    httpClient = okhttp3.OkHttpClient(),
                    endpoint = server.url("/player").toString(),
                    profiles = listOf(AuraYouTubePlayerClient.IOS)
                )

                val error = runCatching { client.player(VIDEO_ID) }.exceptionOrNull()

                assertTrue("Expected ${expectedType.simpleName}, got ${error?.javaClass?.simpleName}", expectedType.isInstance(error))
            }
        }
    }

    private fun playerResponse(videoId: String) = """
        {
          "playabilityStatus":{"status":"OK"},
          "videoDetails":{"videoId":"$videoId"},
          "streamingData":{
            "expiresInSeconds":"21600",
            "adaptiveFormats":[
              {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":129000,"url":"https://rr1.googlevideo.com/videoplayback?expire=1999999999&id=one"},
              {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":136000,"url":"https://rr1.googlevideo.com/videoplayback?expire=1999999999&id=two"},
              {"itag":137,"mimeType":"video/mp4; codecs=\"avc1\"","bitrate":2000000,"url":"https://rr1.googlevideo.com/videoplayback?id=video"},
              {"itag":250,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":70000,"signatureCipher":"s=unsupported"}
            ]
          }
        }
    """.trimIndent()

    private fun noFormatsResponse(videoId: String) = """
        {"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"$videoId"},"streamingData":{"adaptiveFormats":[]}}
    """.trimIndent()

    private fun cipherOnlyResponse(videoId: String) = """
        {
          "playabilityStatus":{"status":"OK"},
          "videoDetails":{"videoId":"$videoId"},
          "streamingData":{"adaptiveFormats":[
            {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","signatureCipher":"s=unsupported"}
          ]}
        }
    """.trimIndent()

    companion object {
        private const val VIDEO_ID = "kXYiU_JCYtU"
    }
}
