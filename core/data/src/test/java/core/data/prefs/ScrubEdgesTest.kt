package core.data.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubEdgesTest {
    @Test
    fun `includes the layout edges named by each value`() {
        assertTrue(ScrubEdges.START.isStartIncluded)
        assertFalse(ScrubEdges.START.isEndIncluded)
        assertFalse(ScrubEdges.END.isStartIncluded)
        assertTrue(ScrubEdges.END.isEndIncluded)
        assertTrue(ScrubEdges.BOTH.isStartIncluded)
        assertTrue(ScrubEdges.BOTH.isEndIncluded)
    }

    @Test
    fun `includes the Rail side named by each value`() {
        assertTrue(ScrubEdges.START.includes(RailSide.START))
        assertFalse(ScrubEdges.START.includes(RailSide.END))
        assertFalse(ScrubEdges.END.includes(RailSide.START))
        assertTrue(ScrubEdges.END.includes(RailSide.END))
        assertTrue(ScrubEdges.BOTH.includes(RailSide.START))
        assertTrue(ScrubEdges.BOTH.includes(RailSide.END))
    }
}
