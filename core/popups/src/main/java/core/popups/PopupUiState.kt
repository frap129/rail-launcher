package core.popups

import core.data.launchables.model.Launchable
import core.data.popups.PopupContent

/** Whether a Pop-up is showing and, when it is, its owner and content. */
sealed interface PopupUiState {
    data object Closed : PopupUiState

    data class Open(
        val owner: Launchable,
        /** The row the card is anchored to; null while a reopened card waits for its row to lay out. */
        val anchor: PopupAnchor?,
        val content: PopupContent,
        val widget: PopupWidgetState? = null,
        /** The clock reading notification ages are measured against. */
        val nowMillis: Long
    ) : PopupUiState
}
