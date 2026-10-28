package feature.launcher.widgets

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAreaHeightTest {
    @Test
    fun `returns null for an empty layout`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = emptyList(),
            totalItemsCount = 0
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(mockLayoutInfo)

        assertNull(actualHeightPx)
    }

    @Test
    fun `sums visible item heights and content padding`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = listOf(
                createLazyListItemInfoMock(size = FIRST_HEIGHT_PX),
                createLazyListItemInfoMock(size = SECOND_HEIGHT_PX)
            ),
            totalItemsCount = 2,
            beforeContentPadding = TOP_PADDING_PX,
            afterContentPadding = BOTTOM_PADDING_PX
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(mockLayoutInfo)

        assertEquals(FIRST_HEIGHT_PX + SECOND_HEIGHT_PX + TOP_PADDING_PX + BOTTOM_PADDING_PX, actualHeightPx)
    }

    @Test
    fun `sums visible items when a zero-height trailing item is outside the viewport`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = listOf(createLazyListItemInfoMock(size = HEADER_HEIGHT_PX, offset = 0)),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = HEADER_HEIGHT_PX + EDGE_PADDING_PX
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(mockLayoutInfo)

        assertEquals(HEADER_HEIGHT_PX + EDGE_PADDING_PX, actualHeightPx)
    }

    @Test
    fun `includes a zero-height trailing item at the viewport edge`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = listOf(
                createLazyListItemInfoMock(size = HEADER_HEIGHT_PX, offset = 0),
                createLazyListItemInfoMock(size = 0, offset = HEADER_HEIGHT_PX + EDGE_PADDING_PX)
            ),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = HEADER_HEIGHT_PX + EDGE_PADDING_PX
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(mockLayoutInfo)

        assertEquals(HEADER_HEIGHT_PX + EDGE_PADDING_PX, actualHeightPx)
    }

    @Test
    fun `uses the cap when the content is unmeasured`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = emptyList(),
            totalItemsCount = 0
        )

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = mockLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the content height below the cap`() {
        val mockLayoutInfo: LazyListLayoutInfo = singleItemLayout(size = CONTENT_HEIGHT_PX)

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = mockLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(CONTENT_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the cap when the content is at the cap`() {
        val mockLayoutInfo: LazyListLayoutInfo = singleItemLayout(size = MAX_HEIGHT_PX)

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = mockLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the cap when the content is above the cap`() {
        val mockLayoutInfo: LazyListLayoutInfo = singleItemLayout(size = MAX_HEIGHT_PX + 1)

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = mockLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `keeps the cap when a zero-height trailing item is at the edge`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = listOf(
                createLazyListItemInfoMock(size = MAX_HEIGHT_PX - EDGE_PADDING_PX, offset = 0),
                createLazyListItemInfoMock(size = 0, offset = MAX_HEIGHT_PX)
            ),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = MAX_HEIGHT_PX
        )

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = mockLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the content height when a zero-height trailing item is outside the viewport`() {
        val mockLayoutInfo: LazyListLayoutInfo = createLazyListLayoutInfoMock(
            visibleItemsInfo = listOf(createLazyListItemInfoMock(size = CONTENT_HEIGHT_PX, offset = 0)),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = CONTENT_HEIGHT_PX + EDGE_PADDING_PX
        )

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = mockLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(CONTENT_HEIGHT_PX + EDGE_PADDING_PX, actualHeightPx)
    }

    private fun singleItemLayout(size: Int): LazyListLayoutInfo = createLazyListLayoutInfoMock(
        visibleItemsInfo = listOf(createLazyListItemInfoMock(size = size)),
        totalItemsCount = 1
    )

    private companion object {
        const val FIRST_HEIGHT_PX: Int = 100
        const val SECOND_HEIGHT_PX: Int = 50
        const val TOP_PADDING_PX: Int = 10
        const val BOTTOM_PADDING_PX: Int = 5
        const val HEADER_HEIGHT_PX: Int = 302
        const val EDGE_PADDING_PX: Int = 84
        const val MAX_HEIGHT_PX: Int = 386
        const val CONTENT_HEIGHT_PX: Int = 200
    }
}
