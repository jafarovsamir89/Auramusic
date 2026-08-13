package az.simplesoft.aura.assistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Delivers a wake-word command to the already running app process without opening a chat screen. */
internal object AuraWakeWordBus {
    private val mutableCommands = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val commands = mutableCommands.asSharedFlow()

    fun submit(command: String): Boolean {
        if (mutableCommands.subscriptionCount.value == 0) return false
        return mutableCommands.tryEmit(command)
    }
}
