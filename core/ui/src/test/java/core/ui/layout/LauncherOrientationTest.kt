package core.ui.layout

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherOrientationTest {
    @Test
    fun `locks phone-sized screens to portrait`() {
        val inputSmallestScreenWidthDp: Int = 411
        val expectedRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val actualRequestedOrientation: Int = resolveRequestedOrientation(smallestScreenWidthDp = inputSmallestScreenWidthDp)
        assertEquals(expectedRequestedOrientation, actualRequestedOrientation)
    }

    @Test
    fun `lets tablet-sized screens follow system rotation`() {
        val inputSmallestScreenWidthDp: Int = 600
        val expectedRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val actualRequestedOrientation: Int = resolveRequestedOrientation(smallestScreenWidthDp = inputSmallestScreenWidthDp)
        assertEquals(expectedRequestedOrientation, actualRequestedOrientation)
    }
}
