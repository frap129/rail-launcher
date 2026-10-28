package core.data.testing

import io.mockk.MockKAnswerScope
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.spyk

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

    /** Runs [onFailure] and throws the pending failure once, then runs [delegate]. */
    suspend fun <T> failThen(onFailure: () -> Unit, delegate: suspend () -> T): T {
        val failure: Throwable = pending ?: return delegate()
        pending = null
        onFailure()
        throw failure
    }
}

/**
 * A spy on this object whose call stubbed by [stub] fails once with [failure], running
 * [onFailure] first, and then delegates to this object through [delegate] with the call's
 * arguments. MockK's own `callOriginal` does not work for Room's suspend DAO functions.
 */
inline fun <reified T : Any> T.failOnce(
    failure: Throwable,
    noinline onFailure: () -> Unit = {},
    noinline stub: suspend MockKMatcherScope.(T) -> Unit,
    noinline delegate: suspend MockKAnswerScope<*, *>.(T) -> Unit
): T {
    val original: T = this
    val mockSpy: T = spyk(original)
    val failOnce: FailOnce = FailOnce().apply { failWith(failure) }
    coEvery { stub(mockSpy) } coAnswers {
        failOnce.failThen(onFailure = onFailure) { this.delegate(original) }
    }
    return mockSpy
}
