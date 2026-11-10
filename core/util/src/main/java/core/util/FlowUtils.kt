package core.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * How long a flow started with `SharingStarted.WhileSubscribed` keeps its upstream
 * subscription after the last collector leaves, so transient collector loss (for
 * example a configuration change) does not restart it.
 */
const val WHILE_SUBSCRIBED_TIMEOUT_MS: Long = 5_000L

/**
 * A shared flow that replays [initialValue] and re-emits every write, including one equal to the
 * last, for state whose observers must react to updates that leave the value unchanged.
 */
fun <T> reemittingFlow(initialValue: T): MutableSharedFlow<T> = MutableSharedFlow<T>(
    replay = 1,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
).apply {
    tryEmit(initialValue)
}
