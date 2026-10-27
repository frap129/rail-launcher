package feature.launcher.widgets

import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.iconOr
import core.data.widgets.labelOr
import core.widgets.AppWidgetModel

/** The provider actions one placed App Widget offers in its menu. */
data class AppWidgetMenuCapabilities(val isReconfigurable: Boolean)

/** The menu capabilities of [widget], whose provider info is [info]. */
fun appWidgetMenuCapabilities(widget: PlacedWidget.AppWidget, info: WidgetProviderInfo?): AppWidgetMenuCapabilities {
    val isReconfigurable: Boolean = widget.availability == WidgetAvailability.Available &&
        info != null &&
        info.isReconfigurable &&
        info.hasConfigureActivity
    return AppWidgetMenuCapabilities(isReconfigurable = isReconfigurable)
}

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
