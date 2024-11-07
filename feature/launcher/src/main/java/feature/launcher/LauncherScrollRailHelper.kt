package feature.launcher

import core.ui.composables.scrollrail.ScrollRailHelper

class LauncherScrollRailHelper(override val railItems: List<Char>, private val viewModel: LauncherViewModel) : ScrollRailHelper {

    override suspend fun onScrollStarted(index: Int) = onScroll(index, -1)

    override suspend fun onScroll(currIndex: Int, prevIndex: Int) {
        viewModel.scrolling.value = true
        viewModel.selectedGroup.value = railItems[currIndex]
    }

    override suspend fun onScrollEnded(index: Int) {
        viewModel.scrolling.value = false
    }
}
