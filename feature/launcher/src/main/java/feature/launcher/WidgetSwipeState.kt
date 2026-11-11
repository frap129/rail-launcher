package feature.launcher

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the App Widget under the current Home touch took the swipe. The App Widget host view
 * marks it when a hosted view claims the gesture, such as a list that starts scrolling.
 */
internal class WidgetSwipeState {
    var isWidgetTakenSwipe: Boolean = false
        private set

    fun reset() {
        isWidgetTakenSwipe = false
    }

    fun markWidgetTookSwipe() {
        isWidgetTakenSwipe = true
    }
}

/** The [WidgetSwipeState] of Home, absent outside Home. */
internal val LocalWidgetSwipeState: ProvidableCompositionLocal<WidgetSwipeState?> = compositionLocalOf { null }
