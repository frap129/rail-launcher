package core.ui.popups

import core.data.folders.Folder
import core.data.launchables.model.Launchable

/** Whether the Add to Folder sheet is showing and, when it is, its Launchable, Folders and membership. */
sealed interface AddToFolderState {
    data object Closed : AddToFolderState

    data class Open(val launchable: Launchable, val folders: List<Folder>, val membership: Set<String>) : AddToFolderState
}
