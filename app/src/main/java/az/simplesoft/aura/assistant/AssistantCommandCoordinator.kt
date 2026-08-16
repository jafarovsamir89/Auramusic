package az.simplesoft.aura.assistant

import android.content.Context
import android.media.AudioManager
import az.simplesoft.aura.data.database.AssistantPendingCommandEntity
import az.simplesoft.aura.data.database.AuraDatabase
import az.simplesoft.aura.playback.PlaybackConnection
import az.simplesoft.aura.domain.music.AuraRepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

enum class ActionExecutionStatus { SUCCESS, NEEDS_CLARIFICATION, TEMPORARY_FAILURE, UNSUPPORTED }

sealed interface ActionExecutionResult {
    data class Success(val message: String? = null) : ActionExecutionResult
    data class Duplicate(val message: String) : ActionExecutionResult
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
        if (matchesAny(text, "включи автопродолжение", "включи авто продолжение", "auto continue on", "enable auto continue")) {
            return CommandDecision(MusicIntent.AutoContinue(true), .96f, negated, true, listOf("auto-continue preference"))
        }
        if (matchesAny(text, "выключи автопродолжение", "выключи авто продолжение", "auto continue off", "disable auto continue")) {
            return CommandDecision(MusicIntent.AutoContinue(false), .96f, negated, true, listOf("auto-continue preference"))
        }
        val rules = listOf(
            Rule(MusicIntent.Pause, listOf("пауза", "поставь на паузу", "останови музыку", "pause", "stop the music")),
            Rule(MusicIntent.Play, listOf("продолжи", "продолжить музыку", "возобнови", "resume", "continue playing")),
            Rule(MusicIntent.Next, listOf("следующий трек", "следующая песня", "следующий канал", "переключи канал", "переключи песню", "next track", "next song", "next station", "change channel")),
            Rule(MusicIntent.Previous, listOf("предыдущий трек", "предыдущая песня", "предыдущий канал", "previous track", "previous song", "previous station")),
            Rule(MusicIntent.Louder, listOf("громче", "прибавь звук", "louder", "volume up")),
            Rule(MusicIntent.Quieter, listOf("тише", "убавь звук", "quieter", "volume down")),
            Rule(MusicIntent.Mute, listOf("без звука", "выключи звук", "mute")),
            Rule(MusicIntent.Unmute, listOf("включи звук", "верни звук", "unmute")),
            Rule(MusicIntent.Like, listOf("поставь лайк", "лайкни", "like this")),
            Rule(MusicIntent.Unlike, listOf("убери лайк", "дизлайк", "unlike")),
            Rule(MusicIntent.Shuffle, listOf("перемешай очередь", "перемешай треки", "shuffle queue")),
            Rule(MusicIntent.Repeat, listOf("повтори трек", "режим повтора", "repeat track", "repeat mode")),
            Rule(MusicIntent.NowPlaying, listOf("что играет", "какой трек играет", "now playing", "what is playing"))
        )
        val matched = rules.firstOrNull { rule -> rule.phrases.any { phrase -> matchesPhrase(text, phrase) } }
        return if (matched == null) {
            CommandDecision(null, .35f, negated, true, listOf("not a background-safe command"))
        } else {
            CommandDecision(
                intent = matched.intent,
                confidence = if (negated) .99f else .96f,
                negated = negated,
                requiresUi = negated || matched.intent in setOf(MusicIntent.Like, MusicIntent.Unlike),
                reasons = listOf("word boundary match", if (negated) "explicit negation" else "imperative phrase")
            )
        }
    }

    private fun matchesAny(text: String, vararg phrases: String): Boolean =
        phrases.any { matchesPhrase(text, it) }

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

sealed interface CommandEnqueueResult {
    data class Created(val command: AssistantPendingCommandEntity) : CommandEnqueueResult
    data class Duplicate(val command: AssistantPendingCommandEntity) : CommandEnqueueResult
}

object CommandDeduplicationPolicy {
    const val WINDOW_MS = 8_000L

