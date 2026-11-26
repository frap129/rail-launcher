package feature.launcher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import core.data.calendar.ICalendarPermissionGate
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.profiles.IProfileSource
import core.data.search.SearchResult
import core.data.shortcuts.ShortcutLaunchable
import core.data.time.IClockSource
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.InsertPosition
import core.ui.GroupMark
import core.ui.IHomeIconsDrawn
import core.ui.R as CoreUiR
import core.ui.agenda.AgendaSheet
import core.ui.calendar.openCalendarEvent
import core.ui.calendar.openSystemCalendar
import core.ui.composables.CalendarPermissionResumeRefresh
import core.ui.composables.KeyboardShortcutsHelpSheet
import core.ui.composables.OnResume
import core.ui.composables.PermissionDeniedDialog
import core.ui.composables.rememberPermissionResultLauncher
import core.ui.composables.scrollrail.RailBounds
import core.ui.layout.HomeEditMode
import core.ui.model.data.Destination
import core.ui.model.data.HOME_ADD_WIDGET_REQUEST_KEY
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.LauncherInputState
import core.ui.model.data.LauncherSignal
import core.ui.model.data.LauncherSignalRequest
import core.ui.model.data.OneShotRequest
import core.ui.model.data.SEARCH_SETTINGS_ROUTE
import core.ui.model.data.SETTINGS_MAIN_ROUTE
import core.ui.model.data.WIDGETS_MEDIA_SETTINGS_ROUTE
import core.ui.model.data.WIDGETS_SETTINGS_ROUTE
import core.ui.popups.AddToFolderHost
import core.ui.popups.FolderDeleteHost
import core.ui.popups.IIconPicker
import core.ui.popups.IPopupEditor
import core.ui.popups.IPopupFolderActions
import core.ui.popups.IPopupPicker
import core.ui.popups.IPopupSession
import core.ui.popups.IconPickerHost
import core.ui.popups.PopupAnchor
import core.ui.popups.PopupEditorHost
import core.ui.popups.showFolderSnackbars
import core.ui.search.ContactRowAction
import core.ui.search.openContactRow
import core.ui.search.openSearchResultRow
import core.ui.util.consumeTouches
import core.ui.widgets.AddPlacementSheet
import core.ui.widgets.AddWidgetFlowState
import core.ui.widgets.AddWidgetOrigin
import core.ui.widgets.AddWidgetPlacement
import core.ui.widgets.AppWidgetPickerSheet
import core.ui.widgets.AppWidgetResults
import core.ui.widgets.AppWidgetViews
import core.ui.widgets.IAppWidgetViewCache
import core.ui.widgets.MoveWidgetAreaPanel
import core.ui.widgets.MoveWidgetAreaState
import core.ui.widgets.NotificationAccessDialog
import core.ui.widgets.ResizeState
import core.ui.widgets.UndoAction
import core.ui.widgets.WidgetEditDraft
import core.ui.widgets.WidgetEvent
import core.util.attemptLaunch
import core.util.copyCalculatorRowResult
import core.util.openApplicationDetailsSettings
import core.util.tryStartIntentSender
import feature.launcher.agenda.AgendaEvent
import feature.launcher.agenda.AgendaUiState
import feature.launcher.agenda.AgendaViewModel
import feature.launcher.widgets.WidgetAreaViewModel
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
    widgetViewModel: WidgetAreaViewModel = koinViewModel(),
    agendaViewModel: AgendaViewModel = koinViewModel()
) {
    // One session per Pop-up host; the card ViewModel keeps the collaborators it was created with.
    val popupSession: IPopupSession = koinInject()
    val popupCardViewModel: PopupCardViewModel = koinViewModel { parametersOf(popupSession) }
    val popupEditor: IPopupEditor = popupCardViewModel.collaborators.editor
    val popupPicker: IPopupPicker = popupCardViewModel.collaborators.picker
    val popupIconPicker: IIconPicker = popupCardViewModel.collaborators.iconPicker
    val popupFolderActions: IPopupFolderActions = popupCardViewModel.collaborators.folder
    val viewModel: LauncherViewModel = koinViewModel()
    val context: Context = LocalContext.current
    val activity: ComponentActivity? = context as? ComponentActivity
    val keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val railBounds: RailBounds = remember { RailBounds() }
    val finishEditingHomeMessage: String = stringResource(R.string.finish_editing_home)
    var showCalendarPermissionDialog: Boolean by remember { mutableStateOf(false) }
    var showNotificationAccessDialog: Boolean by remember { mutableStateOf(false) }
    var isKeyboardShortcutsHelpOpen: Boolean by rememberSaveable { mutableStateOf(false) }
    val appWidgetResults: AppWidgetResults = koinInject()
    val appWidgetHostSource: IAppWidgetHostSource = koinInject()
    val appWidgetViewCache: IAppWidgetViewCache = koinInject()
    val calendarPermissionGate: ICalendarPermissionGate = koinInject()
    val profileSource: IProfileSource = koinInject()
    val launcherInputState: LauncherInputState = koinInject()
    val homeIconsDrawn: IHomeIconsDrawn = koinInject()
    val clockSource: IClockSource = koinInject()
    val uiState: LauncherUiState by viewModel.uiState.collectAsState()
    val agendaState: AgendaUiState by agendaViewModel.uiState.collectAsState()
    val isPopupOpen: Boolean by popupCardViewModel.isCardOpen.collectAsState()
    val pausedLaunchable: Launchable? by viewModel.pausedPrompt.collectAsState()
    val railItems: List<GroupMark> by viewModel.railItems.collectAsState()
    val railSide: RailSide by viewModel.railSide.collectAsState()
    val scrubEdges: ScrubEdges by viewModel.scrubEdges.collectAsState()
    val railHaptics: Boolean by viewModel.railHaptics.collectAsState()
    val showRail: Boolean by viewModel.showRail.collectAsState()
    val homeEditMode: HomeEditMode by viewModel.homeEditMode.collectAsState()
    val launcherSignalRequest: LauncherSignalRequest = remember(backStackEntry) {
        LauncherSignalRequest(savedStateHandle = backStackEntry.savedStateHandle)
    }
    val requestOpenAgenda: () -> Unit = { launcherSignalRequest.request(signal = LauncherSignal.OpenAgenda) }
    val addWidgetRequest: OneShotRequest = remember(backStackEntry) {
        OneShotRequest(savedStateHandle = backStackEntry.savedStateHandle, key = HOME_ADD_WIDGET_REQUEST_KEY)
    }
    val bindLauncher: ManagedActivityResultLauncher<Intent, ActivityResult> =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            widgetViewModel.onBindResult(granted = result.resultCode == Activity.RESULT_OK)
        }
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CALENDAR) { granted, canAskAgain ->
            widgetViewModel.onCalendarPermissionResult(granted = granted, canAskAgain = canAskAgain)
        }
    val agendaPermissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CALENDAR) { granted, canAskAgain ->
            agendaViewModel.onCalendarPermissionResult(granted = granted, canAskAgain = canAskAgain)
        }
    val couldntOpenShortcutMessage: String = stringResource(R.string.couldnt_open_shortcut)
    val couldntOpenSearchRowMessage: String = stringResource(R.string.couldnt_open_search_row)
    val couldntOpenStorePageMessage: String = stringResource(R.string.couldnt_open_store_page)
    val calculatorCopiedMessage: String = stringResource(R.string.calculator_copied)
    val attemptSearchRowOpen: (open: () -> Boolean) -> Unit = { open ->
        keyboardController?.hide()
        if (open()) {
            viewModel.closeSearch()
        } else {
            coroutineScope.showSnackbar(snackbarHostState = snackbarHostState, message = couldntOpenSearchRowMessage)
        }
    }
    val openSearchRow: (SearchResult.ExternalRow) -> Unit = { row ->
        attemptSearchRowOpen { openSearchResultRow(context = context, row = row) }
    }
    val openContactRowAction: (SearchResult.ContactRow, ContactRowAction) -> Unit = { row, action ->
        attemptSearchRowOpen { openContactRow(context = context, contact = row.contact, action = action) }
    }
    val launchLaunchable: (Launchable) -> Unit = { launchable ->
        launchLauncherItem(
            launchable = launchable,
            context = context,
            viewModel = viewModel,
            effects = LaunchItemEffects(
                onHideKeyboard = { keyboardController?.hide() },
                onDisabledLaunch = {
                    coroutineScope.showSnackbar(snackbarHostState = snackbarHostState, message = couldntOpenShortcutMessage)
                },
                onPopupLaunched = popupCardViewModel::onLaunched,
                onLaunchFailed = { failedLaunchable ->
                    val message: String = if (failedLaunchable is ShortcutLaunchable) {
                        couldntOpenShortcutMessage
                    } else {
                        context.getString(R.string.cannot_open_launchable, failedLaunchable.name)
                    }
                    coroutineScope.showSnackbar(snackbarHostState = snackbarHostState, message = message)
                },
                finishHomeInteractions = { widgetViewModel.finishHomeInteractions(resetScroll = false) }
            )
        )
    }
    val openLaunchablePopup: (Launchable, PopupAnchor?) -> Unit = { launchable, anchor ->
        openLauncherItemPopup(
            launchable = launchable,
            anchor = anchor,
            popupCard = popupCardViewModel,
            viewModel = viewModel
        )
    }
    val openRow: (Launchable, PopupAnchor?) -> Unit = { launchable, anchor ->
        if (launchable is Folder) {
            openLaunchablePopup(launchable, anchor)
        } else {
            launchLaunchable(launchable)
        }
    }
    val closeAgendaThenOpen: (open: () -> Unit) -> Unit = { open ->
        agendaViewModel.close()
        open()
    }
    DisposableEffect(lifecycleOwner, viewModel, widgetViewModel) {
        val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.setLauncherStarted(isStarted = true)
                    appWidgetHostSource.startListening()
                }

                Lifecycle.Event.ON_STOP -> {
                    viewModel.setLauncherStarted(isStarted = false)
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
    CalendarPermissionResumeRefresh(calendarPermissionGate = calendarPermissionGate)
    OnResume {
        widgetViewModel.refreshMediaAccess()
        viewModel.refreshContactsPermission()
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
                profileSource = profileSource,
                onRequestQuietModeOff = viewModel::turnOnProfile,
                bindLauncher = bindLauncher,
                permissionLauncher = permissionLauncher,
                navController = navController,
                callbacks = callbacks,
                showCalendarPermissionDialog = { showCalendarPermissionDialog = true },
                showNotificationAccessDialog = { showNotificationAccessDialog = true }
            )
        }
    }
    LaunchedEffect(agendaViewModel) {
        agendaViewModel.events.collect { event ->
            when (event) {
                AgendaEvent.ShowCalendarPermissionDenied -> showCalendarPermissionDialog = true
            }
        }
    }
    val editFolderLabel: String = stringResource(R.string.edit_folder)
    LaunchedEffect(popupFolderActions, snackbarHostState, editFolderLabel) {
        showFolderSnackbars(
            folders = popupFolderActions.folderAdditions,
            snackbarHostState = snackbarHostState,
            actionLabel = editFolderLabel,
            message = { folder: Folder -> context.getString(R.string.added_to_folder, folder.name) },
            onAction = popupEditor::startEdit
        )
    }
    LaunchedEffect(uiState, homeEditMode, isPopupOpen) {
        launcherInputState.setAcceptsTyping(
            acceptsTyping = viewModel.canAcceptTypedCharacter(isPopupOpen = isPopupOpen)
        )
        launcherInputState.setCanToggleAppList(
            canToggleAppList = viewModel.canToggleAppList(isPopupOpen = isPopupOpen)
        )
    }
    LaunchedEffect(launcherSignalRequest) {
        launcherSignalRequest.collectRequests { signal ->
            if (signal.closesPopupCard) {
                popupCardViewModel.onHomeIntent()
            }
            when (signal) {
                is LauncherSignal.Home -> {
                    agendaViewModel.close()
                    isKeyboardShortcutsHelpOpen = false
                    val wasEditingHome: Boolean =
                        widgetViewModel.editState.value != null || widgetViewModel.moveState.value != null
                    if (!widgetViewModel.finishHomeInteractions(resetScroll = signal.shouldShowHome) || wasEditingHome) {
                        viewModel.onHomeIntent(signal)
                    }
                }

                LauncherSignal.OpenSearch -> viewModel.openSearch()

                LauncherSignal.OpenAgenda -> agendaViewModel.open()

                LauncherSignal.OpenSettings -> navController.navigate(route = SETTINGS_MAIN_ROUTE)

                LauncherSignal.ToggleAppList -> viewModel.toggleAppList()

                LauncherSignal.OpenKeyboardShortcutsHelp -> isKeyboardShortcutsHelpOpen = true

                is LauncherSignal.OpenIndex -> {
                    val target: LauncherRowTarget? = viewModel.rowTargetForOpenIndex(
                        index = signal.index,
                        isPopupOpen = popupCardViewModel.isCardOpen.value
                    )
                    when (target) {
                        null -> Unit
                        is LauncherRowTarget.OpenLaunchable -> openRow(target.launchable, null)
                        LauncherRowTarget.UnlockPrivateSpace -> viewModel.requestPrivateSpaceUnlock()
                        LauncherRowTarget.LockPrivateSpace -> viewModel.lockPrivateSpace()
                        is LauncherRowTarget.OpenPrivateSpaceSettings -> context.tryStartIntentSender(target.intentSender)
                    }
                }

                is LauncherSignal.TypedCharacter -> viewModel.onTypedCharacter(
                    character = signal.character,
                    isPopupOpen = popupCardViewModel.isCardOpen.value
                )
            }
        }
    }
    LaunchedEffect(addWidgetRequest) {
        addWidgetRequest.collectRequests {
            val started: Boolean = widgetViewModel.startAdd(
                origin = AddWidgetOrigin(insertAt = InsertPosition.End, returnToSettings = true)
            )
            if (!started) {
                coroutineScope.showSnackbar(snackbarHostState = snackbarHostState, message = finishEditingHomeMessage)
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
            railSide = railSide,
            showRail = showRail,
            onItemLaunch = launchLaunchable,
            onRowTap = openRow,
            onOpenAgenda = requestOpenAgenda,
            onItemOpenPopup = openLaunchablePopup,
            onItemAnchorChanged = { owner, anchor -> popupCardViewModel.onAnchorChanged(owner.key, anchor) },
            onCopyCalculatorRow = { result ->
                // Android 13 and later show the system's own clipboard confirmation.
                if (copyCalculatorRowResult(context = context, result = result) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    coroutineScope.showSnackbar(snackbarHostState = snackbarHostState, message = calculatorCopiedMessage)
                }
            },
            onOpenSearchRow = openSearchRow,
            onContactRowAction = openContactRowAction,
            onOpenSearchSettings = { navController.navigate(route = SEARCH_SETTINGS_ROUTE) },
            homeIconsDrawn = homeIconsDrawn,
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets,
            homeEditMode = homeEditMode
        )
        if (agendaState.isOpen) {
            AgendaSheet(
                days = agendaState.days,
                isLoading = agendaState.isLoading,
                hasPermission = agendaState.hasPermission,
                onOpenEvent = { target -> closeAgendaThenOpen { context.openCalendarEvent(target = target) } },
                onOpenCalendar = {
                    closeAgendaThenOpen { context.openSystemCalendar(atEpochMillis = clockSource.now().toEpochMilli()) }
                },
                onGrantPermission = { agendaPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onDismiss = agendaViewModel::close
            )
        }
        if (isKeyboardShortcutsHelpOpen) {
            KeyboardShortcutsHelpSheet(onDismiss = { isKeyboardShortcutsHelpOpen = false })
        }
        PopupHost(
            viewModel = popupCardViewModel,
            railSide = railSide,
            onLaunch = launchLaunchable,
            onAddItems = popupPicker::startFolderPicking,
            modifier = Modifier.fillMaxSize()
        )
        LauncherRail(
            railItems = railItems,
            uiState = { viewModel.uiState.value },
            railBounds = railBounds,
            railSide = railSide,
            scrubEdges = scrubEdges,
            showRail = showRail,
            isCardOpen = popupCardViewModel::isOpen,
            railHaptics = railHaptics,
            onRailTouched = popupCardViewModel::close,
            onRailPreview = viewModel::onRailPreview,
            onRailReleased = viewModel::onRailReleased,
            homeEditMode = homeEditMode
        )
        LauncherDoneButton(viewModel, widgetViewModel, popupEditor)
        LauncherMenu(
            viewModel = viewModel,
            collaborators = popupCardViewModel.collaborators,
            onStorePageFailed = {
                coroutineScope.showSnackbar(snackbarHostState = snackbarHostState, message = couldntOpenStorePageMessage)
            }
        )
        PopupMenuSheet(
            card = popupCardViewModel,
            editor = popupEditor,
            folderActions = popupFolderActions
        )
        PopupEditorHost(editor = popupEditor, picker = popupPicker)
        IconPickerHost(iconPicker = popupIconPicker)
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
        PermissionDeniedDialog(
            title = stringResource(CoreUiR.string.calendar_permission_denied_title),
            message = stringResource(CoreUiR.string.calendar_permission_denied_message),
            onDismiss = { showCalendarPermissionDialog = false },
            onOpenSettings = {
                showCalendarPermissionDialog = false
                context.openApplicationDetailsSettings()
            }
        )
    }
    if (showNotificationAccessDialog) {
        NotificationAccessDialog(onDismiss = { showNotificationAccessDialog = false })
    }
    if (pausedLaunchable != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPausedPrompt,
            title = { Text(text = stringResource(CoreUiR.string.work_apps_paused)) },
            confirmButton = {
                TextButton(onClick = viewModel::turnOnPausedLaunchable) {
                    Text(text = stringResource(CoreUiR.string.turn_on))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPausedPrompt) {
                    Text(text = stringResource(CoreUiR.string.cancel))
                }
            }
        )
    }
}

