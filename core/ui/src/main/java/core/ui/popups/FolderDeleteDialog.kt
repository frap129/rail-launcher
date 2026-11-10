package core.ui.popups

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import core.data.folders.Folder
import core.ui.R
import core.ui.composables.DestructiveConfirmationDialog

/** Confirms deleting [folder] and explains that its apps stay installed. */
@Composable
fun FolderDeleteDialog(folder: Folder, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DestructiveConfirmationDialog(
        title = stringResource(R.string.delete_folder_title, folder.name),
        body = stringResource(R.string.delete_folder_body),
        confirmLabel = stringResource(R.string.delete_folder),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
