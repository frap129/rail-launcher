package feature.launcher.widgets

import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetLifecycleReconciler
import core.ui.R as CoreUiR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Starts the add flow's events, restore and provider wiring every Widget host needs. */
fun IWidgetAddController.startWidgetWiring(
    scope: CoroutineScope,
    appWidgetHost: IAppWidgetHostSource,
    lifecycleReconciler: IWidgetLifecycleReconciler,
    onEvent: suspend (WidgetEvent) -> Unit
) {
    scope.launch { events.collect(onEvent) }
    scope.launch { restore() }
    scope.launch { appWidgetHost.providers.collect { providers -> updateProviders(providers = providers) } }
    scope.launch {
        lifecycleReconciler.removedIds.collect { appWidgetId ->
            onEvent(WidgetEvent.RemoveHostView(appWidgetId = appWidgetId))
        }
    }
}

/** Leaves Resize, stores its height and reports the new size; false when no Resize was active. */
fun IWidgetResizeController.finishResizeAndReport(scope: CoroutineScope, onReport: suspend (WidgetSizeReport) -> Unit): Boolean {
    val finish: ResizeFinish = finishResize() ?: return false
    scope.launch { persistResize(finish = finish)?.let { report -> onReport(report) } }
    return true
}

/** Removes a placed Widget, frees its id and reports it to the host. */
suspend fun IRemoveAppWidget.removeWidgetAndReport(widgetId: Long, onEvent: suspend (WidgetEvent) -> Unit): AppWidgetId? {
    val appWidgetId: AppWidgetId = invoke(widgetId = widgetId) ?: return null
    onEvent(WidgetEvent.RemoveHostView(appWidgetId = appWidgetId))
    onEvent(WidgetEvent.ShowMessage(message = CoreUiR.string.widget_removed))
    return appWidgetId
}

/** The size report in the shared Widget event shape. */
fun WidgetSizeReport.toWidgetEvent(): WidgetEvent.ReportWidgetSize = WidgetEvent.ReportWidgetSize(
    appWidgetIds = appWidgetIds,
    widthDp = widthDp,
    heightDp = heightDp
)
