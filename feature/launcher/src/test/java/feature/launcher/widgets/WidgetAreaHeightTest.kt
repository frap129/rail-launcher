package feature.launcher.widgets

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAreaHeightTest {
    @Test
    fun `returns null for an empty layout`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = emptyList(),
            totalItemsCount = 0
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(inputLayoutInfo)

        assertNull(actualHeightPx)
    }

    @Test
    fun `sums visible item heights and content padding`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = listOf(
                FakeItemInfo(index = 0, key = "first", offset = 0, size = FIRST_HEIGHT_PX),
                FakeItemInfo(index = 1, key = "second", offset = FIRST_HEIGHT_PX, size = SECOND_HEIGHT_PX)
            ),
            totalItemsCount = 2,
            beforeContentPadding = TOP_PADDING_PX,
            afterContentPadding = BOTTOM_PADDING_PX
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(inputLayoutInfo)

        assertEquals(FIRST_HEIGHT_PX + SECOND_HEIGHT_PX + TOP_PADDING_PX + BOTTOM_PADDING_PX, actualHeightPx)
    }

    @Test
    fun `sums visible items when a zero-height trailing item is outside the viewport`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = listOf(
                FakeItemInfo(index = 0, key = "header", offset = 0, size = HEADER_HEIGHT_PX)
            ),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = HEADER_HEIGHT_PX + EDGE_PADDING_PX
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(inputLayoutInfo)

        assertEquals(HEADER_HEIGHT_PX + EDGE_PADDING_PX, actualHeightPx)
    }

    @Test
    fun `includes a zero-height trailing item at the viewport edge`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = listOf(
                FakeItemInfo(index = 0, key = "header", offset = 0, size = HEADER_HEIGHT_PX),
                FakeItemInfo(
                    index = 1,
                    key = "media",
                    offset = HEADER_HEIGHT_PX + EDGE_PADDING_PX,
                    size = 0
                )
            ),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = HEADER_HEIGHT_PX + EDGE_PADDING_PX
        )

        val actualHeightPx: Int? = calculateVisibleItemsHeightPx(inputLayoutInfo)

        assertEquals(HEADER_HEIGHT_PX + EDGE_PADDING_PX, actualHeightPx)
    }

    @Test
    fun `uses the cap when the content is unmeasured`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = emptyList(),
            totalItemsCount = 0
        )

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = inputLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the content height below the cap`() {
        val inputLayoutInfo: LazyListLayoutInfo = singleItemLayout(size = CONTENT_HEIGHT_PX)

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = inputLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(CONTENT_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the cap when the content is at the cap`() {
        val inputLayoutInfo: LazyListLayoutInfo = singleItemLayout(size = MAX_HEIGHT_PX)

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = inputLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the cap when the content is above the cap`() {
        val inputLayoutInfo: LazyListLayoutInfo = singleItemLayout(size = MAX_HEIGHT_PX + 1)

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = inputLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `keeps the cap when a zero-height trailing item is at the edge`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = listOf(
                FakeItemInfo(index = 0, key = "header", offset = 0, size = MAX_HEIGHT_PX - EDGE_PADDING_PX),
                FakeItemInfo(index = 1, key = "media", offset = MAX_HEIGHT_PX, size = 0)
            ),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = MAX_HEIGHT_PX
        )

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = inputLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(MAX_HEIGHT_PX, actualHeightPx)
    }

    @Test
    fun `uses the content height when a zero-height trailing item is outside the viewport`() {
        val inputLayoutInfo: LazyListLayoutInfo = FakeLayoutInfo(
            visibleItemsInfo = listOf(
                FakeItemInfo(index = 0, key = "header", offset = 0, size = CONTENT_HEIGHT_PX)
            ),
            totalItemsCount = 2,
            beforeContentPadding = EDGE_PADDING_PX,
            viewportEndOffset = CONTENT_HEIGHT_PX + EDGE_PADDING_PX
        )

        val actualHeightPx: Int = calculateWidgetAreaHeightPx(
            layoutInfo = inputLayoutInfo,
            maxHeightPx = MAX_HEIGHT_PX
        )

        assertEquals(CONTENT_HEIGHT_PX + EDGE_PADDING_PX, actualHeightPx)
    }

    private fun singleItemLayout(size: Int): LazyListLayoutInfo = FakeLayoutInfo(
        visibleItemsInfo = listOf(FakeItemInfo(index = 0, key = "item", offset = 0, size = size)),
        totalItemsCount = 1
    )

    private class FakeLayoutInfo(
        override val visibleItemsInfo: List<LazyListItemInfo>,
        override val totalItemsCount: Int,
        override val beforeContentPadding: Int = 0,
        override val afterContentPadding: Int = 0,
        override val viewportStartOffset: Int = -beforeContentPadding,
        override val viewportEndOffset: Int = visibleItemsInfo.maxOfOrNull { item -> item.offset + item.size } ?: 0
    ) : LazyListLayoutInfo {
        override val reverseLayout: Boolean = false
        override val orientation: Orientation = Orientation.Vertical
        override val mainAxisItemSpacing: Int = 0
    }

    private class FakeItemInfo(
        override val index: Int,
        override val key: Any,
        override val offset: Int,
        override val size: Int,
        override val contentType: Any? = null
    ) : LazyListItemInfo

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
