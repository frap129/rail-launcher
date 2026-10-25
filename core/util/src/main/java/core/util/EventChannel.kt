package core.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A one-slot event channel that replays nothing and delivers to active collectors only. */
class EventChannel<T> {
    private val events: MutableSharedFlow<T> = MutableSharedFlow(extraBufferCapacity = 1)

    val flow: SharedFlow<T> = events.asSharedFlow()

    /** Emits [event], suspending while the one-slot buffer is full. */
    suspend fun send(event: T) {
        events.emit(event)
    }
}
