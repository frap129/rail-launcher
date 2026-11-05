package core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragHandlePlacementTest {
    @Test
    fun `places the handle before content and clear of a Start Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.START.dragHandlePlacement()
        val actualPadding: PaddingValues = RailSide.START.dragHandlePadding()
        assertTrue(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterStart, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopStart, actualPlacement.topAlignment)
        assertEquals(64.dp, RailSide.START.dragHandleInset())
        assertEquals(64.dp, actualPadding.calculateStartPadding(layoutDirection = LayoutDirection.Ltr))
        assertEquals(0.dp, actualPadding.calculateEndPadding(layoutDirection = LayoutDirection.Ltr))
    }

    @Test
    fun `places the handle after content and clear of an End Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.END.dragHandlePlacement()
        val actualPadding: PaddingValues = RailSide.END.dragHandlePadding()
        assertFalse(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterEnd, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopEnd, actualPlacement.topAlignment)
        assertEquals(64.dp, RailSide.END.dragHandleInset())
        assertEquals(0.dp, actualPadding.calculateStartPadding(layoutDirection = LayoutDirection.Ltr))
        assertEquals(64.dp, actualPadding.calculateEndPadding(layoutDirection = LayoutDirection.Ltr))
    }
}
