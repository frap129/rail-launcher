package feature.launcher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.shortcuts.ShortcutLaunchable
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.InsertPosition
import core.popups.FolderDeleteHost
import core.popups.IPopupEditor
import core.popups.IPopupFolderActions
import core.popups.IPopupPicker
import core.popups.IPopupSession
import core.popups.PopupEditorHost
import core.popups.R as PopupsR
import core.popups.showFolderSnackbars
import core.ui.R as CoreUiR
import core.ui.model.data.Destination
import core.ui.model.data.HOME_ADD_WIDGET_REQUEST_KEY
import core.ui.model.data.HOME_INTENT_REQUEST_KEY
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.OneShotRequest
import core.ui.model.data.POPUPS_SETTINGS_ROUTE
import core.ui.model.data.WIDGETS_MEDIA_SETTINGS_ROUTE
import core.ui.model.data.WIDGETS_SETTINGS_ROUTE
import core.ui.util.consumeTouches
import core.util.openApplicationDetailsSettings
import core.util.requestQuietModeOff
import core.util.userForSerial
import core.widgets.AddWidgetFlowState
import core.widgets.AddWidgetOrigin
import core.widgets.AddWidgetPlacement
import core.widgets.AppWidgetPickerSheet
import core.widgets.AppWidgetResults
import core.widgets.AppWidgetViews
import core.widgets.IAppWidgetViewCache
import core.widgets.NotificationAccessDialog
import core.widgets.ResizeState
import core.widgets.UndoAction
import core.widgets.WidgetEvent
import feature.launcher.widgets.MoveWidgetAreaPanel
import feature.launcher.widgets.MoveWidgetAreaState
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.WidgetEditDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

val launcherDestination = Destination(
    route = LAUNCHER_ROUTE,
    content = { navController, backStackEntry ->
        LauncherScreen(navController, backStackEntry)
    },
    isWallpaperBacked = true
)

private val snackbarBottomPadding: Dp = 80.dp

