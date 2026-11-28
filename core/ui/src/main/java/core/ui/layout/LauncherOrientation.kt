package core.ui.layout

import android.content.pm.ActivityInfo

/** Resolves the stored Rotate Home choice, defaulting by the screen's smallest width. */
fun resolveRotateHome(rotateHome: Boolean?, smallestScreenWidthDp: Int): Boolean =
    rotateHome ?: isLargeScreen(widthDp = smallestScreenWidthDp)

/**
 * Locks Home to portrait when Rotate Home is off and lets it follow the system's
 * auto-rotate setting when it is on.
 */
fun resolveRequestedOrientation(smallestScreenWidthDp: Int, rotateHome: Boolean?): Int =
    if (resolveRotateHome(rotateHome = rotateHome, smallestScreenWidthDp = smallestScreenWidthDp)) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
