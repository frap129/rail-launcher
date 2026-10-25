package core.popups

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import core.data.folders.Folder
import kotlinx.coroutines.flow.Flow

/** A Folder shown in a Snackbar, with the message to show for it. */
data class FolderSnackbar(val message: String, val folder: Folder)

/**
 * Shows each Folder Snackbar in [snackbarHostState] and opens the Folder's editor
 * when its action is taken. Collects [snackbars] for as long as the caller's effect lives.
 */
suspend fun showFolderSnackbars(
    snackbars: Flow<FolderSnackbar>,
    snackbarHostState: SnackbarHostState,
    actionLabel: String,
    onEdit: (Folder) -> Unit
) {
    snackbars.collect { snackbar: FolderSnackbar ->
        val result: SnackbarResult = snackbarHostState.showSnackbar(
            message = snackbar.message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            onEdit(snackbar.folder)
        }
    }
}
