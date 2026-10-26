package core.popups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import core.ui.R as CoreUiR

/** Hosts the Folder rename dialog; renders nothing while it is closed. */
@Composable
fun FolderRenameHost(folderViewModel: PopupFolderViewModel) {
    val dialogState: FolderDialogState by folderViewModel.folderDialogState.collectAsState()
    val renaming: FolderDialogState.Renaming? = dialogState as? FolderDialogState.Renaming
    if (renaming != null) {
        FolderNameDialog(
            initialName = renaming.folder.name,
            title = stringResource(R.string.rename_folder_title),
            confirmLabel = stringResource(CoreUiR.string.done),
            onConfirm = folderViewModel::confirmRename,
            onDismiss = folderViewModel::dismissFolderDialog
        )
    }
}