@Composable
fun LauncherScreen(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: LauncherViewModel = koinViewModel(),
    widgetViewModel: WidgetAreaViewModel = koinViewModel()
) {
    // One session per Pop-up host; the card ViewModel keeps the collaborators it was created with.
    val popupSession: IPopupSession = koinInject()
    val popupCardViewModel: PopupCardViewModel = koinViewModel { parametersOf(popupSession) }
    val popupEditor: IPopupEditor = popupCardViewModel.collaborators.editor
    val popupPicker: IPopupPicker = popupCardViewModel.collaborators.picker
    val popupFolderActions: IPopupFolderActions = popupCardViewModel.collaborators.folder
    val context: Context = LocalContext.current
    val activity: ComponentActivity? = context as? ComponentActivity
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val railBounds: RailBounds = remember { RailBounds() }
    val finishEditingHomeMessage: String = stringResource(R.string.finish_editing_home)
    var showCalendarPermissionDialog: Boolean by remember { mutableStateOf(false) }
    var showNotificationAccessDialog: Boolean by remember { mutableStateOf(false) }
    val appWidgetResults: AppWidgetResults = koinInject()
    val appWidgetHostSource: IAppWidgetHostSource = koinInject()
    val appWidgetViewCache: IAppWidgetViewCache = koinInject()
    val homeIntentRequest: OneShotRequest = remember(backStackEntry) {
        OneShotRequest(savedStateHandle = backStackEntry.savedStateHandle, key = HOME_INTENT_REQUEST_KEY)
    }
    val addWidgetRequest: OneShotRequest = remember(backStackEntry) {
        OneShotRequest(savedStateHandle = backStackEntry.savedStateHandle, key = HOME_ADD_WIDGET_REQUEST_KEY)
    }
    val bindLauncher: ManagedActivityResultLauncher<Intent, ActivityResult> =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            widgetViewModel.onBindResult(granted = result.resultCode == Activity.RESULT_OK)
        }
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val canAskAgain: Boolean = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == true
            widgetViewModel.onCalendarPermissionResult(granted = granted, canAskAgain = canAskAgain)
        }
    val couldntOpenShortcutMessage: String = stringResource(R.string.couldnt_open_shortcut)
    val launchItem: (Launchable) -> Unit = launchItem@{ launchable ->
        if (launchable is Folder) {
            return@launchItem
        }
        popupCardViewModel.onLaunched()
        val didLaunch: Boolean = if (launchable is ShortcutLaunchable) {
            launchShortcut(shortcut = launchable, context = context) {
                coroutineScope.launch { snackbarHostState.showSnackbar(message = couldntOpenShortcutMessage) }
            }
        } else {
            launchable.launch(context)
            true
        }
        if (didLaunch) {
            widgetViewModel.onHomeIntent()
            viewModel.onLaunchableLaunched()
        }
    }
    DisposableEffect(lifecycleOwner, widgetViewModel) {
        val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    widgetViewModel.refreshCalendarPermission()
                    widgetViewModel.refreshMediaAccess()
                }

                Lifecycle.Event.ON_START -> appWidgetHostSource.startListening()

                Lifecycle.Event.ON_STOP -> {
                    popupCardViewModel.onStopped()
                    widgetViewModel.finishResize()
                    widgetViewModel.finishEditingWidgets()
                    widgetViewModel.finishMoveWidgetArea()
                    appWidgetHostSource.stopListening()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            appWidgetHostSource.startListening()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            appWidgetHostSource.stopListening()
        }
    }
    LaunchedEffect(viewModel, widgetViewModel, popupCardViewModel) {
        val widgetCallbacks: WidgetEventCallbacks = WidgetEventCallbacks(
            onBindResult = widgetViewModel::onBindResult,
            onConfigureResult = widgetViewModel::onConfigureResult,
            onReconfigureResult = widgetViewModel::onReconfigureResult,
            onScrollToPlace = widgetViewModel::requestScrollToPlace,
            onUndo = widgetViewModel::undo,
            onEditFavorites = viewModel::editFavorites
        )
        val popupCallbacks: WidgetEventCallbacks = WidgetEventCallbacks(
            onBindResult = popupCardViewModel.widgetFlow::onBindResult,
            onConfigureResult = popupCardViewModel.widgetFlow::onConfigureResult,
            onReconfigureResult = popupCardViewModel.widgetFlow::onReconfigureResult,
            onScrollToPlace = {},
            onUndo = {},
            onEditFavorites = viewModel::editFavorites
        )
        merge(
            widgetViewModel.events.map { event -> event to widgetCallbacks },
            popupCardViewModel.events.map { event -> event to popupCallbacks }
        ).collect { (event, callbacks) ->
            handleWidgetEvent(
                event = event,
                activity = activity,
                context = context,
                coroutineScope = coroutineScope,
                snackbarHostState = snackbarHostState,
                appWidgetHostSource = appWidgetHostSource,
                appWidgetViewCache = appWidgetViewCache,
                bindLauncher = bindLauncher,
                permissionLauncher = permissionLauncher,
                navController = navController,
                callbacks = callbacks,
                showCalendarPermissionDialog = { showCalendarPermissionDialog = true },
                showNotificationAccessDialog = { showNotificationAccessDialog = true }
            )
        }
    }
    val editFolderLabel: String = stringResource(PopupsR.string.edit_folder)
    LaunchedEffect(popupFolderActions, snackbarHostState, editFolderLabel) {
        showFolderSnackbars(
            folders = popupFolderActions.folderAdditions,
            snackbarHostState = snackbarHostState,
            actionLabel = editFolderLabel,
            message = { folder: Folder -> context.getString(PopupsR.string.added_to_folder, folder.name) },
            onAction = popupEditor::startEdit
        )
    }
    val settingsLabel: String = stringResource(CoreUiR.string.settings)
    LaunchedEffect(viewModel, navController, snackbarHostState, settingsLabel) {
        showFolderSnackbars(
            folders = viewModel.foldersOnlyInSettings,
            snackbarHostState = snackbarHostState,
            actionLabel = settingsLabel,
            message = { folder: Folder -> context.getString(R.string.folder_only_in_settings, folder.name) },
            onAction = { navController.navigate(POPUPS_SETTINGS_ROUTE) }
        )
    }
    LaunchedEffect(homeIntentRequest) {
        homeIntentRequest.collectRequests {
            popupCardViewModel.onHomeIntent()
            val wasEditingHome: Boolean =
                widgetViewModel.editState.value != null || widgetViewModel.moveState.value != null
            if (!widgetViewModel.onHomeIntent() || wasEditingHome) {
                viewModel.onHomeIntent()
            }
        }
    }
    LaunchedEffect(addWidgetRequest) {
        addWidgetRequest.collectRequests {
            val started: Boolean = widgetViewModel.startAdd(
                origin = AddWidgetOrigin(insertAt = InsertPosition.End, returnToSettings = true)
            )
            if (!started) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(message = finishEditingHomeMessage)
                }
            }
        }
    }
    LaunchedEffect(appWidgetResults) {
        appWidgetResults.results.collect { result ->
            // Only bind-configuration results change data; reconfigure results keep the widget either way.
            if (result.requestCode == AppWidgetResults.REQUEST_CODE) {
                widgetViewModel.onConfigureResult(ok = result.isOk)
                popupCardViewModel.widgetFlow.onConfigureResult(ok = result.isOk)
            }
        }
    }
    val addFlowState: AddWidgetFlowState by widgetViewModel.addFlowState.collectAsState()
    val popupAddFlowState: AddWidgetFlowState by popupCardViewModel.widgetFlow.state.collectAsState()
    val resizeState: ResizeState? by widgetViewModel.resizeState.collectAsState()
    val editDraft: WidgetEditDraft? by widgetViewModel.editState.collectAsState()
    val moveState: MoveWidgetAreaState? by widgetViewModel.moveState.collectAsState()
    val isResizing: Boolean = resizeState != null
    val isEditingWidgets: Boolean = editDraft != null
    val isMovingWidgetArea: Boolean = moveState != null
    val isEditingHome: Boolean = isResizing || isEditingWidgets || isMovingWidgetArea
    BackHandler {
        if (!popupCardViewModel.onBack() && !widgetViewModel.onBack()) {
            viewModel.onBack()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LauncherContent(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            widgetViewModel = widgetViewModel,
            railBounds = railBounds,
            onItemLaunch = launchItem,
            onItemOpenPopup = popupCardViewModel::open,
            onItemAnchorChanged = { owner, anchor -> popupCardViewModel.onAnchorChanged(owner.key, anchor) },
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets,
            isMovingWidgetArea = isMovingWidgetArea
        )
        PopupHost(
            viewModel = popupCardViewModel,
            onLaunch = launchItem,
            onAddItems = popupPicker::startFolderPicking,
            modifier = Modifier.fillMaxSize()
        )
        LauncherRail(
            viewModel = viewModel,
            railBounds = railBounds,
            isCardOpen = popupCardViewModel::isOpen,
            onRailTouched = popupCardViewModel::close,
            enabled = !isEditingHome
        )
        LauncherDoneButton(viewModel, widgetViewModel, popupEditor)
        LauncherMenu(viewModel, popupEditor, popupFolderActions)
        PopupMenuSheet(
            card = popupCardViewModel,
            editor = popupEditor,
            folderActions = popupFolderActions
        )
        PopupEditorHost(editor = popupEditor, picker = popupPicker)
        FolderDeleteHost(folderActions = popupFolderActions)
        AddToFolderHost(folderActions = popupFolderActions)
        LauncherWidgetMenu(widgetViewModel)
        if (isMovingWidgetArea) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeTouches(onGestureEnd = widgetViewModel::finishMoveWidgetArea)
            )
        }
        moveState?.let { state ->
            MoveWidgetAreaPanel(
                state = state,
                onDraftChange = widgetViewModel::setDraftOffset,
                onStep = widgetViewModel::stepOffset,
                onReset = widgetViewModel::resetOffset,
                onDone = widgetViewModel::finishMoveWidgetArea,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
            )
        }
        if (addFlowState is AddWidgetFlowState.Picking) {
            AppWidgetPickerSheet(
                state = addFlowState as AddWidgetFlowState.Picking,
                onQueryChange = widgetViewModel::setPickerQuery,
                onPick = widgetViewModel::pickProvider,
                onDismiss = widgetViewModel::cancelAdd
            )
        }
        if (addFlowState is AddWidgetFlowState.ChoosingPlacement) {
            AddPlacementSheet(
                canAddToStack = (addFlowState as AddWidgetFlowState.ChoosingPlacement).canAddToStack,
                onBelow = { widgetViewModel.choosePlacement(AddWidgetPlacement.Below) },
                onIntoStack = { widgetViewModel.choosePlacement(AddWidgetPlacement.IntoStack) },
                onDismiss = widgetViewModel::cancelAdd
            )
        }
        if (popupAddFlowState is AddWidgetFlowState.Picking) {
            AppWidgetPickerSheet(
                state = popupAddFlowState as AddWidgetFlowState.Picking,
                onQueryChange = popupCardViewModel.widgetFlow::setQuery,
                onPick = popupCardViewModel.widgetFlow::pickProvider,
                onDismiss = popupCardViewModel.widgetFlow::cancel
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = snackbarBottomPadding)
        )
    }
    if (showCalendarPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarPermissionDialog = false },
            title = { Text(text = stringResource(CoreUiR.string.calendar_permission_denied_title)) },
            text = { Text(text = stringResource(CoreUiR.string.calendar_permission_denied_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalendarPermissionDialog = false
                        context.openApplicationDetailsSettings()
                    }
                ) {
                    Text(text = stringResource(CoreUiR.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarPermissionDialog = false }) {
                    Text(text = stringResource(CoreUiR.string.cancel))
                }
            }
        )
    }
    if (showNotificationAccessDialog) {
        NotificationAccessDialog(onDismiss = { showNotificationAccessDialog = false })
    }
}

