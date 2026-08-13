package az.simplesoft.aura.assistant

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraWakeWordBusTest {
    @Test
    fun `bare wake word is delivered as activation event`() = runBlocking {
        val received = async { AuraWakeWordBus.events.first() }
        yield()

        assertTrue(AuraWakeWordBus.submitActivation())
        assertEquals(AuraWakeWordBus.Event.Activated, received.await())
    }
}
