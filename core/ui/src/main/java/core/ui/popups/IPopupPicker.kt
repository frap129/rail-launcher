package core.ui.popups

import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.StateFlow

/** Owns the item and Shortcut pickers the Edit Pop-up sheet opens. */
interface IPopupPicker {
    /** What the Add apps picker shows. */
    val pickerState: StateFlow<PopupPickerState>

    /** What the Add Shortcuts picker shows. */
    val shortcutPickerState: StateFlow<ShortcutPickerState>

    fun startPicking(owner: Launchable)

    /** Opens the item picker straight from an empty Folder's card. */
    fun startFolderPicking()

    fun confirmPicking(selectedInOrder: List<String>)

    fun cancelPicking()

    fun startPickingShortcuts(owner: Launchable)

    fun confirmShortcuts(selectedInOrder: List<String>)

    fun cancelShortcutPicking()

    /** Clears the pickers an opening card would cover. */
    fun onCardOpened()

    /** Clears the pickers a closing card would cover. */
    fun onCardClosed()

    /** Clears the pickers an opening editor would cover. */
    fun onEditStarted()

    /** Clears the pickers a closing editor would cover. */
    fun onEditFinished()

    /** Closes the topmost picker on Back; returns false when both are closed. */
    fun backBeforeItemMenu(): Boolean

    /** Stops this picker's flows. */
    fun release()
}
