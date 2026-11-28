package core.ui.layout

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherOrientationTest {
    @Test
    fun `locks phone-sized screens to portrait`() {
        val inputSmallestScreenWidthDp: Int = 411
        val expectedRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val actualRequestedOrientation: Int = resolveRequestedOrientation(
            smallestScreenWidthDp = inputSmallestScreenWidthDp,
            rotateHome = null
        )
        assertEquals(expectedRequestedOrientation, actualRequestedOrientation)
    }

    @Test
    fun `lets tablet-sized screens follow system rotation`() {
        val inputSmallestScreenWidthDp: Int = 600
        val expectedRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val actualRequestedOrientation: Int = resolveRequestedOrientation(
            smallestScreenWidthDp = inputSmallestScreenWidthDp,
            rotateHome = null
        )
        assertEquals(expectedRequestedOrientation, actualRequestedOrientation)
    }

    @Test
    fun `honors Rotate Home on a phone-sized screen`() {
        val inputSmallestScreenWidthDp: Int = 411
        val expectedRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val actualRequestedOrientation: Int = resolveRequestedOrientation(
            smallestScreenWidthDp = inputSmallestScreenWidthDp,
            rotateHome = true
        )
        assertEquals(expectedRequestedOrientation, actualRequestedOrientation)
    }

    @Test
    fun `locks Home on a large screen when Rotate Home is off`() {
        val inputSmallestScreenWidthDp: Int = 600
        val expectedRequestedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val actualRequestedOrientation: Int = resolveRequestedOrientation(
            smallestScreenWidthDp = inputSmallestScreenWidthDp,
            rotateHome = false
        )
        assertEquals(expectedRequestedOrientation, actualRequestedOrientation)
    }

    @Test
    fun `defaults Rotate Home off below 600dp`() {
        val inputSmallestScreenWidthDp: Int = 599
        val actualRotateHome: Boolean = resolveRotateHome(
            rotateHome = null,
            smallestScreenWidthDp = inputSmallestScreenWidthDp
        )
        assertFalse(actualRotateHome)
    }

    @Test
    fun `defaults Rotate Home on at 600dp`() {
        val inputSmallestScreenWidthDp: Int = 600
        val actualRotateHome: Boolean = resolveRotateHome(
            rotateHome = null,
            smallestScreenWidthDp = inputSmallestScreenWidthDp
        )
        assertTrue(actualRotateHome)
    }

    @Test
    fun `keeps Rotate Home off on a large screen when the user locked it`() {
        val inputRotateHome: Boolean = false
        val inputSmallestScreenWidthDp: Int = 600
        val actualRotateHome: Boolean = resolveRotateHome(
            rotateHome = inputRotateHome,
            smallestScreenWidthDp = inputSmallestScreenWidthDp
        )
        assertFalse(actualRotateHome)
    }

    @Test
    fun `keeps Rotate Home enabled on a phone when the user chose it`() {
        val inputRotateHome: Boolean = true
        val inputSmallestScreenWidthDp: Int = 411
        val actualRotateHome: Boolean = resolveRotateHome(
            rotateHome = inputRotateHome,
            smallestScreenWidthDp = inputSmallestScreenWidthDp
        )
        assertTrue(actualRotateHome)
    }
}
