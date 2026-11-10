package core.lifecycle

import core.ui.model.data.LauncherSignal
import core.util.EventChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Pending launcher signals for the launcher UI, owned by the activity.
 *
 * Each HOME signal carries whether the launcher was already resumed when the HOME intent arrived,
 * so the UI can tell a Home press on Home from one that returns to Rail from another app. The
 * Search entry and Typed characters use the same signal flow, so every signal reaches the launcher
 * through one path.
 *
 * The launcher activity uses `singleTask`, so a HOME intent that arrives while
 * the activity is being recreated is redelivered by the platform to the new
 * instance through `onNewIntent`, and each instance queues signals for its own
 * collector. The activity's creation intent intentionally does not signal a HOME
 * intent, so rotating the device never resets Home scroll.
 *
 * Signals sent before a collector attaches stay queued until one does, and each
 * signal is consumed exactly once. HOME and Search signals use a conflated
 * [EventChannel] instead of a replaying flow so an already consumed signal is never
 * re-delivered to a later collector (e.g. a composition that re-collects after
 * recreation), which would wrongly reset the UI to Home. Conflation is safe because
 * only the latest pending signal matters. Typed characters use a buffered channel
 * instead, so fast typing never loses a character.
 *
 * [events] has a single consumer: a second concurrent collector would steal
 * signals from the first.
 */
class LauncherSignals {
    private val signalChannel: EventChannel<LauncherSignal> = EventChannel(capacity = Channel.CONFLATED)
    private val typedCharacterChannel: EventChannel<String> = EventChannel(capacity = Channel.UNLIMITED)

    val events: Flow<LauncherSignal> = merge(
        signalChannel.flow,
        typedCharacterChannel.flow.map { character -> LauncherSignal.TypedCharacter(character = character) }
    )

    fun signal(signal: LauncherSignal) {
        when (signal) {
            is LauncherSignal.TypedCharacter -> typedCharacterChannel.trySend(signal.character)
            is LauncherSignal.Home, LauncherSignal.OpenSearch -> signalChannel.trySend(signal)
        }
    }
}
