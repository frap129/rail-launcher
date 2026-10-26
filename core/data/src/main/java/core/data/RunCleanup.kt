package core.data

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** Runs [action], rethrowing cancellation and logging any other failure with [message]. */
internal suspend fun runCleanup(message: String, action: suspend () -> Unit) {
    try {
        action()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Timber.e(exception, message)
    }
}
