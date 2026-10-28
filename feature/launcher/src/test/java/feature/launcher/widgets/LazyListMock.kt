package feature.launcher.widgets

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import io.mockk.every
import io.mockk.mockk

/** A MockK [LazyListLayoutInfo] over [visibleItemsInfo], with the given viewport. */
fun createLazyListLayoutInfoMock(
    visibleItemsInfo: List<LazyListItemInfo>,
    totalItemsCount: Int,
    beforeContentPadding: Int = 0,
    afterContentPadding: Int = 0,
    viewportEndOffset: Int = visibleItemsInfo.maxOfOrNull { item -> item.offset + item.size } ?: 0
): LazyListLayoutInfo {
    val mockLazyListLayoutInfo: LazyListLayoutInfo = mockk()
    every { mockLazyListLayoutInfo.visibleItemsInfo } returns visibleItemsInfo
    every { mockLazyListLayoutInfo.totalItemsCount } returns totalItemsCount
    every { mockLazyListLayoutInfo.beforeContentPadding } returns beforeContentPadding
    every { mockLazyListLayoutInfo.afterContentPadding } returns afterContentPadding
    every { mockLazyListLayoutInfo.viewportStartOffset } returns -beforeContentPadding
    every { mockLazyListLayoutInfo.viewportEndOffset } returns viewportEndOffset
    return mockLazyListLayoutInfo
}

/** A MockK [LazyListItemInfo] with [size] at [offset]. */
fun createLazyListItemInfoMock(size: Int, offset: Int = 0): LazyListItemInfo {
    val mockLazyListItemInfo: LazyListItemInfo = mockk()
    every { mockLazyListItemInfo.offset } returns offset
    every { mockLazyListItemInfo.size } returns size
    return mockLazyListItemInfo
}
