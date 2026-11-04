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
}
