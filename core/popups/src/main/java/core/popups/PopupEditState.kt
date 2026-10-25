package core.popups

import core.data.launchables.model.Launchable
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutsResult

/** Whether the Edit Pop-up sheet is showing and, when it is, its live content. */
sealed interface PopupEditState {
    data object Closed : PopupEditState

    data class Editing(
        val owner: Launchable,
        val shortcuts: ShortcutsResult,
        val hiddenIds: Set<String>,
        val items: List<PopupItem>,
        val shortcutSourceNames: Map<String, String>
    ) : PopupEditState
}
