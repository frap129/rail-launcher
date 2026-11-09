package core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawableNameTest {
    @Test
    fun `matches a term with underscores read as spaces and ignoring case`() {
        assertTrue("google_maps".drawableNameContains("maps"))
        assertTrue("Google_Maps".drawableNameContains("google maps"))
        assertFalse("calculator".drawableNameContains("music"))
    }
}
