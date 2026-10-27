package core.ui.popups

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import core.data.folders.Folder
import kotlinx.coroutines.flow.Flow

/**
 * Shows each Folder in [folders] as a Snackbar whose message is [message], and
 * runs [onAction] when the Snackbar action is taken. Collects [folders] for as
 * long as the caller's effect lives.
 */
suspend fun showFolderSnackbars(
    folders: Flow<Folder>,
    snackbarHostState: SnackbarHostState,
    actionLabel: String,
    message: (Folder) -> String,
    onAction: (Folder) -> Unit
) {
    folders.collect { folder: Folder ->
        val result: SnackbarResult = snackbarHostState.showSnackbar(
            message = message(folder),
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            onAction(folder)
        }
    }
}