/** The UI effects the launcher's single launch path runs around a launch. */
internal class LaunchItemEffects(
    val onHideKeyboard: () -> Unit,
    val onDisabledLaunch: () -> Unit,
    val onPopupLaunched: () -> Unit,
    val onLaunchFailed: (Launchable) -> Unit,
    val finishHomeInteractions: () -> Unit
)

/**
 * The launcher's single launch path, shared by rows, Favorites, Pop-ups and Tap actions.
 * Reaches onLaunched only when [launchable] opened.
 */
internal fun launchLauncherItem(launchable: Launchable, context: Context, viewModel: LauncherViewModel, effects: LaunchItemEffects) {
    if (launchable is Folder) {
        return
    }
    if (viewModel.isSearchOpen()) {
        effects.onHideKeyboard()
    }
    if (launchable.isDisabled) {
        effects.onDisabledLaunch()
        return
    }
    if (!viewModel.requestLaunch(launchable)) {
        return
    }
    effects.onPopupLaunched()
    val didLaunch: Boolean = attemptLaunch { launchable.launch(context) }
    if (!didLaunch) {
        effects.onLaunchFailed(launchable)
        return
    }
    viewModel.onLaunched(launchable)
    effects.finishHomeInteractions()
}

/**
 * The launcher's single Pop-up open path, shared by rows, Favorites and Search results.
 * Records a Launch count only for a Folder's Pop-up that opened.
 */
