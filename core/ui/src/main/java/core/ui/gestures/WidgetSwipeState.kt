package core.ui.gestures

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the App Widget under the current Home touch took the swipe. The App Widget host view
 * marks it when a hosted view claims the gesture, such as a list that starts scrolling.
 */
class WidgetSwipeState {
    var isWidgetTakenSwipe: Boolean by mutableStateOf(false)
        private set

    fun reset() {
        isWidgetTakenSwipe = false
    }

    fun markWidgetTookSwipe() {
        isWidgetTakenSwipe = true
    }
}

/** The [WidgetSwipeState] of Home, absent outside Home. */
val LocalWidgetSwipeState: ProvidableCompositionLocal<WidgetSwipeState?> = compositionLocalOf { null }
