package feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.folders.Folder
import core.popups.FolderDeleteDialog
import core.popups.FolderNameDialog
import core.popups.PopupEditorHost
import core.popups.PopupViewModel
import core.popups.R as PopupsR
import core.popups.showFolderSnackbars
import core.ui.R as CoreUiR
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetTitle
import org.koin.androidx.compose.koinViewModel

private val folderIconSize: Dp = 32.dp
private val emptyStateGap: Dp = 16.dp
private val createButtonPadding: Dp = 16.dp

/** Lists every Folder, with the actions sheet and dialogs that manage it. */
@Composable
fun SettingsPopupsScreen(viewModel: PopupSettingsViewModel = koinViewModel(), popupViewModel: PopupViewModel = koinViewModel()) {
    val uiState: PopupSettingsUiState by viewModel.uiState.collectAsState()
    val deletingFolder: Folder? by popupViewModel.folderDeleteState.collectAsState()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val selectedFolder: PopupSettingsFolder? = uiState.selectedFolder
    val folderCreatedMessage: String = stringResource(R.string.folder_created)
    val editContentsLabel: String = stringResource(R.string.edit_contents)
    LaunchedEffect(viewModel, popupViewModel, snackbarHostState, folderCreatedMessage, editContentsLabel) {
        showFolderSnackbars(
            folders = viewModel.createdFolders,
            snackbarHostState = snackbarHostState,
            actionLabel = editContentsLabel,
            message = { folderCreatedMessage },
            onAction = popupViewModel::startEdit
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        FolderSettingsList(
            folders = uiState.folders,
            onFolderClick = viewModel::openActions,
            onCreateClick = viewModel::openNewFolderDialog
        )
        SettingsSnackbarHost(hostState = snackbarHostState)
    }
    if (selectedFolder != null) {
        FolderActionsSheet(
            row = selectedFolder,
            onDismiss = viewModel::closeActions,
            onRenameClick = viewModel::openRenameDialog,
            onEditContents = {
                viewModel.closeActions()
                popupViewModel.startEdit(selectedFolder.folder)
            },
            onOnHomeChange = viewModel::setOnHome,
            onInAppListChange = viewModel::setInAppList,
            onDeleteClick = { popupViewModel.requestDeleteFolder(selectedFolder.folder) }
        )
    }
    if (uiState.isNewFolderDialogOpen) {
        FolderNameDialog(
            onConfirm = viewModel::createFolder,
            onDismiss = viewModel::closeNewFolderDialog
        )
    }
    if (uiState.isRenameDialogOpen && selectedFolder != null) {
        FolderNameDialog(
            initialName = selectedFolder.folder.name,
            title = stringResource(PopupsR.string.rename_folder_title),
            confirmLabel = stringResource(CoreUiR.string.done),
            onConfirm = viewModel::renameFolder,
            onDismiss = viewModel::closeRenameDialog
        )
    }
    deletingFolder?.let { folder: Folder ->
        FolderDeleteDialog(
            folder = folder,
            onConfirm = popupViewModel::confirmDeleteFolder,
            onDismiss = popupViewModel::cancelDeleteFolder
        )
    }
    PopupEditorHost(viewModel = popupViewModel)
}

@Composable
private fun FolderSettingsList(folders: List<PopupSettingsFolder>, onFolderClick: (Folder) -> Unit, onCreateClick: () -> Unit) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.no_folders_yet), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(emptyStateGap))
                CreateFolderButton(onClick = onCreateClick)
            }
        }
        return
    }
    LazyColumn {
        items(items = folders, key = { row -> row.folder.key }) { row: PopupSettingsFolder ->
            FolderSettingsRow(row = row, onClick = { onFolderClick(row.folder) })
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(createButtonPadding),
                contentAlignment = Alignment.Center
            ) {
                CreateFolderButton(onClick = onCreateClick)
            }
        }
    }
}

@Composable
private fun CreateFolderButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Text(stringResource(R.string.create_folder))
    }
}

@Composable
private fun FolderSettingsRow(row: PopupSettingsFolder, onClick: () -> Unit) {
    val itemCountLabel: String = pluralStringResource(R.plurals.folder_item_count, row.itemCount, row.itemCount)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(text = row.folder.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = stringResource(R.string.folder_summary, itemCountLabel, folderLocationSummary(location = row.location)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            AsyncImage(
                modifier = Modifier.size(folderIconSize),
                model = row.folder.icon,
                contentDescription = null
            )
        }
    )
}

@Composable
private fun folderLocationSummary(location: FolderLocation): String = when (location) {
    FolderLocation.Home -> stringResource(R.string.on_home)
    FolderLocation.AppList -> stringResource(R.string.in_app_list)
    FolderLocation.HomeAndAppList -> stringResource(R.string.folder_location_home_and_app_list)
    FolderLocation.NotShown -> stringResource(R.string.folder_location_not_shown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionsSheet(
    row: PopupSettingsFolder,
    onDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onEditContents: () -> Unit,
    onOnHomeChange: (Boolean) -> Unit,
    onInAppListChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column {
            SheetTitle(launchable = row.folder)
            SettingsActionRow(title = stringResource(R.string.name), value = row.folder.name, onClick = onRenameClick)
            SettingsActionRow(title = stringResource(R.string.edit_contents), value = "", onClick = onEditContents)
            SettingsSwitchRow(title = stringResource(R.string.on_home), checked = row.isOnHome, onCheckedChange = onOnHomeChange)
            SettingsSwitchRow(
                title = stringResource(R.string.in_app_list),
                checked = row.folder.showInAppList,
                onCheckedChange = onInAppListChange
            )
            SettingsDestructiveRow(title = stringResource(PopupsR.string.delete_folder), onClick = onDeleteClick)
        }
    }
}
