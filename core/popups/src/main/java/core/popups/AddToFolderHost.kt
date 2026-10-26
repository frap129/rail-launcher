package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Hosts the Add to Folder sheet; renders nothing while it is closed. */
@Composable
fun AddToFolderHost(folderViewModel: PopupFolderViewModel) {
    val addToFolderState: AddToFolderState by folderViewModel.addToFolderState.collectAsState()
    val openAddToFolderState: AddToFolderState.Open? = addToFolderState as? AddToFolderState.Open
    if (openAddToFolderState != null) {
        AddToFolderSheet(
            launchable = openAddToFolderState.launchable,
            folders = openAddToFolderState.folders,
            membership = openAddToFolderState.membership,
            onToggle = folderViewModel::toggleMembership,
            onNewFolder = folderViewModel::startNewFolder,
            onDismiss = folderViewModel::closeAddToFolder
        )
    }
}
