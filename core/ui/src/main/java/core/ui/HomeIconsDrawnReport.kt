package core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The Favorite icons Home has settled and whether Home has reported them. */
class HomeIconsDrawnReport(private val homeIconsDrawn: IHomeIconsDrawn) {
    /** The Favorite icons Home has settled; Home records them through [SettledHomeIcons.settle]. */
    val settledIcons: SettledHomeIcons = SettledHomeIcons()

    /** Whether Home has reported Home icons drawn. */
    var isReported: Boolean by mutableStateOf(false)
        private set

    /** Reports Home icons drawn and records that Home has reported it. */
    fun report() {
        homeIconsDrawn.report()
        isReported = true
    }
}
