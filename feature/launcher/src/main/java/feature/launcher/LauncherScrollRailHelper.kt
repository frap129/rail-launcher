package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import core.ui.composables.scrollrail.ScrollRailHelper

class LauncherScrollRailHelper(
    override val railItems: List<Char>,
    private val viewModel: LauncherViewModel,
    private val lazyListState: LazyListState
) : ScrollRailHelper {

    override suspend fun onScrollStarted(index: Int) {
        onScroll(index, -1)
        lazyListState.scrollToItem(0)
    }

    override suspend fun onScroll(currIndex: Int, prevIndex: Int) {
        viewModel.scrolling.value = true
        viewModel.selectedGroup.value = railItems[currIndex]
    }

    override suspend fun onScrollEnded(index: Int) {
        viewModel.scrolling.value = false
    }
}
