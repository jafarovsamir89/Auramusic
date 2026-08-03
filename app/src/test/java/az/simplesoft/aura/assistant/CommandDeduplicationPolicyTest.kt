package az.simplesoft.aura.assistant

import az.simplesoft.aura.data.database.AssistantPendingCommandEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDeduplicationPolicyTest {
    private val fingerprint = "same-command"

    @Test
    fun repeatedCommandWithinWindowIsReused() {
        assertTrue(existing("PENDING").let { CommandDeduplicationPolicy.shouldReuse(it, fingerprint, 5_000L) })
        assertTrue(existing("IN_PROGRESS").let { CommandDeduplicationPolicy.shouldReuse(it, fingerprint, 5_000L) })
        assertTrue(existing("COMPLETED").let { CommandDeduplicationPolicy.shouldReuse(it, fingerprint, 5_000L) })
    }

    @Test
    fun sameTextFromAnotherSourceStillUsesTheSameKey() {
        val wakeWordCommand = existing("IN_PROGRESS").copy(source = "wake_word")
        assertTrue(CommandDeduplicationPolicy.shouldReuse(wakeWordCommand, fingerprint, 5_000L))
    }

    @Test
    fun commandAfterWindowIsCreatedAgain() {
        assertFalse(CommandDeduplicationPolicy.shouldReuse(existing("COMPLETED"), fingerprint, 8_001L))
    }

    @Test
    fun failedCommandCanBeRetriedImmediately() {
        assertFalse(CommandDeduplicationPolicy.shouldReuse(existing("FAILED"), fingerprint, 5_000L))
    }

    private fun existing(status: String) = AssistantPendingCommandEntity(
        commandId = "command",
        text = "play",
        fingerprint = fingerprint,
        source = "voice",
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
        attempts = 1,
        requiresUi = false
    )
}
