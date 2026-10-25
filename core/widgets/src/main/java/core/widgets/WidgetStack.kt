package core.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.widgets.ResizeBounds
import core.ui.R as CoreUiR
import kotlin.math.abs
import kotlin.math.roundToInt

private val stackIndicatorHeight: Dp = ResizeBounds.STACK_INDICATOR_HEIGHT_DP.dp
private val stackIndicatorDotSize: Dp = 6.dp
private val stackIndicatorGap: Dp = 6.dp
private val horizontalDragDominanceFilter: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && abs(available.y) >= abs(available.x)) {
            Offset(available.x, 0f)
        } else {
            Offset.Zero
        }
}

/**
 * Shows [pageCount] Widget Stack pages one at a time, a page indicator below them when
 * [indicatorVisible], and only pages on drags whose horizontal component dominates.
 */
@Composable
fun WidgetStackPager(
    pageCount: Int,
    currentPage: Int,
    heightDp: Int?,
    indicatorVisible: Boolean,
    pageKey: (Int) -> Long,
    onPageSettled: (Int) -> Unit,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean,
    pageContent: @Composable (page: Int, pageHeightDp: Int) -> Unit
) {
    val lastIndex: Int = (pageCount - 1).coerceAtLeast(0)
    val pagerState: PagerState = rememberPagerState(initialPage = currentPage.coerceIn(0, lastIndex)) { pageCount }
    val showIndicator: Boolean = indicatorVisible && pageCount > 1
    val totalHeight: Dp = (heightDp ?: ResizeBounds.MIN_HEIGHT_DP).dp
    val pageHeight: Dp = (totalHeight - if (showIndicator) stackIndicatorHeight else 0.dp).coerceAtLeast(1.dp)
    val pageHeightDp: Int = pageHeight.value.roundToInt()
    LaunchedEffect(currentPage, pageCount) {
        val target: Int = currentPage.coerceIn(0, lastIndex)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        onPageSettled(pagerState.settledPage)
    }
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .nestedScroll(horizontalDragDominanceFilter),
            userScrollEnabled = userScrollEnabled,
            key = { page -> pageKey(page) }
        ) { page ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                pageContent(page, pageHeightDp)
            }
        }
        if (showIndicator) {
            StackPageIndicator(pageCount = pageCount, currentPage = pagerState.currentPage)
        }
    }
}

@Composable
private fun StackPageIndicator(pageCount: Int, currentPage: Int) {
    val description: String = stringResource(CoreUiR.string.stack_page_of, currentPage + 1, pageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stackIndicatorHeight)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = stackIndicatorGap / 2)
                    .size(stackIndicatorDotSize)
                    .background(
                        color = if (page == currentPage) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}
