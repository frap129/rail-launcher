package feature.launcher

import androidx.compose.ui.geometry.Rect

/** The Rail's bounds in root coordinates: the area whose touches belong to the Rail. */
internal class RailBounds {
    var current: Rect? = null
        private set

    /** Stores the Rail's bounds after it is placed. */
    fun update(bounds: Rect) {
        current = bounds
    }
}
