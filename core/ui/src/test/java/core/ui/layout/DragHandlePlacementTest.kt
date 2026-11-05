package core.ui.layout

import androidx.compose.ui.Alignment
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
        assertTrue(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterStart, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopStart, actualPlacement.topAlignment)
        assertEquals(64.dp, actualPlacement.startPadding(railSide = RailSide.START))
        assertEquals(0.dp, actualPlacement.endPadding(railSide = RailSide.START))
    }

    @Test
    fun `places the handle after content and clear of an End Rail`() {
        val actualPlacement: DragHandlePlacement = RailSide.END.dragHandlePlacement()
        assertFalse(actualPlacement.isBeforeContent)
        assertEquals(Alignment.CenterEnd, actualPlacement.centerAlignment)
        assertEquals(Alignment.TopEnd, actualPlacement.topAlignment)
        assertEquals(0.dp, actualPlacement.startPadding(railSide = RailSide.END))
        assertEquals(64.dp, actualPlacement.endPadding(railSide = RailSide.END))
    }
}
