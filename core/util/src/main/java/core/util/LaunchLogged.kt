package core.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/** Launches [action] in [ViewModel.viewModelScope], logging [message] with any failure. */
fun ViewModel.launchLogged(message: String, action: suspend () -> Unit) {
    viewModelScope.launch {
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, message)
        }
    }
}
