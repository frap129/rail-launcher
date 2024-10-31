package core.ui.composables.scrollrail

interface ScrollRailHelper {
    val railItems: List<Char>

    suspend fun onScrollStarted(index: Int)
    suspend fun onScroll(currIndex: Int, prevIndex: Int)
    suspend fun onScrollEnded(index: Int)
}
