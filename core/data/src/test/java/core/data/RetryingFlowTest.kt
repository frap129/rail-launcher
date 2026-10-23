package core.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val EXPECTED_COLLECTION_COUNT: Int = 9

private val EXPECTED_COLLECTION_TIMES_MS: List<Long> = listOf(
    0L,
    1_000L,
    3_000L,
    7_000L,
    15_000L,
    31_000L,
    63_000L,
    123_000L,
    183_000L
)

class RetryingFlowTest {
    @Test
    fun `backs off exponentially up to a one minute cap`() = runTest {
        val inputRemainingFailures: AtomicInteger = AtomicInteger(EXPECTED_COLLECTION_COUNT - 1)
        val actualObservedCollectionTimes: MutableList<Long> = mutableListOf()
        val inputFailingUpstream: Flow<Unit> = flow {
            actualObservedCollectionTimes += currentTime
            if (inputRemainingFailures.getAndDecrement() > 0) {
                throw IllegalStateException("Upstream failure")
            }
            emit(Unit)
        }
        inputFailingUpstream.retryWithExponentialBackoff().collect()
        assertEquals(EXPECTED_COLLECTION_TIMES_MS, actualObservedCollectionTimes)
    }
}
