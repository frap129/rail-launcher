package core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/** Launches [action] in [this] scope, logging [message] with any failure. */
fun CoroutineScope.launchLogged(message: String, action: suspend () -> Unit) {
    launch {
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, message)
        }
    }
}
