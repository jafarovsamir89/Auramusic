package az.simplesoft.aura.assistant

import android.content.Context
import android.media.AudioManager
import az.simplesoft.aura.data.database.AssistantPendingCommandEntity
import az.simplesoft.aura.data.database.AuraDatabase
import az.simplesoft.aura.playback.PlaybackConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

enum class ActionExecutionStatus { SUCCESS, NEEDS_CLARIFICATION, TEMPORARY_FAILURE, UNSUPPORTED }

sealed interface ActionExecutionResult {
    data object Success : ActionExecutionResult
    data class NeedsClarification(val question: String) : ActionExecutionResult
    data class NotFound(val message: String) : ActionExecutionResult
    data class PermissionRequired(val message: String) : ActionExecutionResult
    data class TemporaryFailure(val message: String) : ActionExecutionResult
    data class Unsupported(val message: String) : ActionExecutionResult
}

interface AssistantActionExecutor {
    suspend fun execute(intent: MusicIntent): ActionExecutionResult
}

data class CommandDecision(
    val intent: MusicIntent?,
    val confidence: Float,
    val negated: Boolean,
    val requiresUi: Boolean,
    val reasons: List<String>
)

/** Boundary-aware recognizer for commands that are safe to run without Compose. */
class LocalCommandClassifier {
    fun classify(raw: String): CommandDecision {
        val text = AssistantTextNormalizer.normalize(raw)
        if (text.isBlank()) return CommandDecision(null, 0f, false, true, listOf("empty"))
        val negated = isNegatedCommand(text)
        if (isDiscussion(text)) return CommandDecision(null, .25f, false, true, listOf("discussion, not imperative"))
        if (text.contains(AssistantTextNormalizer.normalize("пауза")) && (negated || text.contains(AssistantTextNormalizer.normalize("ставь")))) {
            return CommandDecision(MusicIntent.Pause, if (negated) .99f else .96f, negated, negated, listOf("pause phrase"))
        }
        val rules = listOf(
            Rule(MusicIntent.Pause, listOf("пауза", "поставь на паузу", "останови музыку", "pause", "stop the music")),
            Rule(MusicIntent.Play, listOf("продолжи", "продолжить музыку", "возобнови", "resume", "continue playing")),
            Rule(MusicIntent.Next, listOf("следующий трек", "следующая песня", "переключи песню", "next track", "next song")),
            Rule(MusicIntent.Previous, listOf("предыдущий трек", "предыдущая песня", "previous track", "previous song")),
            Rule(MusicIntent.Louder, listOf("громче", "прибавь звук", "louder", "volume up")),
            Rule(MusicIntent.Quieter, listOf("тише", "убавь звук", "quieter", "volume down")),
            Rule(MusicIntent.Mute, listOf("без звука", "выключи звук", "mute"))
        )
        val matched = rules.firstOrNull { rule -> rule.phrases.any { phrase -> matchesPhrase(text, phrase) } }
        return if (matched == null) {
            CommandDecision(null, .35f, negated, true, listOf("not a background-safe command"))
        } else {
            CommandDecision(
                intent = matched.intent,
                confidence = if (negated) .99f else .96f,
                negated = negated,
                requiresUi = negated,
                reasons = listOf("word boundary match", if (negated) "explicit negation" else "imperative phrase")
            )
        }
    }

    private fun matchesPhrase(text: String, phrase: String): Boolean =
        Regex("(?:^|\\s)${Regex.escape(AssistantTextNormalizer.normalize(phrase))}(?:$|\\s)").containsMatchIn(text)

    private fun isNegatedCommand(text: String): Boolean =
        listOf("не", "нет", "не хочу", "don't", "do not", "not", "yox")
            .map(AssistantTextNormalizer::normalize)
            .any { marker -> text == marker || text.startsWith("$marker ") || text.contains(" $marker ") } ||
        Regex("(^|\\s)(не|нет|нехочу|не хочу|don't|do not|not|yox)(?:\\s|$)").containsMatchIn(text) ||
            text.contains("не хочу следующий") || text.contains("не переключай") || text.contains("не ставь")

    private fun isDiscussion(text: String): Boolean =
        containsNormalized(text, "я сказал ему остановиться") || containsNormalized(text, "слово пауза") ||
            containsNormalized(text, "означает перерыв") || containsNormalized(text, "мне не нравится когда музыка") ||
            containsNormalized(text, "перемешай фотографии") ||
        text.contains("я сказал ему") || text.contains("слово пауза") ||
            text.contains("означает перерыв") || text.contains("мне не нравится когда музыка") ||
            text.contains("перемешай фотографии")

    private fun containsNormalized(text: String, phrase: String): Boolean =
        text.contains(AssistantTextNormalizer.normalize(phrase))

