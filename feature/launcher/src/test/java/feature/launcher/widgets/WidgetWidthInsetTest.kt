package feature.launcher.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide
import core.data.widgets.WidgetWidth
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetWidthInsetTest {
    @Test
    fun `inset widgets reserve the Rail column at the layout end`() {
        val actualPadding: PaddingValues = WidgetWidth.Inset.contentPadding(railSide = RailSide.END)

        assertHorizontalPadding(actualPadding = actualPadding, expectedStart = 0.dp, expectedEnd = 48.dp)
    }

    @Test
    fun `inset widgets reserve the Rail column at the layout start`() {
        val actualPadding: PaddingValues = WidgetWidth.Inset.contentPadding(railSide = RailSide.START)

        assertHorizontalPadding(actualPadding = actualPadding, expectedStart = 48.dp, expectedEnd = 0.dp)
    }

    @Test
    fun `Under Rail widgets extend under the Rail on either side`() {
        val actualStartSidePadding: PaddingValues = WidgetWidth.UnderRail.contentPadding(railSide = RailSide.START)
        val actualEndSidePadding: PaddingValues = WidgetWidth.UnderRail.contentPadding(railSide = RailSide.END)

        assertHorizontalPadding(actualPadding = actualStartSidePadding, expectedStart = 0.dp, expectedEnd = 0.dp)
        assertHorizontalPadding(actualPadding = actualEndSidePadding, expectedStart = 0.dp, expectedEnd = 0.dp)
    }

    private fun assertHorizontalPadding(actualPadding: PaddingValues, expectedStart: Dp, expectedEnd: Dp) {
        assertEquals(expectedStart, actualPadding.calculateStartPadding(layoutDirection = LayoutDirection.Ltr))
        assertEquals(expectedEnd, actualPadding.calculateEndPadding(layoutDirection = LayoutDirection.Ltr))
    }
}
