package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Hosts the Folder delete confirmation; renders nothing while it is closed. */
@Composable
fun FolderDeleteHost(folderActions: IPopupFolderActions) {
    val dialogState: FolderDialogState by folderActions.folderDialogState.collectAsState()
    val deleting: FolderDialogState.Deleting? = dialogState as? FolderDialogState.Deleting
    if (deleting != null) {
        FolderDeleteDialog(
            folder = deleting.folder,
            onConfirm = folderActions::confirmDeleteFolder,
            onDismiss = folderActions::dismissFolderDialog
        )
    }
}
