package feature.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAreaScrollTest {
    @Test
    fun `scrolls a short item up when it extends below the viewport`() {
        val actualDelta: Int = calculateScrollIntoViewDelta(
            itemOffset = VIEWPORT_END - SHORT_ITEM_SIZE_PX / 2,
            itemSize = SHORT_ITEM_SIZE_PX,
            viewportStart = VIEWPORT_START,
            viewportEnd = VIEWPORT_END,
            keepBottomVisible = false
        )

        assertEquals(SHORT_ITEM_SIZE_PX / 2, actualDelta)
    }

    @Test
    fun `scrolls a short item down when it starts above the viewport`() {
        val actualDelta: Int = calculateScrollIntoViewDelta(
            itemOffset = VIEWPORT_START - SHORT_ITEM_SIZE_PX,
            itemSize = SHORT_ITEM_SIZE_PX,
            viewportStart = VIEWPORT_START,
            viewportEnd = VIEWPORT_END,
            keepBottomVisible = false
        )

        assertEquals(-SHORT_ITEM_SIZE_PX, actualDelta)
    }

    @Test
    fun `keeps a tall item aligned by its top when the bottom may stay hidden`() {
        val actualDelta: Int = calculateScrollIntoViewDelta(
            itemOffset = VIEWPORT_END - SHORT_ITEM_SIZE_PX,
            itemSize = TALL_ITEM_SIZE_PX,
            viewportStart = VIEWPORT_START,
            viewportEnd = VIEWPORT_END,
            keepBottomVisible = false
        )

        assertEquals(0, actualDelta)
    }

    @Test
    fun `aligns a tall item by its bottom when the bottom must stay visible`() {
        val actualDelta: Int = calculateScrollIntoViewDelta(
            itemOffset = VIEWPORT_END - SHORT_ITEM_SIZE_PX,
            itemSize = TALL_ITEM_SIZE_PX,
            viewportStart = VIEWPORT_START,
            viewportEnd = VIEWPORT_END,
            keepBottomVisible = true
        )

        assertEquals(TALL_ITEM_SIZE_PX - SHORT_ITEM_SIZE_PX, actualDelta)
    }

    @Test
    fun `does not scroll an item that is fully visible`() {
        val actualDelta: Int = calculateScrollIntoViewDelta(
            itemOffset = VIEWPORT_START + SHORT_ITEM_SIZE_PX,
            itemSize = SHORT_ITEM_SIZE_PX,
            viewportStart = VIEWPORT_START,
            viewportEnd = VIEWPORT_END,
            keepBottomVisible = false
        )

        assertEquals(0, actualDelta)
    }

    @Test
    fun `targets the first item when the Media Player is at the top`() {
        val actualIndex: Int? = mediaPlayerScrollTargetIndex(mediaAtTop = true, totalItemsCount = 0)

        assertEquals(0, actualIndex)
    }

    @Test
    fun `waits for items when the Media Player is below the places`() {
        val actualIndex: Int? = mediaPlayerScrollTargetIndex(mediaAtTop = false, totalItemsCount = 0)

        assertNull(actualIndex)
    }

    @Test
    fun `targets the last item when the Media Player is below the places`() {
        val actualIndex: Int? = mediaPlayerScrollTargetIndex(mediaAtTop = false, totalItemsCount = 3)

        assertEquals(2, actualIndex)
    }

    private companion object {
        const val VIEWPORT_START: Int = 0
        const val VIEWPORT_END: Int = 1000
        const val SHORT_ITEM_SIZE_PX: Int = 100
        const val TALL_ITEM_SIZE_PX: Int = 1500
    }
}
