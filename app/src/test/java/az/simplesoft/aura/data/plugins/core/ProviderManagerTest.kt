package az.simplesoft.aura.data.plugins.core

import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.TrackCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderManagerTest {
    @Test
    fun collectsParallelResultsAndKeepsPartialSuccess() = runBlocking {
        val first = FakePlugin("first", 100) {
            delay(40)
            PluginResult.Success(listOf(candidate("first", "1", "Numb")))
        }
        val failing = FakePlugin("failing", 80) {
            PluginResult.Failure(PluginFailureReason.NETWORK, "offline")
        }
        val manager = ProviderManager(setOf(first, failing), ProviderPolicy(searchTimeoutMs = 500))

        val result = manager.search(MusicSearchRequest("Linkin Park Numb"))

        assertTrue(result is PluginResult.Success)
        assertEquals(1, (result as PluginResult.Success).value.size)
        assertEquals("1", result.diagnostics["partialFailures"])
        assertEquals(1L, manager.stats().getValue("first").successes)
        assertEquals(1, manager.stats().getValue("failing").consecutiveFailures)
    }

    @Test
    fun cancelsSlowPluginAtItsTimeout() = runBlocking {
        val slow = FakePlugin("slow", 100) {
            delay(300)
            PluginResult.Success(listOf(candidate("slow", "1", "Late")))
        }
        val manager = ProviderManager(setOf(slow), ProviderPolicy(searchTimeoutMs = 25))

        val result = manager.search(MusicSearchRequest("test"))

        assertTrue(result is PluginResult.Failure)
        assertEquals(PluginFailureReason.TIMEOUT, (result as PluginResult.Failure).reason)
        assertEquals(PluginFailureReason.TIMEOUT, manager.stats().getValue("slow").lastFailure)
    }

    @Test
    fun disabledPluginIsNotCalled() = runBlocking {
        var called = false
        val plugin = FakePlugin("disabled", 100) {
            called = true
            PluginResult.Success(emptyList())
        }
        val manager = ProviderManager(setOf(plugin))
        assertTrue(manager.setEnabled("disabled", false))

        val result = manager.search(MusicSearchRequest("test", preferredProviderId = "disabled"))

        assertTrue(result is PluginResult.Failure)
        assertFalse(called)
    }

    private fun candidate(provider: String, id: String, title: String) = TrackCandidate(
        providerId = provider,
        id = id,
        title = title,
        artist = "Linkin Park",
        detailUrl = "https://example.test/$id"
    )
}

private class FakePlugin(
    override val id: String,
    override val priority: Int,
    private val searchResult: suspend () -> PluginResult<List<TrackCandidate>>
) : MusicPlugin {
    override val displayName: String = id
    override val capabilities: Set<PluginCapability> = setOf(PluginCapability.SEARCH, PluginCapability.STREAM)

    override suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>> = searchResult()

    override suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource> =
        PluginResult.Failure(PluginFailureReason.NOT_PLAYABLE, "not used")
}
