package feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import core.ui.R as CoreUiR
import core.ui.popups.FolderDialogState
import core.ui.popups.FolderNameDialog
import core.ui.popups.IPopupFolderActions

/** Hosts the Folder rename dialog; renders nothing while it is closed. */
@Composable
fun FolderRenameHost(folderActions: IPopupFolderActions) {
    val dialogState: FolderDialogState by folderActions.folderDialogState.collectAsState()
    val renaming: FolderDialogState.Renaming? = dialogState as? FolderDialogState.Renaming
    if (renaming != null) {
        FolderNameDialog(
            initialName = renaming.folder.name,
            title = stringResource(R.string.rename_folder_title),
            confirmLabel = stringResource(CoreUiR.string.done),
            onConfirm = folderActions::confirmRename,
            onDismiss = folderActions::dismissFolderDialog
        )
    }
}
