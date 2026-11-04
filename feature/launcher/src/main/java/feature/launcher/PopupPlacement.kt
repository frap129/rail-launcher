package feature.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import core.data.prefs.RailSide
import core.ui.layout.homeRailTouchInset
import kotlin.math.roundToInt

private const val NON_RAIL_INSET_DP: Float = 16f
private const val MAX_WIDTH_DP: Float = 400f
private const val VERTICAL_INSET_DP: Float = 16f

/** The swiped row's position and icon centre, in root pixels. */
data class PopupAnchor(val rowTop: Int, val rowBottom: Int, val iconCenter: Offset)

/** The window's top and bottom safe insets, in pixels. */
data class PopupInsets(val top: Int, val bottom: Int)

/** Where the Pop-up card is placed, in root pixels. */
data class PopupFrame(val offsetX: Int, val offsetY: Int, val width: Int, val maxHeight: Int)

/** Places the Pop-up card beside its row without covering the Rail. */
object PopupPlacement {
    fun place(
        /** The row to anchor to, or null to centre the card vertically. */
        anchor: PopupAnchor?,
        window: IntRect,
        safeInsets: PopupInsets,
        contentHeightPx: Int,
        density: Float,
        railSide: RailSide,
        isRtl: Boolean
    ): PopupFrame {
        val startInsetDp: Float = if (railSide.isStart) homeRailTouchInset.value else NON_RAIL_INSET_DP
        val endInsetDp: Float = if (railSide.isStart) NON_RAIL_INSET_DP else homeRailTouchInset.value
        val startInsetPx: Int = (startInsetDp * density).roundToInt()
        val endInsetPx: Int = (endInsetDp * density).roundToInt()
        val width: Int = (window.width - startInsetPx - endInsetPx)
            .coerceAtLeast(0)
            .coerceAtMost((MAX_WIDTH_DP * density).roundToInt())
        val offsetX: Int = if (isRtl) {
            window.right - startInsetPx - width
        } else {
            window.left + startInsetPx
        }
        val verticalInsetPx: Int = (VERTICAL_INSET_DP * density).roundToInt()
        val maxHeight: Int = (window.height - safeInsets.top - safeInsets.bottom - verticalInsetPx * 2).coerceAtLeast(0)
        val cardHeight: Int = contentHeightPx.coerceAtMost(maxHeight)
        val topBound: Int = window.top + safeInsets.top + verticalInsetPx
        val bottomBound: Int = window.bottom - safeInsets.bottom - verticalInsetPx
        val offsetY: Int = if (anchor == null) {
            (window.top + (window.height - cardHeight) / 2).coerceAtMost(bottomBound - cardHeight).coerceAtLeast(topBound)
        } else {
            anchor.rowTop.coerceAtMost(bottomBound - cardHeight).coerceAtLeast(topBound)
        }
        return PopupFrame(offsetX = offsetX, offsetY = offsetY, width = width, maxHeight = maxHeight)
    }
}
