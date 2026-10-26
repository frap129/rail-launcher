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
import androidx.compose.runtime.SideEffect
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
import core.popups.FolderRenameHost
import core.popups.IPopupSession
import core.popups.PopupEditorHost
import core.popups.PopupEditorViewModel
import core.popups.PopupFolderViewModel
import core.popups.PopupPickerViewModel
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
fun SettingsPopupsScreen(viewModel: PopupSettingsViewModel = koinViewModel()) {
    // One session per Pop-up host; the ViewModels keep the instance they were created with.
    val popupSession: IPopupSession = koinInject()
    val popupEditorViewModel: PopupEditorViewModel = koinViewModel { parametersOf(popupSession) }
    val popupPickerViewModel: PopupPickerViewModel = koinViewModel { parametersOf(popupSession) }
    val popupFolderViewModel: PopupFolderViewModel = koinViewModel { parametersOf(popupSession) }
    SideEffect {
        popupSession.attach(
            editor = popupEditorViewModel,
            picker = popupPickerViewModel,
            folder = popupFolderViewModel
        )
    }
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
    LaunchedEffect(viewModel, popupEditorViewModel, snackbarHostState, folderCreatedMessage, editContentsLabel) {
        showFolderSnackbars(
            folders = viewModel.createdFolders,
            snackbarHostState = snackbarHostState,
            actionLabel = editContentsLabel,
            message = { folderCreatedMessage },
            onAction = popupEditorViewModel::startEdit
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
            onRenameClick = { popupFolderViewModel.requestRename(selectedFolder.folder) },
            onEditContents = {
                viewModel.closeActions()
                popupEditorViewModel.startEdit(selectedFolder.folder)
            },
            onOnHomeChange = viewModel::setOnHome,
            onInAppListChange = { inAppList -> popupFolderViewModel.setShowInAppList(selectedFolder.folder, inAppList) },
            onDeleteClick = { popupFolderViewModel.requestDeleteFolder(selectedFolder.folder) }
        )
    }
    if (uiState.isNewFolderDialogOpen) {
        FolderNameDialog(
            onConfirm = viewModel::createFolder,
            onDismiss = viewModel::closeNewFolderDialog
        )
    }
    FolderDeleteHost(folderViewModel = popupFolderViewModel)
    FolderRenameHost(folderViewModel = popupFolderViewModel)
    if (showNotificationAccessDialog) {
        NotificationAccessDialog(onDismiss = { showNotificationAccessDialog = false })
    }
    PopupEditorHost(editorViewModel = popupEditorViewModel, pickerViewModel = popupPickerViewModel)
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
