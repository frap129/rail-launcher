package core.widgets

import core.data.widgets.PlacedWidget
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.iconOr
import core.data.widgets.labelOr

/** The provider limits and actions one placed App Widget offers in its menu. */
data class AppWidgetMenuCapabilities(val resizeBounds: ResizeBounds?, val isReconfigurable: Boolean) {
    /** Whether Resize is offered right now. */
    fun canResize(widget: PlacedWidget.AppWidget): Boolean =
        widget.availability == WidgetAvailability.Available && resizeBounds?.isResizable == true
}

/** The menu capabilities of [widget], using [resizeBounds] to override the provider's own bounds for a stack. */
fun appWidgetMenuCapabilities(
    widget: PlacedWidget.AppWidget,
    info: WidgetProviderInfo?,
    resizeBounds: ResizeBounds? = info?.let(ResizeBounds::from)
): AppWidgetMenuCapabilities = AppWidgetMenuCapabilities(
    resizeBounds = resizeBounds,
    isReconfigurable = widget.availability == WidgetAvailability.Available &&
        info != null &&
        info.isReconfigurable &&
        info.hasConfigureActivity
)

/** The App Widget's display model, falling back to the stored provider data when [info] is gone. */
fun PlacedWidget.AppWidget.toAppWidgetModel(info: WidgetProviderInfo?): AppWidgetModel = AppWidgetModel(
    id = id,
    appWidgetId = appWidgetId,
    provider = provider.key,
    label = info.labelOr(provider = provider),
    icon = info.iconOr(provider = provider),
    minHeightDp = info?.minHeightDp ?: defaultHeightDp ?: 0,
    isWorkProfile = info?.isWorkProfile ?: false,
    availability = availability
)
