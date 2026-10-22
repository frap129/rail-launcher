package core.data.widgets

import kotlin.math.roundToInt

/** The vertical height range an App Widget can be resized to. */
data class ResizeBounds(val minDp: Int, val maxDp: Int?, val isResizable: Boolean = maxDp == null || maxDp > minDp) {
    /** Clamps [heightDp] to this range and the safe Home height. */
    fun clamp(heightDp: Int, homeHeightDp: Int): Int {
        val upper: Int = minOf(maxDp ?: homeHeightDp, homeHeightDp).coerceAtLeast(minDp)
        return heightDp.coerceIn(minDp, upper)
    }

    /** Rounds [heightDp] to the 8dp resize grid; callers clamp the result to the bounds. */
    fun snapToStepGrid(heightDp: Int): Int = (heightDp / STEP_DP.toFloat()).roundToInt() * STEP_DP

    companion object {
        const val MIN_HEIGHT_DP: Int = 48
        const val STEP_DP: Int = 8
        const val STACK_INDICATOR_HEIGHT_DP: Int = 12

        /** Builds the bounds from the provider info; a non-vertical or inverted range is not resizable. */
        fun from(info: WidgetProviderInfo): ResizeBounds {
            val minDp: Int = maxOf(MIN_HEIGHT_DP, if (info.minResizeHeightDp > 0) info.minResizeHeightDp else info.minHeightDp)
            val maxDp: Int? = info.maxResizeHeightDp?.takeIf { height -> height > 0 }
            return ResizeBounds(
                minDp = minDp,
                maxDp = maxDp,
                isResizable = info.isVerticallyResizable && (maxDp == null || maxDp > minDp)
            )
        }

        /** The intersection of every page's bounds; an empty intersection is non-resizable at the highest minimum. */
        fun stackBounds(pages: List<ResizeBounds>): ResizeBounds {
            if (pages.isEmpty()) {
                return ResizeBounds(minDp = MIN_HEIGHT_DP, maxDp = MIN_HEIGHT_DP, isResizable = false)
            }
            val minDp: Int = pages.maxOf { page -> page.minDp }
            val maxDp: Int? = pages.mapNotNull { page -> page.maxDp }.minOrNull()
            return if (maxDp != null && maxDp <= minDp) {
                ResizeBounds(minDp = minDp, maxDp = minDp, isResizable = false)
            } else {
                ResizeBounds(minDp = minDp, maxDp = maxDp, isResizable = maxDp == null || maxDp > minDp)
            }
        }
    }
}

/** The bounds of one Widget Stack page; a Header contributes its measured height as a minimum. */
fun PlacedWidget.resizeBounds(headerHeightDp: Int?, providerInfo: (PlacedWidget.AppWidget) -> WidgetProviderInfo?): ResizeBounds =
    when (this) {
        is PlacedWidget.Header -> ResizeBounds(
            minDp = maxOf(ResizeBounds.MIN_HEIGHT_DP, headerHeightDp ?: ResizeBounds.MIN_HEIGHT_DP),
            maxDp = null,
            isResizable = false
        )

        is PlacedWidget.AppWidget -> appWidgetResizeBounds(
            providerInfo = providerInfo(this),
            defaultHeightDp = defaultHeightDp
        )
    }

/** Bounds for an App Widget from provider info, falling back to its stored default height. */
internal fun appWidgetResizeBounds(providerInfo: WidgetProviderInfo?, defaultHeightDp: Int?): ResizeBounds =
    providerInfo?.let(ResizeBounds::from)
        ?: ResizeBounds(
            minDp = maxOf(ResizeBounds.MIN_HEIGHT_DP, defaultHeightDp ?: ResizeBounds.MIN_HEIGHT_DP),
            maxDp = null,
            isResizable = false
        )
