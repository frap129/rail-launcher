package core.popups

import core.data.shortcuts.AppShortcuts

/** One App List group in the Shortcut picker. */
data class ShortcutPickerGroup(val label: String, val apps: List<AppShortcuts>)

/** Whether the Add shortcuts picker is showing and, when it is, its candidates and checked items. */
sealed interface ShortcutPickerState {
    data object Closed : ShortcutPickerState

    data class Picking(val groups: List<ShortcutPickerGroup>, val selectedKeys: Set<String>, val shortcutsUnavailable: Boolean) :
        ShortcutPickerState
}