private fun launchShortcut(context: Context, shortcut: ShortcutLaunchable, onFailure: () -> Unit): Boolean =
    runCatching { shortcut.launch(context) }.fold(
        onSuccess = { true },
        onFailure = { exception ->
            if (exception is ActivityNotFoundException || exception is SecurityException || exception is IllegalStateException) {
                onFailure()
                false
            } else {
                throw exception
            }
        }
    )

private class WidgetEventCallbacks(
    val onBindResult: (Boolean) -> Unit,
    val onConfigureResult: (Boolean) -> Unit,
    val onReconfigureResult: (Boolean) -> Unit,
    val onScrollToPlace: (Long) -> Unit,
    val onUndo: (UndoAction) -> Unit,
    val onEditFavorites: () -> Unit
)

private suspend fun handleWidgetEvent(
    event: WidgetEvent,
    activity: ComponentActivity?,
    context: Context,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    appWidgetHostSource: IAppWidgetHostSource,
    appWidgetViewCache: IAppWidgetViewCache,
    bindLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    navController: NavController,
    callbacks: WidgetEventCallbacks,
    showCalendarPermissionDialog: () -> Unit,
    showNotificationAccessDialog: () -> Unit
) {
    when (event) {
        is WidgetEvent.ShowUndo -> coroutineScope.launch {
            val action: UndoAction = event.action
            val result: SnackbarResult = snackbarHostState.showSnackbar(
                message = when (action) {
                    is UndoAction.RemoveHeader -> context.getString(R.string.header_removed)
                    is UndoAction.ReplaceHeader -> context.getString(R.string.header_replaced)
                    is UndoAction.IncludeMediaApp -> context.getString(R.string.media_player_excluded, action.appLabel)
                },
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                callbacks.onUndo(action)
            }
        }

        WidgetEvent.RequestCalendarPermission -> permissionLauncher.launch(Manifest.permission.READ_CALENDAR)

        is WidgetEvent.ShowCalendarPermissionDenied -> {
            if (event.permanentlyDenied) {
                showCalendarPermissionDialog()
            } else {
                coroutineScope.launch {
                    val result: SnackbarResult = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.calendar_permission_needed),
                        actionLabel = context.getString(CoreUiR.string.open_settings),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        context.openApplicationDetailsSettings()
                    }
                }
            }
        }

        WidgetEvent.NavigateToWidgetSettings -> navController.navigate(WIDGETS_SETTINGS_ROUTE)

        WidgetEvent.NavigateToMediaPlayerSettings -> navController.navigate(WIDGETS_MEDIA_SETTINGS_ROUTE)

        WidgetEvent.EditFavorites -> callbacks.onEditFavorites()

        WidgetEvent.RequestNotificationAccess -> showNotificationAccessDialog()

        is WidgetEvent.LaunchBind -> try {
            bindLauncher.launch(AppWidgetViews.bindIntent(context = context, request = event.request))
        } catch (exception: ActivityNotFoundException) {
            callbacks.onBindResult(false)
        } catch (exception: SecurityException) {
            callbacks.onBindResult(false)
        }

        is WidgetEvent.LaunchConfigure -> {
            val currentActivity: ComponentActivity? = activity
            if (currentActivity == null) {
                callbacks.onConfigureResult(false)
            } else {
                try {
                    appWidgetHostSource.startConfiguring(
                        activity = currentActivity,
                        id = event.id,
                        requestCode = AppWidgetResults.REQUEST_CODE
                    )
                } catch (exception: ActivityNotFoundException) {
                    callbacks.onConfigureResult(false)
                } catch (exception: SecurityException) {
                    callbacks.onConfigureResult(false)
                }
            }
        }

        is WidgetEvent.LaunchReconfigure -> {
            val currentActivity: ComponentActivity? = activity
            if (currentActivity == null) {
                callbacks.onReconfigureResult(false)
            } else {
                try {
                    appWidgetHostSource.startConfiguring(
                        activity = currentActivity,
                        id = event.id,
                        requestCode = AppWidgetResults.RECONFIGURE_REQUEST_CODE
                    )
                } catch (exception: ActivityNotFoundException) {
                    callbacks.onReconfigureResult(false)
                } catch (exception: SecurityException) {
                    callbacks.onReconfigureResult(false)
                }
            }
        }

        is WidgetEvent.ShowMessage -> coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = event.formatArg?.let { arg -> context.getString(event.message, arg) }
                    ?: context.getString(event.message)
            )
        }

        is WidgetEvent.RemoveHostView -> appWidgetViewCache.remove(event.appWidgetId)

        is WidgetEvent.ReportWidgetSize -> appWidgetViewCache.updateSize(
            ids = event.appWidgetIds,
            widthDp = event.widthDp,
            heightDp = event.heightDp
        )

        is WidgetEvent.RequestQuietModeOff -> context.userForSerial(event.profileSerial)?.let { profile ->
            context.requestQuietModeOff(profile)
        }

        is WidgetEvent.ScrollToPlace -> callbacks.onScrollToPlace(event.placeId)

        WidgetEvent.ReturnToSettings -> navController.navigate(WIDGETS_SETTINGS_ROUTE)
    }
}
