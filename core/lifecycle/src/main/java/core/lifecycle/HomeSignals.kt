package core.lifecycle

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Pending HOME intent events for the launcher UI, owned by the activity.
 *
 * The launcher activity uses `singleTask`, so a HOME intent that arrives while
 * the activity is being recreated is redelivered by the platform to the new
 * instance through `onNewIntent`, and each instance queues signals for its own
 * collector. The activity's creation intent intentionally does not signal, so
 * rotating the device never resets Home scroll.
 *
 * Signals sent before a collector attaches stay queued until one does, and each
 * signal is consumed exactly once. A [Channel] is used instead of a replaying
 * SharedFlow so an already consumed signal is never re-delivered to a later
 * collector (e.g. a composition that re-collects after recreation), which would
 * wrongly reset the UI to Home. The channel is conflated because the reaction
 * is idempotent: pending duplicates only need to trigger it once.
 *
 * [events] has a single consumer: a second concurrent collector would steal
 * signals from the first.
 */
class HomeSignals {
    private val channel: Channel<Unit> = Channel(capacity = Channel.CONFLATED)

    val events: Flow<Unit> = channel.receiveAsFlow()

    fun signal() {
        channel.trySend(Unit)
    }
}
