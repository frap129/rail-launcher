package core.popups

import core.data.launchables.model.Launchable

/** One action in a Pop-up menu or an added item's menu. */
sealed interface PopupMenuItem {
    data object EditPopup : PopupMenuItem

    data object EditFolder : PopupMenuItem

    data object DeleteFolder : PopupMenuItem

    data object AddWidget : PopupMenuItem

    data object AddToStack : PopupMenuItem

    data object ResizeWidget : PopupMenuItem

    data object ReconfigureWidget : PopupMenuItem

    data object RemoveWidget : PopupMenuItem

    data object RemoveFromStack : PopupMenuItem

    data object RemoveFromPopup : PopupMenuItem

    data object AppInfo : PopupMenuItem
}

/** Whether a Pop-up menu is showing and, when it is, its target and actions. */
sealed interface PopupMenuState {
    data object Closed : PopupMenuState

    data class Open(
        val target: Launchable,
        val items: List<PopupMenuItem>,
        /** The Stack page whose menu this is; null unless the menu belongs to a widget page. */
        val widgetPage: PopupWidgetPage? = null
    ) : PopupMenuState
}
