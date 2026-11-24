package core.ui.layout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeScreenTest {
    @Test
    fun `does not treat a width below 600dp as large`() {
        val inputWidthDp: Int = 599
        val actualIsLargeScreen: Boolean = isLargeScreen(widthDp = inputWidthDp)
        assertFalse(actualIsLargeScreen)
    }

    @Test
    fun `treats a width of 600dp as large`() {
        val inputWidthDp: Int = 600
        val actualIsLargeScreen: Boolean = isLargeScreen(widthDp = inputWidthDp)
        assertTrue(actualIsLargeScreen)
    }
}
