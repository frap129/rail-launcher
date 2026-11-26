package core.ui.composables.scrollrail

import androidx.compose.ui.geometry.Rect

/** The Rail's bounds in root coordinates: the area whose touches belong to the Rail. */
class RailBounds {
    var current: Rect? = null
        private set

    /** Stores the Rail's bounds after it is placed. */
    fun update(bounds: Rect) {
        current = bounds
    }

    /** Forgets the stored bounds once nothing takes Rail touches. */
    fun clear() {
        current = null
    }
}
