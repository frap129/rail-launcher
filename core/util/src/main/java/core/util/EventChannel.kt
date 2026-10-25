package core.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** A single-consumer event channel that queues events until a collector attaches. */
class EventChannel<T>(capacity: Int = Channel.BUFFERED) {
    private val channel: Channel<T> = Channel(capacity = capacity)

    val flow: Flow<T> = channel.receiveAsFlow()

    /** Emits [event], suspending while the channel is full. */
    suspend fun send(event: T) {
        channel.send(event)
    }

    /** Emits [event] without suspending; false when the channel is full. */
    fun trySend(event: T): Boolean = channel.trySend(event).isSuccess
}
