package core.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import core.data.widgets.AppWidgetHostCallbacks
import core.ui.widgets.RailAppWidgetHostView

/** The AppWidgetHost Rail owns for the process lifetime. */
class RailAppWidgetHost(context: Context, hostId: Int, private val callbacks: AppWidgetHostCallbacks) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo): AppWidgetHostView =
        RailAppWidgetHostView(context)

    override fun onProvidersChanged() {
        super.onProvidersChanged()
        callbacks.onProvidersChanged()
    }
}
