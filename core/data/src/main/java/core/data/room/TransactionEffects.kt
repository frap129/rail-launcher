package core.data.room

import core.data.runCleanup
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Actions that must run only after the surrounding database transaction commits. */
internal class TransactionEffects : AbstractCoroutineContextElement(Key) {
    private val actions: MutableList<suspend () -> Unit> = mutableListOf()

    /** Queues [action] to run after the transaction commits. */
    fun afterCommit(action: suspend () -> Unit) {
        actions += action
    }

    /** Runs the queued actions best-effort, so one failure cannot skip the rest. */
    suspend fun run() {
        actions.forEach { action -> runCleanup(message = "Failed to run a post-commit action", action = action) }
        actions.clear()
    }

    /** The context key of [TransactionEffects]. */
    object Key : CoroutineContext.Key<TransactionEffects>
}
