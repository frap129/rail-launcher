package feature.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ListOffsetsTest {
    @Test
    fun `derives the App List offsets from the screen height`() {
        val actualOffsets: ListOffsets = ListOffsets.forScreenHeight(screenHeightPx = 1000)
        assertEquals(ListOffsets(startPx = 200, endPx = 800), actualOffsets)
    }

    @Test
    fun `uses integer division for the start offset`() {
        val actualOffsets: ListOffsets = ListOffsets.forScreenHeight(screenHeightPx = 1002)
        assertEquals(ListOffsets(startPx = 200, endPx = 802), actualOffsets)
    }
}
