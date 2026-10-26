package feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import core.popups.FolderDialogState
import core.popups.FolderNameDialog
import core.popups.IPopupFolderActions
import core.popups.R as PopupsR
import core.ui.R as CoreUiR

/** Hosts the Folder rename dialog; renders nothing while it is closed. */
@Composable
fun FolderRenameHost(folderActions: IPopupFolderActions) {
    val dialogState: FolderDialogState by folderActions.folderDialogState.collectAsState()
    val renaming: FolderDialogState.Renaming? = dialogState as? FolderDialogState.Renaming
    if (renaming != null) {
        FolderNameDialog(
            initialName = renaming.folder.name,
            title = stringResource(PopupsR.string.rename_folder_title),
            confirmLabel = stringResource(CoreUiR.string.done),
            onConfirm = folderActions::confirmRename,
            onDismiss = folderActions::dismissFolderDialog
        )
    }
}
