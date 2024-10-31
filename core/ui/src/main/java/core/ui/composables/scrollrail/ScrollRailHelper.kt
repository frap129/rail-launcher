package core.ui.composables.scrollrail

/**
 * A helper for using [core.ui.composables.scrollrail.ScrollRail]. This
 * interface provides callbacks for handling scrolling.
 *
 * @property railItems the list of items on the [core.ui.composables.scrollrail.ScrollRail]
 */

interface ScrollRailHelper {
    val railItems: List<Char>

    /**
     * Called when a user initially touches the [core.ui.composables.scrollrail.ScrollRail]
     *
     * @param index the index of the item touched
     */
    suspend fun onScrollStarted(index: Int)

    /**
     * Called when a user scrolls along the [core.ui.composables.scrollrail.ScrollRail]
     *
     * @param currIndex the index of the item touched
     * @param prevIndex the index of the last item touched
     */
    suspend fun onScroll(currIndex: Int, prevIndex: Int)

    /**
     * Called when scrolling on the [core.ui.composables.scrollrail.ScrollRail]
     * ends, either because the user has stopped touching the rail, or
     * the input has been interrupted.
     *
     * @param index the index of the last item touched
     */
    suspend fun onScrollEnded(index: Int)
}
