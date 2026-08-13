package az.simplesoft.aura.assistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Delivers a wake-word command to the already running app process without opening a chat screen. */
internal object AuraWakeWordBus {
    sealed interface Event {
        data object Activated : Event
        data class Command(val text: String) : Event
    }

    private val mutableEvents = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    fun submitActivation(): Boolean {
        if (mutableEvents.subscriptionCount.value == 0) return false
        return mutableEvents.tryEmit(Event.Activated)
    }

    fun submit(command: String): Boolean {
        if (mutableEvents.subscriptionCount.value == 0) return false
        return mutableEvents.tryEmit(Event.Command(command))
    }
}
