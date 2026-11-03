package core.data.testing

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent

/**
 * Lets all pending work finish. Background-scope jobs only run through [runCurrent], while
 * [advanceUntilIdle] drains foreground work scheduled for later.
 */
fun TestScope.finishPendingWork() {
    advanceUntilIdle()
    runCurrent()
}