    /** Source does not split the idempotency key: wake-word and UI retries are the same command. */
    fun shouldReuse(
        existing: AssistantPendingCommandEntity,
        fingerprint: String,
        now: Long
    ): Boolean = existing.fingerprint == fingerprint &&
        existing.status != PendingVoiceCommandRepository.STATUS_FAILED &&
        now - existing.createdAt in 0..WINDOW_MS
}

interface PendingCommandRepository {
    suspend fun enqueue(text: String, source: String, requiresUi: Boolean): CommandEnqueueResult
    fun observePendingUiCommands(): Flow<List<AssistantPendingCommandEntity>>
    suspend fun claim(commandId: String): AssistantPendingCommandEntity?
    suspend fun complete(commandId: String)
    suspend fun fail(commandId: String)
    suspend fun requeue(commandId: String)
    suspend fun recoverStale(now: Long = System.currentTimeMillis())
}

class PendingVoiceCommandRepository(context: Context) : PendingCommandRepository {
    private val dao = AuraDatabase.get(context).stateDao()

    override suspend fun enqueue(text: String, source: String, requiresUi: Boolean): CommandEnqueueResult = withContext(Dispatchers.IO) {
        commandDedupMutex.withLock {
        val now = System.currentTimeMillis()
        val fingerprint = fingerprint(text)
        val existing = dao.recentCommand(fingerprint, now - DEDUP_WINDOW_MS)
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
        if (existing == null || !CommandDeduplicationPolicy.shouldReuse(existing, fingerprint, now)) {
            dao.upsertPendingCommand(command)
            CommandEnqueueResult.Created(command)
        } else {
            CommandEnqueueResult.Duplicate(existing)
        }
        }
    }

    override fun observePendingUiCommands(): Flow<List<AssistantPendingCommandEntity>> = dao.observePendingUiCommands()

    suspend fun claimForUi(limit: Int = 20): List<AssistantPendingCommandEntity> = withContext(Dispatchers.IO) {
        dao.pendingUiCommands(limit).mapNotNull { command ->
            command.takeIf {
                dao.updateCommandStatus(it.commandId, STATUS_PENDING, STATUS_IN_PROGRESS, System.currentTimeMillis()) == 1
            }?.copy(status = STATUS_IN_PROGRESS, attempts = command.attempts + 1)
        }
    }

    override suspend fun claim(commandId: String): AssistantPendingCommandEntity? = withContext(Dispatchers.IO) {
        val claimed = dao.updateCommandStatus(
            commandId,
            STATUS_PENDING,
            STATUS_IN_PROGRESS,
            System.currentTimeMillis()
        )
        if (claimed != 1) null else dao.pendingCommand(commandId)
    }

    override suspend fun complete(commandId: String) {
        withContext(Dispatchers.IO) {
        dao.updateCommandStatus(commandId, STATUS_IN_PROGRESS, STATUS_COMPLETED, System.currentTimeMillis())
        }
    }

    override suspend fun fail(commandId: String) {
        withContext(Dispatchers.IO) {
        dao.updateCommandStatus(commandId, STATUS_IN_PROGRESS, STATUS_FAILED, System.currentTimeMillis())
        }
    }

    override suspend fun requeue(commandId: String) {
        withContext(Dispatchers.IO) {
        dao.requeueUiCommand(commandId, System.currentTimeMillis())
        }
    }

    override suspend fun recoverStale(now: Long) {
        withContext(Dispatchers.IO) {
        val staleBefore = now - STALE_COMMAND_MS
        dao.recoverStaleUiCommands(staleBefore, now, MAX_ATTEMPTS)
        dao.failExhaustedUiCommands(staleBefore, now, MAX_ATTEMPTS)
        dao.failStaleBackgroundCommands(staleBefore, now)
        }
    }

    private fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(AssistantTextNormalizer.normalize(text).toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val MAX_ATTEMPTS = 3
        private const val STALE_COMMAND_MS = 2 * 60_000L
        private const val DEDUP_WINDOW_MS = CommandDeduplicationPolicy.WINDOW_MS
        private val commandDedupMutex = Mutex()
    }
}

