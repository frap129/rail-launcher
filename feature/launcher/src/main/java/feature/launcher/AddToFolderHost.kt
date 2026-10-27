package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import core.ui.popups.AddToFolderState
import core.ui.popups.IPopupFolderActions

/** Hosts the Add to Folder sheet; renders nothing while it is closed. */
@Composable
fun AddToFolderHost(folderActions: IPopupFolderActions) {
    val addToFolderState: AddToFolderState by folderActions.addToFolderState.collectAsState()
    val openAddToFolderState: AddToFolderState.Open? = addToFolderState as? AddToFolderState.Open
    if (openAddToFolderState != null) {
        AddToFolderSheet(
            launchable = openAddToFolderState.launchable,
            folders = openAddToFolderState.folders,
            membership = openAddToFolderState.membership,
            onToggle = folderActions::toggleMembership,
            onNewFolder = folderActions::startNewFolder,
            onDismiss = folderActions::closeAddToFolder
        )
    }
}
