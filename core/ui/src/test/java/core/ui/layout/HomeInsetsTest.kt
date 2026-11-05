package core.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide
import org.junit.Assert.assertEquals
import org.junit.Test

private val WIDGET_SCREEN_WIDTH: Dp = 1080.dp
private val EXPECTED_WIDGET_CONTENT_WIDTH: Dp = 968.dp

private data class ExpectedHomeInsets(
    val railSide: RailSide,
    val startInset: Dp,
    val endInset: Dp,
    val contentStartInset: Dp,
    val contentEndInset: Dp
)

class HomeInsetsTest {
    @Test
    fun `reserves the Rail touch column at the layout end`() {
        assertInsets(
            ExpectedHomeInsets(
                railSide = RailSide.END,
                startInset = 32.dp,
                endInset = 80.dp,
                contentStartInset = 0.dp,
                contentEndInset = 48.dp
            )
        )
    }

    @Test
    fun `reserves the Rail touch column at the layout start`() {
        assertInsets(
            ExpectedHomeInsets(
                railSide = RailSide.START,
                startInset = 80.dp,
                endInset = 32.dp,
                contentStartInset = 48.dp,
                contentEndInset = 0.dp
            )
        )
    }

    private fun assertInsets(expected: ExpectedHomeInsets) {
        assertEquals(expected.startInset, homeRailStartInset(railSide = expected.railSide))
        assertEquals(expected.endInset, homeRailEndInset(railSide = expected.railSide))
        assertEquals(expected.contentStartInset, homeContentStartInset(railSide = expected.railSide))
        assertEquals(expected.contentEndInset, homeContentEndInset(railSide = expected.railSide))
        assertEquals(
            EXPECTED_WIDGET_CONTENT_WIDTH,
            insetWidgetContentWidth(screenWidth = WIDGET_SCREEN_WIDTH, railSide = expected.railSide)
        )
    }
}
