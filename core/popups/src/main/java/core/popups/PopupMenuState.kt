package core.popups

import core.data.launchables.model.Launchable

/** One action in the open Pop-up's menu. */
sealed interface PopupMenuItem {
    data object EditPopup : PopupMenuItem

    data object AppInfo : PopupMenuItem
}

/** Whether the open Pop-up's menu is showing and, when it is, its actions. */
sealed interface PopupMenuState {
    data object Closed : PopupMenuState

    data class Open(val owner: Launchable, val items: List<PopupMenuItem>) : PopupMenuState
}
