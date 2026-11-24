package core.ui.layout

import android.content.pm.ActivityInfo

/**
 * Locks phone-sized screens to portrait and lets large screens follow the
 * system's auto-rotate setting.
 */
fun resolveRequestedOrientation(smallestScreenWidthDp: Int): Int = if (isLargeScreen(widthDp = smallestScreenWidthDp)) {
    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
} else {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