internal fun openLauncherItemPopup(
    launchable: Launchable,
    anchor: PopupAnchor?,
    popupCard: PopupCardViewModel,
    viewModel: LauncherViewModel
) {
    if (!popupCard.open(owner = launchable, anchor = anchor)) {
        return
    }
    if (launchable is Folder) {
        viewModel.onFolderPopupOpened(folder = launchable)
    }
}

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
    profileSource: IProfileSource,
    onRequestQuietModeOff: (Long) -> Unit,
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
                        message = context.getString(CoreUiR.string.calendar_permission_needed),
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
            bindLauncher.launch(
                AppWidgetViews.bindIntent(context = context, request = event.request, profileSource = profileSource)
            )
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

        is WidgetEvent.ShowMessage -> coroutineScope.showSnackbar(
            snackbarHostState = snackbarHostState,
            message = event.formatArg?.let { arg -> context.getString(event.message, arg) } ?: context.getString(event.message)
        )

        is WidgetEvent.RemoveHostView -> appWidgetViewCache.remove(event.appWidgetId)

        is WidgetEvent.ReportWidgetSize -> appWidgetViewCache.updateSize(
            ids = event.appWidgetIds,
            widthDp = event.widthDp,
            heightDp = event.heightDp
        )

        is WidgetEvent.RequestQuietModeOff -> onRequestQuietModeOff(event.profileSerial)

        is WidgetEvent.ScrollToPlace -> callbacks.onScrollToPlace(event.placeId)

        WidgetEvent.ReturnToSettings -> navController.navigate(WIDGETS_SETTINGS_ROUTE)
    }
}

/** Shows [message] in the launcher's Snackbar host. */
private fun CoroutineScope.showSnackbar(snackbarHostState: SnackbarHostState, message: String) {
    launch { snackbarHostState.showSnackbar(message = message) }
}
