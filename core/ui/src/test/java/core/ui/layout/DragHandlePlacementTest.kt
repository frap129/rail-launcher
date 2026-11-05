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
        assertEquals(64.dp, RailSide.START.dragHandleInset())
        assertHorizontalPadding(
            actualPadding = RailSide.START.dragHandlePadding(),
            expected = HorizontalPadding(start = 64.dp, end = 0.dp)
        )
    }

    @Test
    fun `places the handle after content and clear of an End Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.END.dragHandlePlacement()
        assertFalse(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterEnd, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopEnd, actualPlacement.topAlignment)
        assertEquals(64.dp, RailSide.END.dragHandleInset())
        assertHorizontalPadding(
            actualPadding = RailSide.END.dragHandlePadding(),
            expected = HorizontalPadding(start = 0.dp, end = 64.dp)
        )
    }

    @Test
    fun `keeps a Stack page handle clear of a Start Rail`() {
        val actualInset: Dp = RailSide.START.stackPageHandleInset(gap = STACK_PAGE_HANDLE_GAP)
        assertEquals(56.dp, actualInset)
        assertHorizontalPadding(
            actualPadding = RailSide.START.dragHandlePadding(inset = actualInset),
            expected = HorizontalPadding(start = 56.dp, end = 0.dp)
        )
    }

    @Test
    fun `keeps the End Rail layout for a Stack page handle`() {
        val actualInset: Dp = RailSide.END.stackPageHandleInset(gap = STACK_PAGE_HANDLE_GAP)
        assertEquals(8.dp, actualInset)
        assertHorizontalPadding(
            actualPadding = RailSide.END.dragHandlePadding(inset = actualInset),
            expected = HorizontalPadding(start = 0.dp, end = 8.dp)
        )
    }
}
