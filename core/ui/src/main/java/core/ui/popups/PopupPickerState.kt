package core.ui.popups

import core.data.launchables.model.LaunchableGroup

/** Whether the Add apps picker is showing and, when it is, its candidates and checked items. */
sealed interface PopupPickerState {
    data object Closed : PopupPickerState

    data class Picking(val groups: List<LaunchableGroup.Listed>, val selectedKeys: Set<String>) : PopupPickerState
}
