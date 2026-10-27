package feature.settings

import android.content.Context
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.folders.Folder
import core.popups.FolderDeleteHost
import core.popups.FolderNameDialog
import core.popups.IPopupEditor
import core.popups.IPopupFolderActions
import core.popups.IPopupPicker
import core.popups.IPopupSession
import core.popups.PopupEditorHost
import core.popups.R as PopupsR
import core.popups.showFolderSnackbars
import core.ui.composables.NotificationAccessRow
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetTitle
import core.widgets.NotificationAccessDialog
import core.widgets.openRailNotificationListenerSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private val folderIconSize: Dp = 32.dp
private val emptyStateGap: Dp = 16.dp
private val createButtonPadding: Dp = 16.dp

/** Lists every Folder, with the actions sheet and dialogs that manage it. */
@Composable
fun SettingsPopupsScreen() {
    // One session per Pop-up host; the settings ViewModel owns the collaborators it was created with.
    val popupSession: IPopupSession = koinInject()
    val viewModel: PopupSettingsViewModel = koinViewModel { parametersOf(popupSession) }
    val popupEditor: IPopupEditor = viewModel.collaborators.editor
    val popupPicker: IPopupPicker = viewModel.collaborators.picker
    val popupFolderActions: IPopupFolderActions = viewModel.collaborators.folder
    val uiState: PopupSettingsUiState by viewModel.uiState.collectAsState()
    val context: Context = LocalContext.current
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    var showNotificationAccessDialog: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == WidgetSettingsEvent.RequestNotificationAccess) {
                showNotificationAccessDialog = true
            }
        }
    }
    val selectedFolder: PopupSettingsFolder? = uiState.selectedFolder
    val folderCreatedMessage: String = stringResource(R.string.folder_created)
    val editContentsLabel: String = stringResource(R.string.edit_contents)
    LaunchedEffect(viewModel, popupEditor, snackbarHostState, folderCreatedMessage, editContentsLabel) {
        showFolderSnackbars(
            folders = viewModel.createdFolders,
            snackbarHostState = snackbarHostState,
            actionLabel = editContentsLabel,
            message = { folderCreatedMessage },
            onAction = popupEditor::startEdit
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        PopupSettingsList(
            uiState = uiState,
            onFolderClick = viewModel::openActions,
            onCreateClick = viewModel::openNewFolderDialog,
            onNotificationsChange = viewModel::setNotificationsEnabled,
            onNotificationAccessClick = { context.openRailNotificationListenerSettings() }
        )
        SettingsSnackbarHost(hostState = snackbarHostState)
    }
    if (selectedFolder != null) {
        FolderActionsSheet(
            row = selectedFolder,
            onDismiss = viewModel::closeActions,
            onRenameClick = { popupFolderActions.requestRename(selectedFolder.folder) },
            onEditContents = {
                viewModel.closeActions()
                popupEditor.startEdit(selectedFolder.folder)
            },
            onOnHomeChange = viewModel::setOnHome,
            onDeleteClick = { popupFolderActions.requestDeleteFolder(selectedFolder.folder) }
        )
    }
    if (uiState.isNewFolderDialogOpen) {
        FolderNameDialog(
            onConfirm = viewModel::createFolder,
            onDismiss = viewModel::closeNewFolderDialog
        )
    }
    FolderDeleteHost(folderActions = popupFolderActions)
    FolderRenameHost(folderActions = popupFolderActions)
    if (showNotificationAccessDialog) {
        NotificationAccessDialog(onDismiss = { showNotificationAccessDialog = false })
    }
    PopupEditorHost(editor = popupEditor, picker = popupPicker)
}

@Composable
private fun PopupSettingsList(
    uiState: PopupSettingsUiState,
    onFolderClick: (Folder) -> Unit,
    onCreateClick: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNotificationAccessClick: () -> Unit
) {
    LazyColumn {
        item { SettingsSectionTitle(R.string.notifications) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_notifications_in_popups),
                checked = uiState.notificationsEnabled,
                onCheckedChange = onNotificationsChange
            )
        }
        item {
            NotificationAccessRow(
                hasAccess = uiState.notificationAccess,
                onClick = onNotificationAccessClick
            )
        }
        item { SettingsSectionTitle(R.string.folders) }
        if (uiState.folders.isEmpty()) {
            item { EmptyFolderSettings(onCreateClick = onCreateClick) }
        } else {
            items(items = uiState.folders, key = { row -> row.folder.key }) { row: PopupSettingsFolder ->
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
}

@Composable
private fun EmptyFolderSettings(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(createButtonPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.no_folders_yet), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(emptyStateGap))
        CreateFolderButton(onClick = onCreateClick)
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
    FolderLocation.AppList -> stringResource(R.string.in_app_list)
    FolderLocation.HomeAndAppList -> stringResource(R.string.folder_location_home_and_app_list)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionsSheet(
    row: PopupSettingsFolder,
    onDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onEditContents: () -> Unit,
    onOnHomeChange: (Boolean) -> Unit,
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
            SettingsDestructiveRow(title = stringResource(PopupsR.string.delete_folder), onClick = onDeleteClick)
        }
    }
}
