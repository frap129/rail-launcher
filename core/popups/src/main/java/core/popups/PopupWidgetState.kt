package core.popups

import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetProvider
import core.widgets.AppWidgetModel

/** The largest share of the window height a Pop-up widget may take. */
const val POPUP_WIDGET_MAX_HEIGHT_FRACTION: Float = 0.6f

/** The Pop-up height cap for a window [windowHeightDp] tall. */
fun popupWidgetMaxHeightDp(windowHeightDp: Int): Int = (windowHeightDp * POPUP_WIDGET_MAX_HEIGHT_FRACTION).toInt()

/** The App Widget shown at the top of a Pop-up. */
data class PopupWidgetState(
    val widgetId: Long,
    val placeId: Long,
    val ownerKey: String,
    val appWidgetId: Int,
    val provider: WidgetProvider,
    val profileSerial: Long,
    val model: AppWidgetModel,
    val heightDp: Int,
    val bounds: ResizeBounds,
    val isResizable: Boolean,
    val isReconfigurable: Boolean
)
