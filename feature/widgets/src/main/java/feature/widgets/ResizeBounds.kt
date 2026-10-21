package feature.widgets

import core.data.widgets.WidgetProviderInfo
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
    }
}
