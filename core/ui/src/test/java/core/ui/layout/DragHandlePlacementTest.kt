package core.ui.layout

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val STACK_PAGE_HANDLE_GAP: Dp = 8.dp

class DragHandlePlacementTest {
    @Test
    fun `places the handle before content and clear of a Start Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.START.dragHandlePlacement()
        assertTrue(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterStart, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopStart, actualPlacement.topAlignment)
        assertEquals(64.dp, actualPlacement.startPadding(railSide = RailSide.START))
        assertHorizontalPadding(
            actualPadding = actualPlacement.padding(railSide = RailSide.START),
            expected = HorizontalPadding(start = 64.dp, end = 0.dp)
        )
    }

    @Test
    fun `places the handle after content and clear of an End Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.END.dragHandlePlacement()
        assertFalse(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterEnd, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopEnd, actualPlacement.topAlignment)
        assertEquals(0.dp, actualPlacement.startPadding(railSide = RailSide.END))
        assertHorizontalPadding(
            actualPadding = actualPlacement.padding(railSide = RailSide.END),
            expected = HorizontalPadding(start = 0.dp, end = 64.dp)
        )
    }

    @Test
    fun `keeps a stack page handle clear of a Start Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.START.dragHandlePlacement()
        assertHorizontalPadding(
            actualPadding = actualPlacement.padding(
                railSide = RailSide.START,
                gap = STACK_PAGE_HANDLE_GAP,
                clearsEndRail = false
            ),
            expected = HorizontalPadding(start = 56.dp, end = 0.dp)
        )
    }

    @Test
    fun `keeps the End Rail layout for a stack page handle`() {
        val actualPlacement: DragHandlePlacement = RailSide.END.dragHandlePlacement()
        assertHorizontalPadding(
            actualPadding = actualPlacement.padding(
                railSide = RailSide.END,
                gap = STACK_PAGE_HANDLE_GAP,
                clearsEndRail = false
            ),
            expected = HorizontalPadding(start = 0.dp, end = 8.dp)
        )
    }
}
