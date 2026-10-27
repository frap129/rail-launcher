package feature.launcher.widgets

import android.app.Activity
import core.util.EventChannel
import kotlinx.coroutines.flow.Flow

/** A result delivered to Rail for one of its App Widget activity requests. */
data class AppWidgetResult(val requestCode: Int, val resultCode: Int) {
    val isOk: Boolean
        get() = resultCode == Activity.RESULT_OK
}

/** Relays App Widget activity results from the Activity to the Widget Area. */
class AppWidgetResults {
    private val eventChannel: EventChannel<AppWidgetResult> = EventChannel()
    val results: Flow<AppWidgetResult> = eventChannel.flow

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        eventChannel.trySend(AppWidgetResult(requestCode = requestCode, resultCode = resultCode))
    }

    companion object {
        const val REQUEST_CODE: Int = 1001
        const val RECONFIGURE_REQUEST_CODE: Int = 1002
    }
}
