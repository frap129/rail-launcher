package core.data.time

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Runs [write] on [dispatcher] with the clock source's current time in epoch milliseconds. */
internal suspend fun withClockNow(clockSource: IClockSource, dispatcher: CoroutineDispatcher, write: suspend (nowEpochMs: Long) -> Unit) {
    withContext(dispatcher) { write(clockSource.now().toEpochMilli()) }
}