    private data class Rule(val intent: MusicIntent, val phrases: List<String>)
}

class PendingVoiceCommandRepository(context: Context) {
    private val dao = AuraDatabase.get(context).stateDao()

    suspend fun enqueue(text: String, source: String, requiresUi: Boolean): AssistantPendingCommandEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fingerprint = fingerprint(text)
        dao.recentCommand(fingerprint, now - DEDUP_WINDOW_MS)?.let { return@withContext it }
        val command = AssistantPendingCommandEntity(
            commandId = UUID.randomUUID().toString(),
            text = text.trim().take(500),
            fingerprint = fingerprint,
            source = source,
            status = if (requiresUi) STATUS_PENDING else STATUS_IN_PROGRESS,
            createdAt = now,
            updatedAt = now,
            requiresUi = requiresUi
        )
        dao.upsertPendingCommand(command)
        command
    }

    suspend fun claimForUi(limit: Int = 20): List<AssistantPendingCommandEntity> = withContext(Dispatchers.IO) {
        dao.pendingUiCommands(limit).mapNotNull { command ->
            command.takeIf {
                dao.updateCommandStatus(it.commandId, STATUS_PENDING, STATUS_IN_PROGRESS, System.currentTimeMillis()) == 1
            }?.copy(status = STATUS_IN_PROGRESS, attempts = command.attempts + 1)
        }
    }

    suspend fun complete(commandId: String) = withContext(Dispatchers.IO) {
        dao.updateCommandStatus(commandId, STATUS_IN_PROGRESS, STATUS_COMPLETED, System.currentTimeMillis())
    }

    suspend fun fail(commandId: String) = withContext(Dispatchers.IO) {
        dao.updateCommandStatus(commandId, STATUS_IN_PROGRESS, STATUS_FAILED, System.currentTimeMillis())
    }

    private fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(AssistantTextNormalizer.normalize(text).toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        private const val DEDUP_WINDOW_MS = 8_000L
    }
}

class AssistantCommandCoordinator(context: Context) {
    private val repository = PendingVoiceCommandRepository(context)
    private val classifier = LocalCommandClassifier()
    private val mutableEvents = MutableSharedFlow<AssistantPendingCommandEntity>(extraBufferCapacity = 8)
    val events = mutableEvents.asSharedFlow()

    suspend fun submit(
        text: String,
        source: String,
        executor: AssistantActionExecutor? = null
    ): ActionExecutionResult = withContext(Dispatchers.IO) {
        val decision = classifier.classify(text)
        val command = repository.enqueue(text, source, requiresUi = decision.requiresUi || decision.intent == null || decision.negated)
        if (decision.intent == null || decision.negated || decision.confidence < .90f || executor == null) {
            mutableEvents.tryEmit(command)
            return@withContext if (decision.negated) {
                ActionExecutionResult.NeedsClarification("Команда отменена отрицанием: ${text.trim()}")
            } else ActionExecutionResult.NeedsClarification("Открой AURA, чтобы уточнить команду")
        }
        val result = executor.execute(decision.intent)
        if (result is ActionExecutionResult.Success) {
            repository.complete(command.commandId)
        } else {
            repository.fail(command.commandId)
            mutableEvents.tryEmit(command)
        }
        result
    }

    suspend fun claimPendingForUi(limit: Int = 20): List<AssistantPendingCommandEntity> = repository.claimForUi(limit)
    suspend fun complete(commandId: String) = repository.complete(commandId)
    suspend fun fail(commandId: String) = repository.fail(commandId)
}

/** Executes only transport-safe player actions while the Activity is closed. */
class BackgroundMusicActionExecutor(context: Context) : AssistantActionExecutor {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playback = PlaybackConnection(context, object : PlaybackConnection.Listener {
        override fun onPlaybackChanged(isPlaying: Boolean, isBuffering: Boolean) = Unit
        override fun onTrackChanged(trackId: String) = Unit
        override fun onPlaybackError(trackId: String?, message: String) = Unit
    })

    override suspend fun execute(intent: MusicIntent): ActionExecutionResult = runCatching {
        when (intent) {
            MusicIntent.Play -> playback.play()
            MusicIntent.Pause -> playback.pause()
            MusicIntent.Next -> playback.next()
            MusicIntent.Previous -> playback.previous()
            MusicIntent.Louder -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            MusicIntent.Quieter -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            MusicIntent.Mute -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            else -> return ActionExecutionResult.Unsupported("Команда требует интерфейс AURA")
        }
        ActionExecutionResult.Success
    }.getOrElse { ActionExecutionResult.TemporaryFailure(it.message ?: "Плеер временно недоступен") }

    fun release() = playback.release()
}
