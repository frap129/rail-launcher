package core.popups

import core.data.folders.Folder

/** The Folder dialog the Folder collaborator is showing, if any. */
sealed interface FolderDialogState {
    data object Closed : FolderDialogState

    data class Deleting(val folder: Folder) : FolderDialogState

    data class Renaming(val folder: Folder) : FolderDialogState
}
