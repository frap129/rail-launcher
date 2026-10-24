package core.popups

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DENSITY: Float = 1f
private val INPUT_WINDOW: IntRect = IntRect(left = 0, top = 0, right = 1080, bottom = 1920)
private val INPUT_SAFE_INSETS: PopupInsets = PopupInsets(top = 100, bottom = 100)
private val INPUT_ANCHOR: PopupAnchor = PopupAnchor(rowTop = 500, rowBottom = 564, iconCenter = Offset(32f, 532f))

class PopupPlacementTest {
    @Test
    fun `anchors the card at the row top with the start and Rail insets`() {
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = INPUT_ANCHOR,
            window = INPUT_WINDOW,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 200,
            density = DENSITY,
            isRtl = false
        )
        assertEquals(16, actualFrame.offsetX)
        assertEquals(500, actualFrame.offsetY)
        assertEquals(400, actualFrame.width)
        assertEquals(1688, actualFrame.maxHeight)
    }

    @Test
    fun `caps the width at 400dp on wide screens`() {
        val inputWindow: IntRect = IntRect(left = 0, top = 0, right = 1200, bottom = 1920)
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = INPUT_ANCHOR,
            window = inputWindow,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 200,
            density = DENSITY,
            isRtl = false
        )
        assertEquals(400, actualFrame.width)
    }

    @Test
    fun `uses the available width on narrow screens`() {
        val inputWindow: IntRect = IntRect(left = 0, top = 0, right = 300, bottom = 1920)
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = INPUT_ANCHOR,
            window = inputWindow,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 200,
            density = DENSITY,
            isRtl = false
        )
        assertEquals(204, actualFrame.width)
    }

    @Test
    fun `anchors from the RTL start edge and keeps the Rail inset free`() {
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = INPUT_ANCHOR,
            window = INPUT_WINDOW,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 200,
            density = DENSITY,
            isRtl = true
        )
        assertEquals(664, actualFrame.offsetX)
        assertEquals(400, actualFrame.width)
    }

    @Test
    fun `shifts the card up when it does not fit below the row`() {
        val inputAnchor: PopupAnchor = INPUT_ANCHOR.copy(rowTop = 1800, rowBottom = 1864)
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = inputAnchor,
            window = INPUT_WINDOW,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 400,
            density = DENSITY,
            isRtl = false
        )
        assertEquals(1404, actualFrame.offsetY)
    }

    @Test
    fun `clamps the card at the window height and reports the max height`() {
        val inputAnchor: PopupAnchor = INPUT_ANCHOR.copy(rowTop = 1800, rowBottom = 1864)
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = inputAnchor,
            window = INPUT_WINDOW,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 2000,
            density = DENSITY,
            isRtl = false
        )
        assertEquals(1688, actualFrame.maxHeight)
        assertEquals(116, actualFrame.offsetY)
    }

    @Test
    fun `scales the insets by density`() {
        val actualFrame: PopupFrame = PopupPlacement.place(
            anchor = INPUT_ANCHOR,
            window = INPUT_WINDOW,
            safeInsets = INPUT_SAFE_INSETS,
            contentHeightPx = 200,
            density = 2f,
            isRtl = false
        )
        assertEquals(32, actualFrame.offsetX)
        assertEquals(800, actualFrame.width)
        assertEquals(1656, actualFrame.maxHeight)
    }
}
