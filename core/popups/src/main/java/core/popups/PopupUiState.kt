package core.popups

import core.data.launchables.model.Launchable

/** Whether a Pop-up is showing and, when it is, its owner and content. */
sealed interface PopupUiState {
    data object Closed : PopupUiState

    data class Open(val owner: Launchable, val anchor: PopupAnchor, val content: PopupContent) : PopupUiState
}
