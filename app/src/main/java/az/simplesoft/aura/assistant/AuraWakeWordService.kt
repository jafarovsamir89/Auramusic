package az.simplesoft.aura.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import az.simplesoft.aura.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class WakeWordServiceState {
    DISABLED, STARTING, WAITING_FOR_WAKE_WORD, LISTENING_FOR_COMMAND, EXECUTING, COOLDOWN, RECOVERING, ERROR
}

/**
 * User-enabled foreground microphone service. It stays active while the app is
 * backgrounded or the display is off, and executes only utterances prefixed by
 * the wake word «АУРА» / "AURA". Android always shows an ongoing notification.
 */
class AuraWakeWordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recognizer: OfflineSpeechRecognizer
    private lateinit var audioGate: WakeWordAudioGate
    private lateinit var coordinator: AssistantCommandCoordinator
    private lateinit var actionExecutor: BackgroundMusicActionExecutor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var waitingForFollowUp = false
    private var followUpExpiresAt = 0L
    private var cooldownAfterCommand = false
    private var failureStreak = 0
    @Volatile var state: WakeWordServiceState = WakeWordServiceState.DISABLED
        private set

    override fun onCreate() {
        super.onCreate()
        state = WakeWordServiceState.STARTING
        coordinator = AssistantCommandCoordinator(this)
        actionExecutor = BackgroundMusicActionExecutor(this)
        audioGate = WakeWordAudioGate(this)
        recognizer = OfflineSpeechRecognizer(
            context = this,
            onCommand = ::handleUtterance,
            onState = { listening -> state = if (listening) WakeWordServiceState.LISTENING_FOR_COMMAND else WakeWordServiceState.WAITING_FOR_WAKE_WORD },
            onTerminal = ::handleTerminal,
            preferOnDevice = true
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            state = WakeWordServiceState.DISABLED
            audioGate.stop()
            recognizer.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state = WakeWordServiceState.ERROR
            Log.w(TAG, "Wake-word service cannot start without RECORD_AUDIO")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START && state !in setOf(WakeWordServiceState.STARTING, WakeWordServiceState.DISABLED, WakeWordServiceState.ERROR)) {
            return START_STICKY
        }
        promoteToForeground()
        state = WakeWordServiceState.WAITING_FOR_WAKE_WORD
        beginListening(0L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audioGate.stop()
        recognizer.destroy()
        actionExecutor.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleUtterance(utterance: String) {
        val commandAfterWakeWord = WakeWordMatcher.commandAfterWakeWord(utterance)
        when {
            waitingForFollowUp && System.currentTimeMillis() <= followUpExpiresAt -> {
                waitingForFollowUp = false
                followUpExpiresAt = 0L
                cooldownAfterCommand = true
                submitToCoordinator(utterance)
            }
            waitingForFollowUp -> {
                waitingForFollowUp = false
                followUpExpiresAt = 0L
            }
            commandAfterWakeWord == null -> Unit
            commandAfterWakeWord.isBlank() -> {
                if (AuraWakeWordBus.submitActivation()) {
                    waitingForFollowUp = false
                    followUpExpiresAt = 0L
                    cooldownAfterCommand = true
                    state = WakeWordServiceState.EXECUTING
                    Log.i(TAG, "Wake-word activation routed to active AURA session")
                } else {
                    waitingForFollowUp = true
                    followUpExpiresAt = System.currentTimeMillis() + FOLLOW_UP_WINDOW_MS
                }
            }
            else -> {
                cooldownAfterCommand = true
                submitToCoordinator(commandAfterWakeWord)
            }
        }
    }

    private fun submitToCoordinator(command: String) {
        state = WakeWordServiceState.EXECUTING
        if (AuraWakeWordBus.submit(command)) {
            Log.i(TAG, "Wake-word command routed to active AURA session")
            cooldownAfterCommand = true
            return
        }
        serviceScope.launch {
            val result = coordinator.submit(command, source = "wake_word", executor = actionExecutor)
            Log.i(TAG, "Background command result: ${result.javaClass.simpleName}")
        }
    }

    private fun handleTerminal() {
        if (waitingForFollowUp && System.currentTimeMillis() > followUpExpiresAt) {
            waitingForFollowUp = false
            followUpExpiresAt = 0L
        }
        val delay = when {
            cooldownAfterCommand -> COMMAND_COOLDOWN_MS.also { cooldownAfterCommand = false }
            waitingForFollowUp -> FOLLOW_UP_DELAY_MS
            else -> RETRY_DELAY_MS
        }
        state = if (delay >= COMMAND_COOLDOWN_MS) WakeWordServiceState.COOLDOWN else WakeWordServiceState.RECOVERING
        beginListening(delay)
    }

    private fun beginListening(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            runCatching {
                audioGate.start(
                    scope = serviceScope,
                    onSpeech = ::onSpeechDetected,
                    onFailure = { error ->
                        serviceScope.launch {
                            handleGateFailure(error)
                        }
                    }
                )
            }
                .onSuccess { failureStreak = 0 }
                .onFailure { error ->
                    failureStreak += 1
                    Log.w(TAG, "Wake-word microphone gate failed to start", error)
                    if (failureStreak >= MAX_RETRY_STREAK) {
                        state = WakeWordServiceState.ERROR
                        beginListening(LONG_RECOVERY_DELAY_MS)
                        failureStreak = 0
                    } else {
                        state = WakeWordServiceState.RECOVERING
                        beginListening(RETRY_DELAY_MS * failureStreak)
                    }
                }
        }, delayMs)
    }

    private fun onSpeechDetected() {
        serviceScope.launch {
            state = WakeWordServiceState.LISTENING_FOR_COMMAND
            runCatching { recognizer.start() }
                .onSuccess { failureStreak = 0 }
                .onFailure { error ->
                    failureStreak += 1
                    Log.w(TAG, "Wake-word recognizer failed after speech gate", error)
                    handleTerminal()
                }
        }
    }

    private fun handleGateFailure(error: Throwable) {
        failureStreak += 1
        Log.w(TAG, "Wake-word microphone gate stopped unexpectedly", error)
        state = WakeWordServiceState.RECOVERING
        val delay = if (failureStreak >= MAX_RETRY_STREAK) {
            failureStreak = 0
            LONG_RECOVERY_DELAY_MS
        } else {
            RETRY_DELAY_MS * failureStreak
        }
        beginListening(delay)
    }

    private fun promoteToForeground() {
        createNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AURA слушает")
            .setContentText("Скажите «АУРА», затем команду")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AURA голосовая активация", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Показывает, когда AURA ожидает ключевое слово"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AuraWakeWord"
        private const val CHANNEL_ID = "aura_wake_word"
        private const val NOTIFICATION_ID = 207
        private const val RETRY_DELAY_MS = 400L
        private const val FOLLOW_UP_DELAY_MS = 250L
        private const val FOLLOW_UP_WINDOW_MS = 4_000L
        private const val COMMAND_COOLDOWN_MS = 8_000L
        private const val MAX_RETRY_STREAK = 6
        private const val LONG_RECOVERY_DELAY_MS = 60_000L
        private const val ACTION_START = "az.simplesoft.aura.action.START_WAKE_WORD"
        private const val ACTION_STOP = "az.simplesoft.aura.action.STOP_WAKE_WORD"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AuraWakeWordService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AuraWakeWordService::class.java).setAction(ACTION_STOP))
        }
    }
}
