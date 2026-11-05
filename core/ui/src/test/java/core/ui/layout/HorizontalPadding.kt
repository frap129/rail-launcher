package core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals

internal data class HorizontalPadding(val start: Dp, val end: Dp)

internal fun assertHorizontalPadding(actualPadding: PaddingValues, expected: HorizontalPadding) {
    assertEquals(expected.start, actualPadding.calculateStartPadding(layoutDirection = LayoutDirection.Ltr))
    assertEquals(expected.end, actualPadding.calculateEndPadding(layoutDirection = LayoutDirection.Ltr))
}
