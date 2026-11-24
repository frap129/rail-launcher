package core.lifecycle

/**
 * Tracks the two independent facts a HOME intent needs: whether Rail is being re-selected while
 * shown, and whether the launcher had window focus for the Home-press/Search entry.
 *
 * Leaving Rail for another activity by user action, or a stop, marks a return from another app
 * until the next resume, so a HOME intent that arrives before the launched app stops Rail still
 * keeps the current screen. A pause without either keeps the launcher shown, so HOME after a pause
 * that Rail did not leave for still returns Home. Focus is recorded while resumed and snapshotted
 * on pause because Android may pause before delivering the intent. Focus survives pause because
 * window focus does not change across the pause and resume around intent delivery; losing focus
 * or stopping clears it so only a focused Home press can open Search.
 */
class HomePressState {
    private var isResumed: Boolean = false
    private var hasWindowFocus: Boolean = false
    private var isShown: Boolean = false
    private var wasResumedAndFocusedUntilPause: Boolean = false

    fun onResume() {
        isResumed = true
        isShown = true
        wasResumedAndFocusedUntilPause = false
    }

    fun onPause() {
        wasResumedAndFocusedUntilPause = isResumed && hasWindowFocus
        isResumed = false
    }

    fun onUserLeaveHint() {
        isShown = false
    }

    fun onStop() {
        isResumed = false
        hasWindowFocus = false
        isShown = false
        wasResumedAndFocusedUntilPause = false
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        hasWindowFocus = hasFocus
        if (!hasFocus) {
            wasResumedAndFocusedUntilPause = false
        }
    }

    /**
     * Whether the launcher had window focus while resumed when the current HOME intent arrived.
     * The live term covers devices that deliver HOME without a preceding pause; otherwise the
     * pre-pause snapshot supplies the focus state.
     */
    fun wasFocusedAtHomeIntent(): Boolean = (isResumed && hasWindowFocus) || wasResumedAndFocusedUntilPause

    /** Whether the HOME intent presses the shown launcher rather than returning from another app. */
    fun shouldShowHomeAtHomeIntent(): Boolean = isShown
}
