package feature.launcher

import android.app.Activity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** A result delivered to Rail for one of its App Widget activity requests. */
data class AppWidgetResult(val requestCode: Int, val resultCode: Int) {
    val isOk: Boolean
        get() = resultCode == Activity.RESULT_OK
}

/** Relays App Widget activity results from the Activity to the Widget Area. */
class AppWidgetResults {
    private val channel: Channel<AppWidgetResult> = Channel(Channel.BUFFERED)
    val results: Flow<AppWidgetResult> = channel.receiveAsFlow()

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        channel.trySend(AppWidgetResult(requestCode = requestCode, resultCode = resultCode))
    }

    companion object {
        const val REQUEST_CODE: Int = 1001
    }
}
