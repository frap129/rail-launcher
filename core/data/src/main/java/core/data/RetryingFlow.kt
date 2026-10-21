package core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import timber.log.Timber

private const val RETRY_INITIAL_DELAY_MS: Long = 1_000L
private const val RETRY_MAX_DELAY_MS: Long = 60_000L
private const val MAX_BACKOFF_SHIFT: Long = 10L

/**
 * Re-collects this Flow after an upstream failure, doubling the delay between
 * attempts up to [RETRY_MAX_DELAY_MS]. Long-lived app-scope Flows would
 * otherwise stay frozen at their last value for the life of the process.
 */
internal fun <T> Flow<T>.retryWithExponentialBackoff(): Flow<T> = retryWhen { cause, attempt ->
    val delayMs: Long = retryDelayMillis(attempt = attempt)
    Timber.w(cause, "Flow upstream failed; retrying in %d ms", delayMs)
    delay(delayMs)
    true
}

private fun retryDelayMillis(attempt: Long): Long {
    // Cap the shift so a long outage cannot overflow the delay.
    val shift: Int = attempt.coerceAtMost(MAX_BACKOFF_SHIFT).toInt()
    return (RETRY_INITIAL_DELAY_MS shl shift).coerceAtMost(RETRY_MAX_DELAY_MS)
}
