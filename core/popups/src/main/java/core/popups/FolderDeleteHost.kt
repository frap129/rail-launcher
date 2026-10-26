package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Hosts the Folder delete confirmation; renders nothing while it is closed. */
@Composable
fun FolderDeleteHost(folderViewModel: PopupFolderViewModel) {
    val dialogState: FolderDialogState by folderViewModel.folderDialogState.collectAsState()
    val deleting: FolderDialogState.Deleting? = dialogState as? FolderDialogState.Deleting
    if (deleting != null) {
        FolderDeleteDialog(
            folder = deleting.folder,
            onConfirm = folderViewModel::confirmDeleteFolder,
            onDismiss = folderViewModel::dismissFolderDialog
        )
    }
}