class AssistantCommandCoordinator(
    private val repository: PendingCommandRepository,
    private val classifier: LocalCommandClassifier = LocalCommandClassifier()
) {
    constructor(context: Context) : this(PendingVoiceCommandRepository(context))
    private val mutableEvents = MutableSharedFlow<AssistantPendingCommandEntity>(extraBufferCapacity = 8)
    val events = mutableEvents.asSharedFlow()

    suspend fun submit(
        text: String,
        source: String,
        executor: AssistantActionExecutor? = null
    ): ActionExecutionResult = withContext(Dispatchers.IO) {
        val decision = classifier.classify(text)
        val enqueue = repository.enqueue(text, source, requiresUi = decision.requiresUi || decision.intent == null || decision.negated)
        if (enqueue is CommandEnqueueResult.Duplicate) {
            return@withContext if (enqueue.command.status == PendingVoiceCommandRepository.STATUS_COMPLETED) {
                ActionExecutionResult.Duplicate("Команда уже выполнена")
            } else {
                ActionExecutionResult.Duplicate("Команда уже обрабатывается")
            }
        }
        val command = (enqueue as CommandEnqueueResult.Created).command
        if (decision.intent == null || decision.requiresUi || decision.negated || decision.confidence < .90f || executor == null) {
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

    fun observePendingUiCommands(): Flow<List<AssistantPendingCommandEntity>> = repository.observePendingUiCommands()
    suspend fun claim(commandId: String): AssistantPendingCommandEntity? = repository.claim(commandId)
    suspend fun complete(commandId: String) = repository.complete(commandId)
    suspend fun fail(commandId: String) = repository.fail(commandId)
    suspend fun requeue(commandId: String) = repository.requeue(commandId)
    suspend fun recoverStale(now: Long = System.currentTimeMillis()) = repository.recoverStale(now)
}

/** Executes only transport-safe player actions while the Activity is closed. */
class BackgroundMusicActionExecutor(context: Context) : AssistantActionExecutor {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playback = PlaybackConnection(context, object : PlaybackConnection.Listener {
        override fun onPlaybackChanged(isPlaying: Boolean, isBuffering: Boolean) = Unit
        override fun onTrackChanged(trackId: String) = Unit
        override fun onPlaybackError(trackId: String?, message: String) = Unit
    })
    private val preferences = context.getSharedPreferences("aura_background_actions", Context.MODE_PRIVATE)

    override suspend fun execute(intent: MusicIntent): ActionExecutionResult = runCatching {
        when (intent) {
            MusicIntent.Play -> playback.play()
            MusicIntent.Pause -> playback.pause()
            MusicIntent.Next -> playback.next()
            MusicIntent.Previous -> playback.previous()
            MusicIntent.Louder -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            MusicIntent.Quieter -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            MusicIntent.Mute -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            MusicIntent.Unmute -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            MusicIntent.Shuffle -> {
                val enabled = !preferences.getBoolean("shuffle", false)
                preferences.edit().putBoolean("shuffle", enabled).apply()
                playback.setShuffle(enabled)
            }
            MusicIntent.Repeat -> {
                val next = when (preferences.getString("repeat", AuraRepeatMode.OFF.name)) {
                    AuraRepeatMode.OFF.name -> AuraRepeatMode.ALL
                    AuraRepeatMode.ALL.name -> AuraRepeatMode.ONE
                    else -> AuraRepeatMode.OFF
                }
                preferences.edit().putString("repeat", next.name).apply()
                playback.setRepeat(next)
            }
            MusicIntent.NowPlaying -> {
                val label = playback.nowPlayingLabel() ?: return ActionExecutionResult.TemporaryFailure("Сейчас ничего не играет")
                return ActionExecutionResult.Success(label)
            }
            else -> return ActionExecutionResult.Unsupported("Команда требует интерфейс AURA")
        }
        ActionExecutionResult.Success()
    }.getOrElse { ActionExecutionResult.TemporaryFailure(it.message ?: "Плеер временно недоступен") }

    fun release() = playback.release()
}
