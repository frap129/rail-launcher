package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.popups.CheckboxLaunchableRow
import core.popups.FolderNameDialog
import core.popups.PopupEmptyState
import core.popups.PopupSheetTitleRow
import core.popups.R as PopupsR
import core.ui.composables.SheetDragHandle

private val folderRowMinHeight: Dp = 56.dp

/** The sheet for filing [launchable] into Folders, shown from the item menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToFolderSheet(
    launchable: Launchable,
    folders: List<Folder>,
    membership: Set<String>,
    onToggle: (folderKey: String, member: Boolean) -> Unit,
    onNewFolder: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isNamingFolder: Boolean by remember(launchable.key) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PopupSheetTitleRow(title = stringResource(R.string.add_to_folder_title, launchable.name))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                if (folders.isEmpty()) {
                    PopupEmptyState(messageResId = R.string.no_folders)
                }
                folders.forEach { folder: Folder ->
                    CheckboxLaunchableRow(
                        launchable = folder,
                        isChecked = folder.key in membership,
                        onCheckedChange = { member -> onToggle(folder.key, member) },
                        minHeight = folderRowMinHeight
                    )
                }
                NewFolderRow(
                    label = stringResource(PopupsR.string.new_folder),
                    onClick = { isNamingFolder = true }
                )
            }
        }
    }
    if (isNamingFolder) {
        FolderNameDialog(
            onConfirm = { name ->
                isNamingFolder = false
                onNewFolder(name)
            },
            onDismiss = { isNamingFolder = false }
        )
    }
}
