package feature.launcher

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import core.ui.composables.scrollrail.ScrollRailHelper
import core.util.screenHeightPx

class LauncherScrollRailHelper(
    private val context: Context,
    override val railItems: List<Char>,
    private val listState: LazyListState,
    private val visibilities: MutableMap<Char, MutableState<Boolean>>
) : ScrollRailHelper {

    override suspend fun onScrollStarted(index: Int) = onScroll(index, -1)

    override suspend fun onScroll(currIndex: Int, prevIndex: Int) {
        listState.scrollToItem(
            index = currIndex + 1,
            scrollOffset = -(screenHeightPx(context) / 5)
        )
        listState.layoutInfo.visibleItemsInfo.forEach {
            // Ignore spacers at start/end of list
            if (it.index > 0 && it.index <= railItems.size) {
                visibilities[railItems[it.index - 1]]?.value = false
            }
        }
        visibilities[railItems[currIndex]]?.value = true
    }

    override suspend fun onScrollEnded(index: Int) {
        visibilities.keys.forEach { key -> visibilities[key]?.value = true }
    }
}
