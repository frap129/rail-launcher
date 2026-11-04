package feature.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import core.data.prefs.RailSide
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DENSITY: Float = 1f
private val INPUT_WINDOW: IntRect = IntRect(left = 0, top = 0, right = 1080, bottom = 1920)
private val INPUT_SAFE_INSETS: PopupInsets = PopupInsets(top = 100, bottom = 100)
private val INPUT_ANCHOR: PopupAnchor = PopupAnchor(rowTop = 500, rowBottom = 564, iconCenter = Offset(32f, 532f))

class PopupPlacementTest {
    @Test
    fun `anchors the card at the row top with the start and Rail insets`() {
        val actualFrame: PopupFrame = placeFrame()

        assertEquals(16, actualFrame.offsetX)
        assertEquals(500, actualFrame.offsetY)
        assertEquals(400, actualFrame.width)
        assertEquals(1688, actualFrame.maxHeight)
    }

    @Test
    fun `caps the width at 400dp on wide screens`() {
        val actualFrame: PopupFrame = placeFrame(window = IntRect(left = 0, top = 0, right = 1200, bottom = 1920))

        assertEquals(400, actualFrame.width)
    }

    @Test
    fun `uses the available width on narrow screens`() {
        val actualFrame: PopupFrame = placeFrame(window = IntRect(left = 0, top = 0, right = 300, bottom = 1920))

        assertEquals(204, actualFrame.width)
    }

    @Test
    fun `anchors from the RTL start edge and keeps the layout end Rail inset free`() {
        val actualFrame: PopupFrame = placeFrame(isRtl = true)

        assertEquals(664, actualFrame.offsetX)
        assertEquals(400, actualFrame.width)
    }

    @Test
    fun `shifts the card past a Rail on the layout start in LTR`() {
        val actualFrame: PopupFrame = placeFrame(railSide = RailSide.START)

        assertEquals(80, actualFrame.offsetX)
        assertEquals(400, actualFrame.width)
    }

    @Test
    fun `shifts the card past a Rail on the layout start in RTL`() {
        val actualFrame: PopupFrame = placeFrame(railSide = RailSide.START, isRtl = true)

        assertEquals(600, actualFrame.offsetX)
        assertEquals(400, actualFrame.width)
    }

    @Test
    fun `shifts the card up when it does not fit below the row`() {
        val actualFrame: PopupFrame = placeFrame(
            anchor = INPUT_ANCHOR.copy(rowTop = 1800, rowBottom = 1864),
            contentHeightPx = 400
        )

        assertEquals(1404, actualFrame.offsetY)
    }

    @Test
    fun `clamps the card at the window height and reports the max height`() {
        val actualFrame: PopupFrame = placeFrame(
            anchor = INPUT_ANCHOR.copy(rowTop = 1800, rowBottom = 1864),
            contentHeightPx = 2000
        )

        assertEquals(1688, actualFrame.maxHeight)
        assertEquals(116, actualFrame.offsetY)
    }

    @Test
    fun `centres the card vertically when it has no row anchor`() {
        val actualFrame: PopupFrame = placeFrame(anchor = null, contentHeightPx = 400)

        assertEquals(760, actualFrame.offsetY)
    }

    @Test
    fun `scales the insets by density`() {
        val actualFrame: PopupFrame = placeFrame(density = 2f)

        assertEquals(32, actualFrame.offsetX)
        assertEquals(800, actualFrame.width)
        assertEquals(1656, actualFrame.maxHeight)
    }

    @Test
    fun `scales a layout-start Rail inset by density`() {
        val actualFrame: PopupFrame = placeFrame(density = 2f, railSide = RailSide.START)

        assertEquals(160, actualFrame.offsetX)
        assertEquals(800, actualFrame.width)
    }

    private fun placeFrame(
        anchor: PopupAnchor? = INPUT_ANCHOR,
        window: IntRect = INPUT_WINDOW,
        safeInsets: PopupInsets = INPUT_SAFE_INSETS,
        contentHeightPx: Int = 200,
        density: Float = DENSITY,
        railSide: RailSide = RailSide.END,
        isRtl: Boolean = false
    ): PopupFrame = PopupPlacement.place(
        anchor = anchor,
        window = window,
        safeInsets = safeInsets,
        contentHeightPx = contentHeightPx,
        density = density,
        railSide = railSide,
        isRtl = isRtl
    )
}
