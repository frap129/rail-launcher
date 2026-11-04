package core.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RailSideTest {
    @Test
    fun `resolves the physical left for each layout direction`() {
        assertTrue(RailSide.START.isOnLeft(isRtl = false))
        assertFalse(RailSide.END.isOnLeft(isRtl = false))
        assertFalse(RailSide.START.isOnLeft(isRtl = true))
        assertTrue(RailSide.END.isOnLeft(isRtl = true))
    }

    @Test
    fun `maps the physical left back to a layout side`() {
        assertEquals(RailSide.START, RailSide.forLeft(isOnLeft = true, isRtl = false))
        assertEquals(RailSide.END, RailSide.forLeft(isOnLeft = false, isRtl = false))
        assertEquals(RailSide.END, RailSide.forLeft(isOnLeft = true, isRtl = true))
        assertEquals(RailSide.START, RailSide.forLeft(isOnLeft = false, isRtl = true))
    }

    @Test
    fun `opposite swaps the layout side`() {
        assertEquals(RailSide.END, RailSide.START.opposite())
        assertEquals(RailSide.START, RailSide.END.opposite())
    }
}
