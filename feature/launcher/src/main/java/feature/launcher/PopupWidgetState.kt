package feature.launcher

import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetProvider
import core.ui.widgets.AppWidgetModel

/** The largest share of the window height a Pop-up widget may take. */
const val POPUP_WIDGET_MAX_HEIGHT_FRACTION: Float = 0.6f

/** The Pop-up height cap for a window [windowHeightDp] tall. */
fun popupWidgetMaxHeightDp(windowHeightDp: Int): Int = (windowHeightDp * POPUP_WIDGET_MAX_HEIGHT_FRACTION).toInt()

/** The App Widget place at the top of a Pop-up, holding one widget or a Widget Stack. */
data class PopupWidgetState(
    val placeId: Long,
    val ownerKey: String,
    val pages: List<PopupWidgetPage>,
    val currentPage: Int,
    val heightDp: Int,
    val bounds: ResizeBounds,
    val isResizable: Boolean,
    val indicatorVisible: Boolean
) {
    /** True when the place pages sideways as a Widget Stack. */
    val isStack: Boolean get() = pages.size > 1

    /** The Stack page holding [widgetId], if any. */
    fun pageOf(widgetId: Long): PopupWidgetPage? = pages.firstOrNull { page -> page.widgetId == widgetId }

    /** The Stack page the pager settled on. */
    fun currentPageOrNull(): PopupWidgetPage? = pages.getOrNull(currentPage)
}

/** One App Widget page of a Pop-up's Widget Stack. */
data class PopupWidgetPage(
    val widgetId: Long,
    val appWidgetId: Int,
    val provider: WidgetProvider,
    val profileSerial: Long,
    val model: AppWidgetModel,
    val isReconfigurable: Boolean
)
