package feature.launcher.widgets

import core.data.widgets.AppWidgetId
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetPlace
import kotlinx.coroutines.flow.StateFlow

/** The live vertical Resize mode of one Widget Area place. */
data class ResizeState(
    val placeId: Long,
    val bounds: ResizeBounds,
    val defaultHeightDp: Int,
    val draftHeightDp: Int,
    val indicatorVisible: Boolean = false
)

/** The settled Resize work that still has to be persisted. */
data class ResizeFinish(
    val placeId: Long,
    val storedHeightDp: Int?,
    val appWidgetIds: List<AppWidgetId>,
    val widthDp: Int,
    val heightDp: Int
)

/** The page size to report to the App Widget providers after a Resize. */
data class WidgetSizeReport(val appWidgetIds: List<AppWidgetId>, val widthDp: Int, val heightDp: Int)

/** Owns Resize: provider bounds, the draft height, persistence, and one size report per session. */
interface IWidgetResizeController {
    val resizeState: StateFlow<ResizeState?>

    val homeHeightDp: StateFlow<Int?>

    /** Supplies the safe Home height so Resize can cap the widget and keep the handle reachable. */
    fun setHomeHeight(heightDp: Int)

    /** Supplies the Widget Area width for the size report sent to the provider after Resize. */
    fun setWidgetAreaWidth(widthDp: Int)

    /** Enters Resize for [placeId], returning false when the place is not resizable right now. */
    suspend fun startResize(placeId: Long, headerHeightDp: Int? = null, ownerKey: String? = null): Boolean

    /** Enters Resize for the place holding [widgetId]. */
    suspend fun startResizeForWidget(widgetId: Long, headerHeightDp: Int? = null, ownerKey: String? = null): Boolean

    /** Applies a fractional drag delta, snapping the accumulated height to the 8dp grid. */
    fun dragResize(deltaDp: Float)

    /** Returns the draft to the provider default, which [finishResize] then stores as no override. */
    fun resetResize()

    /** Leaves Resize and returns the height persistence and size report it still needs. */
    fun finishResize(): ResizeFinish?

    /** Persists [finish] and returns the size report to send, if any. */
    suspend fun persistResize(finish: ResizeFinish): WidgetSizeReport?

    /** Leaves Resize without saving when the place disappears underneath the user. */
    fun cancelResize()

    /** Cancels Resize when its place disappears. */
    fun onPlacesChanged(places: List<WidgetPlace>, ownerKey: String? = null)

    /** Clamps a stack that gained a page and returns the page size to report to its providers. */
    suspend fun onStackGrown(placeId: Long, headerHeightDp: Int?, ownerKey: String? = null): WidgetSizeReport?

    /** Clamps a stack's stored height after the Header is replaced by a page. */
    suspend fun clampStackHeight(placeId: Long, headerHeightDp: Int?, ownerKey: String? = null)
}
