package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandGateTest {
    @Test
    fun `partial hypotheses never execute and final result executes once`() {
        val gate = VoiceCommandGate()
        val commands = buildList {
            gate.onPartial("аура включи")?.let(::add)
            gate.onPartial("аура включи руки вверх")?.let(::add)
            gate.onFinal("аура включи руки вверх")?.let(::add)
            gate.onFinal("аура включи руки вверх")?.let(::add)
        }

        assertEquals(listOf("аура включи руки вверх"), commands)
    }

    @Test
    fun `new listening session may execute the same phrase again`() {
        val gate = VoiceCommandGate()

        assertEquals("аура пауза", gate.onFinal("аура пауза"))
        gate.reset()

        assertEquals("аура пауза", gate.onFinal("аура пауза"))
    }
}
