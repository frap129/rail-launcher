package core.lifecycle

import core.util.EventChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

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
 * signal is consumed exactly once. A conflated [EventChannel] is used instead of
 * a replaying flow so an already consumed signal is never re-delivered to a
 * later collector (e.g. a composition that re-collects after recreation), which
 * would wrongly reset the UI to Home. Conflation is safe because the reaction
 * is idempotent: pending duplicates only need to trigger it once.
 *
 * [events] has a single consumer: a second concurrent collector would steal
 * signals from the first.
 */
class HomeSignals {
    private val eventChannel: EventChannel<Unit> = EventChannel(capacity = Channel.CONFLATED)

    val events: Flow<Unit> = eventChannel.flow

    fun signal() {
        eventChannel.trySend(Unit)
    }
}
