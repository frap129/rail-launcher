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
import core.data.launchables.model.Launchable
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.InsertPosition
import core.ui.R as CoreUiR
import core.ui.model.data.Destination
import core.ui.model.data.HOME_ADD_WIDGET_REQUEST_KEY
import core.ui.model.data.HOME_INTENT_REQUEST_KEY
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.OneShotRequest
import core.ui.model.data.WIDGETS_MEDIA_SETTINGS_ROUTE
import core.ui.model.data.WIDGETS_SETTINGS_ROUTE
import core.ui.util.consumeTouches
import core.util.openApplicationDetailsSettings
import core.widgets.IAppWidgetViewCache
import core.widgets.NotificationAccessDialog
import feature.launcher.widgets.AddWidgetFlowState
import feature.launcher.widgets.AddWidgetOrigin
import feature.launcher.widgets.AddWidgetPlacement
import feature.launcher.widgets.AppWidgetPickerSheet
import feature.launcher.widgets.AppWidgetViews
import feature.launcher.widgets.MoveWidgetAreaPanel
import feature.launcher.widgets.MoveWidgetAreaState
import feature.launcher.widgets.ResizeState
import feature.launcher.widgets.UndoAction
import feature.launcher.widgets.WidgetAreaEvent
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.WidgetEditDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
    val context: Context = LocalContext.current
    val activity: ComponentActivity? = context as? ComponentActivity
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val headerRemovedMessage: String = stringResource(R.string.header_removed)
    val headerReplacedMessage: String = stringResource(R.string.header_replaced)
    val undoLabel: String = stringResource(R.string.undo)
    val calendarPermissionMessage: String = stringResource(R.string.calendar_permission_needed)
    val openSettingsLabel: String = stringResource(CoreUiR.string.open_settings)
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
    val launchItem: (Launchable) -> Unit = { launchable ->
        launchable.launch(context)
        widgetViewModel.onHomeIntent()
        viewModel.onLaunchableLaunched()
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
    LaunchedEffect(viewModel, widgetViewModel) {
        widgetViewModel.events.collect { event ->
            when (event) {
                is WidgetAreaEvent.ShowUndo -> coroutineScope.launch {
                    val action: UndoAction = event.action
                    val result: SnackbarResult = snackbarHostState.showSnackbar(
                        message = when (action) {
                            is UndoAction.RemoveHeader -> headerRemovedMessage

                            is UndoAction.ReplaceHeader -> headerReplacedMessage

                            is UndoAction.IncludeMediaApp -> context.getString(
                                R.string.media_player_excluded,
                                action.appLabel
                            )
                        },
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        widgetViewModel.undo(action = action)
                    }
                }

                WidgetAreaEvent.RequestCalendarPermission -> permissionLauncher.launch(Manifest.permission.READ_CALENDAR)

                is WidgetAreaEvent.ShowCalendarPermissionDenied -> {
                    if (event.permanentlyDenied) {
                        showCalendarPermissionDialog = true
                    } else {
                        coroutineScope.launch {
                            val result: SnackbarResult = snackbarHostState.showSnackbar(
                                message = calendarPermissionMessage,
                                actionLabel = openSettingsLabel,
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                context.openApplicationDetailsSettings()
                            }
                        }
                    }
                }

                WidgetAreaEvent.NavigateToWidgetSettings -> navController.navigate(WIDGETS_SETTINGS_ROUTE)

                WidgetAreaEvent.NavigateToMediaPlayerSettings -> navController.navigate(WIDGETS_MEDIA_SETTINGS_ROUTE)

                WidgetAreaEvent.EditFavorites -> viewModel.editFavorites()

                WidgetAreaEvent.RequestNotificationAccess -> showNotificationAccessDialog = true

                is WidgetAreaEvent.LaunchBind -> {
                    try {
                        bindLauncher.launch(AppWidgetViews.bindIntent(context = context, request = event.request))
                    } catch (exception: ActivityNotFoundException) {
                        widgetViewModel.onBindResult(granted = false)
                    } catch (exception: SecurityException) {
                        widgetViewModel.onBindResult(granted = false)
                    }
                }

                is WidgetAreaEvent.LaunchConfigure -> {
                    val currentActivity: ComponentActivity? = activity
                    if (currentActivity == null) {
                        widgetViewModel.onConfigureResult(ok = false)
                    } else {
                        try {
                            appWidgetHostSource.startConfiguring(
                                activity = currentActivity,
                                id = event.id,
                                requestCode = AppWidgetResults.REQUEST_CODE
                            )
                        } catch (exception: ActivityNotFoundException) {
                            widgetViewModel.onConfigureResult(ok = false)
                        } catch (exception: SecurityException) {
                            widgetViewModel.onConfigureResult(ok = false)
                        }
                    }
                }

                is WidgetAreaEvent.LaunchReconfigure -> {
                    val currentActivity: ComponentActivity? = activity
                    if (currentActivity == null) {
                        widgetViewModel.onReconfigureResult(ok = false)
                    } else {
                        try {
                            appWidgetHostSource.startConfiguring(
                                activity = currentActivity,
                                id = event.id,
                                requestCode = AppWidgetResults.RECONFIGURE_REQUEST_CODE
                            )
                        } catch (exception: ActivityNotFoundException) {
                            widgetViewModel.onReconfigureResult(ok = false)
                        } catch (exception: SecurityException) {
                            widgetViewModel.onReconfigureResult(ok = false)
                        }
                    }
                }

                is WidgetAreaEvent.ShowMessage -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message =
                        event.formatArg?.let { arg -> context.getString(event.message, arg) } ?: context.getString(event.message)
                    )
                }

                is WidgetAreaEvent.RemoveHostView -> appWidgetViewCache.remove(event.appWidgetId)

                is WidgetAreaEvent.ReportWidgetSize -> appWidgetViewCache.updateSize(
                    ids = event.appWidgetIds,
                    widthDp = event.widthDp,
                    heightDp = event.heightDp
                )

                is WidgetAreaEvent.RequestQuietModeOff -> AppWidgetViews.requestQuietModeOff(
                    context = context,
                    profileSerial = event.profileSerial
                )

                is WidgetAreaEvent.ScrollToPlace -> widgetViewModel.requestScrollToPlace(placeId = event.placeId)

                WidgetAreaEvent.ReturnToSettings -> navController.navigate(WIDGETS_SETTINGS_ROUTE)
            }
        }
    }
    LaunchedEffect(homeIntentRequest) {
        homeIntentRequest.collectRequests {
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
            }
        }
    }
    val addFlowState: AddWidgetFlowState by widgetViewModel.addFlowState.collectAsState()
    val resizeState: ResizeState? by widgetViewModel.resizeState.collectAsState()
    val editDraft: WidgetEditDraft? by widgetViewModel.editState.collectAsState()
    val moveState: MoveWidgetAreaState? by widgetViewModel.moveState.collectAsState()
    val isResizing: Boolean = resizeState != null
    val isEditingWidgets: Boolean = editDraft != null
    val isMovingWidgetArea: Boolean = moveState != null
    val isEditingHome: Boolean = isResizing || isEditingWidgets || isMovingWidgetArea
    BackHandler {
        if (!widgetViewModel.onBack()) {
            viewModel.onBack()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LauncherContent(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            widgetViewModel = widgetViewModel,
            onItemLaunch = launchItem,
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets,
            isMovingWidgetArea = isMovingWidgetArea
        )
        LauncherRail(viewModel, enabled = !isEditingHome)
        LauncherDoneButton(viewModel, widgetViewModel)
        LauncherMenu(viewModel)
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
