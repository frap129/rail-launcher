package core.ui.popups

import core.data.folders.Folder
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Owns the Folder dialogs and the Add to Folder sheet. */
interface IPopupFolderActions {
    /** The Folder dialog the collaborator is showing, if any. */
    val folderDialogState: StateFlow<FolderDialogState>

    /** Folders created from the Add to Folder sheet, for its "Added to ..." Snackbar. */
    val folderAdditions: Flow<Folder>

    /** What the Add to Folder sheet shows. */
    val addToFolderState: StateFlow<AddToFolderState>

    /** Shows the delete confirmation when [target] is the Folder whose menu is open. */
    fun requestDeleteFolder(target: Launchable)

    /** Shows the name dialog for [folder]. */
    fun requestRename(folder: Folder)

    /** Closes the Folder dialog without changing the Folder. */
    fun dismissFolderDialog()

    /** Deletes the Folder whose confirmation is showing and everything belonging to it. */
    fun confirmDeleteFolder()

    /** Renames the Folder whose name dialog is showing, resetting its name when [name] is blank. */
    fun confirmRename(name: String)

    /** Opens the Add to Folder sheet for [launchable], which must not be a Folder. */
    fun startAddToFolder(launchable: Launchable)

    fun closeAddToFolder()

    /** Adds or removes the Add to Folder sheet's Launchable from [folderKey]. */
    fun toggleMembership(folderKey: String, member: Boolean)

    /** Creates a Folder holding the Add to Folder sheet's Launchable, then reports it for the Snackbar. */
    fun startNewFolder(name: String)

    /** Closes the dialogs and sheet when the Pop-up closes. */
    fun onCardClosed()

    /** Closes the Add to Folder sheet when the editor opens. */
    fun onEditStarted()

    /** Closes the Add to Folder sheet on Back; returns false when it was closed already. */
    fun backBeforeItemMenu(): Boolean

    /** Stops the observations these actions own. */
    fun release()
}
