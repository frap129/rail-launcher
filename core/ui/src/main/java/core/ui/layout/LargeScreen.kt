package core.ui.layout

/** The width, in dp, at which Rail enables large-screen behavior. */
const val LARGE_SCREEN_MIN_WIDTH_DP: Int = 600

/** Whether [widthDp] reaches Rail's shared large-screen threshold. */
fun isLargeScreen(widthDp: Int): Boolean = widthDp >= LARGE_SCREEN_MIN_WIDTH_DP
