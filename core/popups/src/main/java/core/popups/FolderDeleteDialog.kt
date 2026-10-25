package core.popups

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import core.data.folders.Folder
import core.ui.R as CoreUiR

/** Confirms deleting a Folder and explains that its apps stay installed. */
@Composable
fun FolderDeleteDialog(viewModel: PopupViewModel) {
    val folder: Folder? by viewModel.folderDeleteState.collectAsState()
    val target: Folder = folder ?: return
    AlertDialog(
        onDismissRequest = viewModel::cancelDeleteFolder,
        title = { Text(stringResource(R.string.delete_folder_title, target.name)) },
        text = { Text(stringResource(R.string.delete_folder_body)) },
        confirmButton = {
            TextButton(onClick = viewModel::confirmDeleteFolder) {
                Text(
                    text = stringResource(R.string.delete_folder),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelDeleteFolder) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}
