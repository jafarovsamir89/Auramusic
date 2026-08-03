package az.simplesoft.aura.assistant

import az.simplesoft.aura.data.database.AssistantPendingCommandEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AssistantCommandCoordinatorTest {
    @Test
    fun repeatedBackgroundCommandExecutesOnlyOnce() = runBlocking {
        val repository = FakePendingCommandRepository()
        val coordinator = AssistantCommandCoordinator(repository)
        var executions = 0
        val executor = object : AssistantActionExecutor {
            override suspend fun execute(intent: MusicIntent): ActionExecutionResult {
                executions += 1
                assertEquals(MusicIntent.Next, intent)
                return ActionExecutionResult.Success()
            }
        }

        val first = coordinator.submit("next track", "test", executor)
        val second = coordinator.submit("next track", "test", executor)

        assertTrue(first is ActionExecutionResult.Success)
        assertTrue(second is ActionExecutionResult.Duplicate)
        assertEquals(1, executions)
        assertEquals(1, repository.commands.size)
        assertEquals(PendingVoiceCommandRepository.STATUS_COMPLETED, repository.commands.single().status)
    }

    private class FakePendingCommandRepository : PendingCommandRepository {
        val commands = mutableListOf<AssistantPendingCommandEntity>()
        private var now = 1_000L

        override suspend fun enqueue(text: String, source: String, requiresUi: Boolean): CommandEnqueueResult {
            val fingerprint = text.trim().lowercase()
            val existing = commands.lastOrNull { it.fingerprint == fingerprint }
            if (existing != null && CommandDeduplicationPolicy.shouldReuse(existing, fingerprint, now)) {
                return CommandEnqueueResult.Duplicate(existing)
            }
            val command = AssistantPendingCommandEntity(
                commandId = UUID.randomUUID().toString(),
                text = text,
                fingerprint = fingerprint,
                source = source,
                status = if (requiresUi) PendingVoiceCommandRepository.STATUS_PENDING else PendingVoiceCommandRepository.STATUS_IN_PROGRESS,
                createdAt = now,
                updatedAt = now,
                requiresUi = requiresUi
            )
            commands += command
            return CommandEnqueueResult.Created(command)
        }

        override fun observePendingUiCommands(): Flow<List<AssistantPendingCommandEntity>> = emptyFlow()
        override suspend fun claim(commandId: String): AssistantPendingCommandEntity? = commands.firstOrNull { it.commandId == commandId }
        override suspend fun complete(commandId: String) = update(commandId, PendingVoiceCommandRepository.STATUS_COMPLETED)
        override suspend fun fail(commandId: String) = update(commandId, PendingVoiceCommandRepository.STATUS_FAILED)
        override suspend fun requeue(commandId: String) = update(commandId, PendingVoiceCommandRepository.STATUS_PENDING)
        override suspend fun recoverStale(now: Long) = Unit

        private fun update(commandId: String, status: String) {
            val index = commands.indexOfFirst { it.commandId == commandId }
            if (index >= 0) commands[index] = commands[index].copy(status = status, updatedAt = now)
        }
    }
}
