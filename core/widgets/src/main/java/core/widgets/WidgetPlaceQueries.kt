package core.widgets

import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace

/** The place holding the Header, optionally only the page with [widgetId]. */
fun List<WidgetPlace>.headerPlace(widgetId: Long? = null): WidgetPlace? = firstOrNull { place ->
    place.widgets.any { widget -> widget is PlacedWidget.Header && (widgetId == null || widget.id == widgetId) }
}

fun List<WidgetPlace>.hasHeader(): Boolean = headerPlace() != null
