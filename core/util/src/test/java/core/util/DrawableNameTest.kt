package core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawableNameTest {
    @Test
    fun `reads underscores in a drawable name as spaces`() {
        assertEquals("google maps", "google_maps".toNormalizedDrawableName())
    }

    @Test
    fun `matches a term with underscores read as spaces and ignoring case`() {
        assertTrue("google_maps".drawableNameContains("maps"))
        assertTrue("Google_Maps".drawableNameContains("google maps"))
        assertFalse("calculator".drawableNameContains("music"))
    }

    @Test
    fun `matches a whole drawable name word ignoring case`() {
        assertTrue("car".drawableNameHasWord("car"))
        assertTrue("car_rental".drawableNameHasWord("car"))
        assertTrue("Car_Rental".drawableNameHasWord("car"))
        assertFalse("card".drawableNameHasWord("car"))
        assertFalse("scarlet".drawableNameHasWord("car"))
    }
}
