package core.lifecycle

/**
 * Tracks whether a HOME intent arrived while the launcher was already resumed and focused.
 *
 * Android pauses a resumed activity immediately before delivering a new intent, so by
 * [wasResumedAtHomeIntent] time the activity is no longer resumed. The activity therefore records
 * the state just before that pause: it must have been resumed and have had window focus, or the
 * press was aimed at a translucent or dialog activity that already paused the launcher. Losing
 * window focus or stopping clears the record, so a press that returns from another app, Recents
 * or such an overlay can never open Search.
 */
class HomePressState {
    private var isResumed: Boolean = false
    private var hasWindowFocus: Boolean = false
    private var wasResumedAndFocusedUntilPause: Boolean = false

    fun onResume() {
        isResumed = true
        wasResumedAndFocusedUntilPause = false
    }

    fun onPause() {
        wasResumedAndFocusedUntilPause = isResumed && hasWindowFocus
        isResumed = false
    }

    fun onStop() {
        isResumed = false
        hasWindowFocus = false
        wasResumedAndFocusedUntilPause = false
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        hasWindowFocus = hasFocus
        if (!hasFocus) {
            wasResumedAndFocusedUntilPause = false
        }
    }

    /** Whether the launcher was already resumed and focused when the current HOME intent arrived. */
    fun wasResumedAtHomeIntent(): Boolean = wasResumedAndFocusedUntilPause
}
