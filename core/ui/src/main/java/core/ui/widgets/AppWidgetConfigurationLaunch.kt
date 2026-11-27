package core.ui.widgets

import androidx.activity.ComponentActivity
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.util.attemptLaunch

/** Starts [id]'s configuration activity, calling [onLaunchFailed] when no Activity can start it. */
fun launchAppWidgetConfiguration(
    activity: ComponentActivity?,
    appWidgetHostSource: IAppWidgetHostSource,
    id: AppWidgetId,
    requestCode: Int,
    onLaunchFailed: () -> Unit
) {
    val didLaunch: Boolean = activity != null &&
        attemptLaunch {
            appWidgetHostSource.startConfiguring(activity = activity, id = id, requestCode = requestCode)
        }
    if (!didLaunch) {
        onLaunchFailed()
    }
}
