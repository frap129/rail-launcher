package feature.launcher

import androidx.compose.foundation.lazy.LazyListState
import core.ui.composables.scrollrail.ScrollRailHelper

class LauncherScrollRailHelper(
    override val railItems: List<Char>,
    private val viewModel: LauncherViewModel,
    private val lazyListState: LazyListState,
    private val endScrollOffset: Int
) : ScrollRailHelper() {

    override suspend fun onScrollStarted(index: Int) {
        viewModel.scrolling.value = true
        viewModel.visibleGroups.clear()
        viewModel.visibleGroups.add(railItems[index])
    }

    override suspend fun onScroll(currIndex: Int, prevIndex: Int) {
        viewModel.visibleGroups.replaceAll { railItems[currIndex] }
    }

    override suspend fun onScrollEnded(index: Int) {
        viewModel.scrolling.value = false
        viewModel.visibleGroups.clear()
        viewModel.visibleGroups.addAll(railItems)
        lazyListState.scrollToItem(
            index + 1,
            endScrollOffset

        )
    }
}
