package core.data.testing

/** Fails the next call once, then lets later calls through. */
class FailOnce {
    private var pending: Throwable? = null

    /** Makes the next call throw [failure]. */
    fun failWith(failure: Throwable) {
        pending = failure
    }

    /** Throws and clears the pending failure, or returns when none is pending. */
    fun throwIfPending() {
        val failure: Throwable = pending ?: return
        pending = null
        throw failure
    }
}
