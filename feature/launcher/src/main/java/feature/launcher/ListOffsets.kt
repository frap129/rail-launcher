package feature.launcher

/**
 * Vertical pixel offsets of the visible App List area.
 *
 * @property startPx Offset of the App List from the top of the screen.
 * @property endPx Offset of the App List from the bottom of the screen.
 */
data class ListOffsets(val startPx: Int, val endPx: Int) {
    companion object {
        private const val SCREEN_HEIGHT_DIVISOR: Int = 5

        fun forScreenHeight(screenHeightPx: Int): ListOffsets {
            val startPx: Int = screenHeightPx / SCREEN_HEIGHT_DIVISOR
            return ListOffsets(startPx = startPx, endPx = screenHeightPx - startPx)
        }
    }
}
