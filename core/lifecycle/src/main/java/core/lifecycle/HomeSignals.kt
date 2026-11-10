package core.lifecycle

import core.ui.model.data.HomeIntent
import core.util.EventChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * Pending HOME intent events for the launcher UI, owned by the activity.
 *
 * Each signal carries whether the launcher was already resumed when the HOME intent arrived, so
 * the UI can tell a Home press on Home from one that returns to Rail from another app.
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
 * would wrongly reset the UI to Home. Conflation is safe because only the
 * latest pending press matters.
 *
 * [events] has a single consumer: a second concurrent collector would steal
 * signals from the first.
 */
class HomeSignals {
    private val eventChannel: EventChannel<HomeIntent> = EventChannel(capacity = Channel.CONFLATED)

    val events: Flow<HomeIntent> = eventChannel.flow

    fun signal(wasResumed: Boolean) {
        eventChannel.trySend(HomeIntent(wasResumed = wasResumed))
    }
}
