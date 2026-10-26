package core.popups

import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.StateFlow

/** Owns the Edit Pop-up sheet's contents and the item writes it commits. */
interface IPopupEditor {
    /** What the Edit Pop-up sheet shows. */
    val editState: StateFlow<PopupEditState>

    /**
     * Opens the editor for an App or Folder. Allowed on Home and during Edit
     * Favorites; ignored in every other Home edit mode.
     */
    fun startEdit(owner: Launchable)

    fun finishEdit()

    /** Hides or shows a shortcut in the App currently being edited. */
    fun setShortcutHidden(shortcutId: String, hidden: Boolean)

    /** Removes [item] from the Pop-up currently being edited. */
    fun removeEditedItem(item: Launchable)

    fun setItemOrder(orderedItemKeys: List<String>)

    /** Dismisses the editor when its Pop-up closes. */
    fun onCardClosed()

    /** Closes the editor when it shows [ownerKey], for example because that Folder was deleted. */
    fun finishEditing(ownerKey: String)

    /** Closes the editor on Back; returns false when it was closed already. */
    fun backEditor(): Boolean

    /** Stops the observations this editor owns. */
    fun release()
}
