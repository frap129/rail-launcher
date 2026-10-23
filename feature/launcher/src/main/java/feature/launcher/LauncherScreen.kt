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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.regular.Star
import compose.icons.fontawesomeicons.solid.GripVertical
import compose.icons.fontawesomeicons.solid.InfoCircle
import compose.icons.fontawesomeicons.solid.Star
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.widgets.IAppWidgetHostSource
import core.ui.R as CoreUiR
import core.ui.composables.DragHandle
import core.ui.composables.FocusRevealContent
import core.ui.composables.OutlinedText
import core.ui.composables.scrollrail.ScrollRail
import core.ui.layout.dragHandleEndPadding
import core.ui.layout.homeEdgePadding
import core.ui.model.data.Destination
import core.ui.model.data.FAVORITES_EMPTY_HINT_KEY
import core.ui.model.data.FLEXIBLE_SPACER_KEY
import core.ui.model.data.HOME_INTENT_REQUEST_KEY
import core.ui.model.data.WIDGETS_MEDIA_SETTINGS_ROUTE
import core.ui.model.data.WIDGETS_SETTINGS_ROUTE
import core.ui.model.data.favoriteKey
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import core.util.openApplicationDetailsSettings
import core.util.toDp
import core.widgets.AppWidgetViewCache
import core.widgets.NotificationAccessDialog
import feature.launcher.widgets.AddWidgetFlowState
import feature.launcher.widgets.AddWidgetPlacement
import feature.launcher.widgets.AppWidgetPickerSheet
import feature.launcher.widgets.AppWidgetViews
import feature.launcher.widgets.CappedWidgetArea
import feature.launcher.widgets.EditMergeState
import feature.launcher.widgets.MoveWidgetAreaPanel
import feature.launcher.widgets.MoveWidgetAreaState
import feature.launcher.widgets.RESIZE_DIMMED_ALPHA
import feature.launcher.widgets.ResizeState
import feature.launcher.widgets.UndoAction
import feature.launcher.widgets.WIDGET_AREA_OFFSET_KEY
import feature.launcher.widgets.WidgetAreaEvent
import feature.launcher.widgets.WidgetAreaLayout
import feature.launcher.widgets.WidgetAreaResizeAutoScroll
import feature.launcher.widgets.WidgetAreaTopOffsetSpacer
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.WidgetEditDraft
import feature.launcher.widgets.WidgetMenu
import feature.launcher.widgets.WidgetMenuAction
import feature.launcher.widgets.WidgetMenuState
import feature.launcher.widgets.rememberWidgetAreaUi
import feature.launcher.widgets.widgetAreaItems
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

val launcherDestination = Destination(
    route = "launcher",
    content = { navController, backStackEntry ->
        LauncherScreen(navController, backStackEntry)
    },
    isWallpaperBacked = true
)

private val doneButtonPadding = 24.dp
private val favoriteToggleSize = 48.dp
private val favoriteStarSize = 28.dp
private val favoriteStarOutlineSize = 36.dp
private val contentHorizontalPadding: Dp = 32.dp
private val railTopPadding: Dp = 200.dp
private val railWidth: Dp = 64.dp
private val railHorizontalOffset: Dp = (-16).dp
private val sheetDragHandleHeight: Dp = 10.dp
private val snackbarBottomPadding: Dp = 80.dp

private const val DEFAULT_FADE_IN_MILLIS: Int = 5
private const val DEFAULT_FADE_IN_DELAY_MILLIS: Int = 5
private const val DEFAULT_FADE_OUT_MILLIS: Int = 5
private const val FOCUS_FADE_IN_MILLIS: Int = 200
private const val FOCUS_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_IN_MILLIS: Int = 200
private const val REVEAL_FADE_IN_DELAY_MILLIS: Int = 75
private const val REVEAL_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_OUT_DELAY_MILLIS: Int = 275

@Immutable
data class LauncherItemActions(
    val onItemLaunch: (Launchable) -> Unit,
    val onItemLongPress: (Launchable) -> Unit,
    val onToggleFavorite: (Launchable) -> Unit
)

@Immutable
data class LauncherRowState(val isEditingFavorites: Boolean, val favoriteKeys: Set<String>)

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
    val coroutineScope = rememberCoroutineScope()
    val headerRemovedMessage: String = stringResource(R.string.header_removed)
    val headerReplacedMessage: String = stringResource(R.string.header_replaced)
    val undoLabel: String = stringResource(R.string.undo)
    val calendarPermissionMessage: String = stringResource(CoreUiR.string.calendar_permission_needed)
    val openSettingsLabel: String = stringResource(CoreUiR.string.open_settings)
    var showCalendarPermissionDialog: Boolean by remember { mutableStateOf(false) }
    var showNotificationAccessDialog: Boolean by remember { mutableStateOf(false) }
    val appWidgetResults: AppWidgetResults = koinInject()
    val appWidgetHostSource: IAppWidgetHostSource = koinInject()
    val appWidgetViewCache: AppWidgetViewCache = koinInject()
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
    LaunchedEffect(widgetViewModel) {
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

                WidgetAreaEvent.ReturnToSettings -> navController.navigate(WIDGETS_SETTINGS_ROUTE)
            }
        }
    }
    LaunchedEffect(backStackEntry) {
        backStackEntry.savedStateHandle
            .getStateFlow(HOME_INTENT_REQUEST_KEY, false)
            .collect { requested ->
                if (requested) {
                    backStackEntry.savedStateHandle[HOME_INTENT_REQUEST_KEY] = false
                    val wasEditingHome: Boolean =
                        widgetViewModel.editState.value != null || widgetViewModel.moveState.value != null
                    if (!widgetViewModel.onHomeIntent() || wasEditingHome) {
                        viewModel.onHomeIntent()
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

@Composable
private fun LauncherContent(
    modifier: Modifier,
    viewModel: LauncherViewModel,
    widgetViewModel: WidgetAreaViewModel,
    onItemLaunch: (Launchable) -> Unit,
    isResizing: Boolean,
    isEditingWidgets: Boolean,
    isMovingWidgetArea: Boolean
) {
    val uiState: LauncherUiState by viewModel.uiState.collectAsState()
    val context: Context = LocalContext.current
    val itemActions: LauncherItemActions = remember(viewModel, onItemLaunch) {
        LauncherItemActions(
            onItemLaunch = onItemLaunch,
            onItemLongPress = viewModel::openItemMenu,
            onToggleFavorite = viewModel::toggleFavorite
        )
    }
    val home: LauncherUiState.Home? = retainedHomeState(uiState)
    val isHomeVisible: Boolean = uiState is LauncherUiState.Home
    val homeAlpha: State<Float> = animateFloatAsState(
        targetValue = if (isHomeVisible) 1f else 0f,
        animationSpec = if (isHomeVisible) {
            tween(durationMillis = DEFAULT_FADE_IN_MILLIS, delayMillis = DEFAULT_FADE_IN_DELAY_MILLIS)
        } else {
            tween(durationMillis = DEFAULT_FADE_OUT_MILLIS)
        },
        label = "homeAlpha"
    )
    val hiddenHomeModifier: Modifier = remember {
        Modifier
            .clearAndSetSemantics { hideFromAccessibility() }
            .consumePointerInput()
    }
    val homePaint: Paint = remember { Paint() }
    Box(modifier) {
        // Kept composed under the App List so returning to Home never rebuilds it.
        if (home != null) {
            val isEditingHome: Boolean = home.isEditingFavorites || isResizing || isEditingWidgets || isMovingWidgetArea
            val widgetArea: WidgetAreaUi = rememberWidgetAreaUi(
                viewModel = widgetViewModel,
                menuEnabled = !isEditingHome
            )
            LauncherHome(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val alpha: Float = homeAlpha.value
                        when {
                            alpha <= 0f -> Unit

                            alpha >= 1f -> drawContent()

                            else -> {
                                homePaint.alpha = alpha
                                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), homePaint)
                                drawContent()
                                drawContext.canvas.restore()
                            }
                        }
                    }
                    .then(if (isHomeVisible) Modifier else hiddenHomeModifier),
                home = home,
                widgetArea = widgetArea,
                itemActions = itemActions,
                onFavoriteMoved = viewModel::moveFavorite,
                onEmptyAreaLongPress = widgetViewModel::openEmptyAreaMenu,
                isResizing = isResizing,
                isEditingWidgets = isEditingWidgets,
                onResizeOutsideTap = widgetViewModel::finishResize
            )
        }
        FocusRevealContent(
            modifier = Modifier.fillMaxSize(),
            targetState = uiState,
            focusState = LauncherUiState.AppList.Scrolling::class,
            revealState = LauncherUiState.AppList.Browsing::class,
            defaultSpec = fadeIn(animationSpec = tween(durationMillis = DEFAULT_FADE_IN_MILLIS, delayMillis = DEFAULT_FADE_IN_DELAY_MILLIS))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = DEFAULT_FADE_OUT_MILLIS))),
            focusSpec = fadeIn(animationSpec = tween(durationMillis = FOCUS_FADE_IN_MILLIS))
                .togetherWith(fadeOut(animationSpec = tween(durationMillis = FOCUS_FADE_OUT_MILLIS))),
            revealSpec = fadeIn(animationSpec = tween(durationMillis = REVEAL_FADE_IN_MILLIS, delayMillis = REVEAL_FADE_IN_DELAY_MILLIS))
                .togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = REVEAL_FADE_OUT_MILLIS, delayMillis = REVEAL_FADE_OUT_DELAY_MILLIS))
                ),
            contentKey = { state ->
                when (state) {
                    is LauncherUiState.AppList.Browsing -> LauncherUiState.AppList.Browsing::class
                    is LauncherUiState.AppList.Scrolling -> state.group
                    is LauncherUiState.Home -> LauncherUiState.Home::class
                    LauncherUiState.Loading -> LauncherUiState.Loading
                }
            }
        ) { state ->
            when (state) {
                is LauncherUiState.Home, LauncherUiState.Loading -> {}

                is LauncherUiState.AppList.Browsing -> {
                    val rowState: LauncherRowState = rememberRowState(state.isEditingFavorites, viewModel.favorites)
                    LauncherList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = contentHorizontalPadding),
                        launcherList = state.groups,
                        lazyListState = state.lazyListState,
                        rowState = rowState,
                        itemActions = itemActions,
                        topOffset = viewModel.listStartOffsetPx.toDp(context).dp,
                        bottomOffset = viewModel.listEndOffsetPx.toDp(context).dp
                    )
                }

                is LauncherUiState.AppList.Scrolling -> {
                    val rowState: LauncherRowState = rememberRowState(state.isEditingFavorites, viewModel.favorites)
                    LauncherItemGroup(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = contentHorizontalPadding, top = viewModel.listStartOffsetPx.toDp(context).dp),
                        group = state.group,
                        rowState = rowState,
                        itemActions = itemActions
                    )
                }
            }
        }
    }
}

// Keeps the latest Home state while the App List covers Home.
@Composable
private fun retainedHomeState(uiState: LauncherUiState): LauncherUiState.Home? {
    var retained: LauncherUiState.Home? by remember { mutableStateOf<LauncherUiState.Home?>(null) }
    SideEffect {
        if (uiState is LauncherUiState.Home) {
            retained = uiState
        }
    }
    return when (uiState) {
        is LauncherUiState.Home -> uiState
        is LauncherUiState.AppList -> retained
        LauncherUiState.Loading -> null
    }
}

// Consumes touches that miss the App List and land on the hidden Home.
private fun Modifier.consumePointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change -> change.consume() }
        }
    }
}

// App List rows only read Favorites while Edit Favorites shows stars.
@Composable
private fun rememberRowState(isEditingFavorites: Boolean, favorites: StateFlow<List<Launchable>>): LauncherRowState {
    if (!isEditingFavorites) {
        return LauncherRowState(isEditingFavorites = false, favoriteKeys = emptySet())
    }
    val favoriteList: List<Launchable> by favorites.collectAsState()
    val favoriteKeys: Set<String> = remember(favoriteList) { favoriteList.mapTo(HashSet()) { favorite -> favorite.key } }
    return LauncherRowState(isEditingFavorites = true, favoriteKeys = favoriteKeys)
}

@Composable
private fun BoxScope.LauncherRail(viewModel: LauncherViewModel, enabled: Boolean = true) {
    val railLabels: List<String> by viewModel.railLabels.collectAsState()
    ScrollRail(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(0.dp, railTopPadding, 0.dp, 0.dp)
            .width(railWidth)
            .offset { IntOffset(railHorizontalOffset.toPx().toInt(), 0) },
        items = railLabels,
        onScroll = viewModel::onRailPreview,
        onScrollEnded = viewModel::onRailReleased,
        enabled = enabled
    )
}

@Composable
private fun BoxScope.LauncherDoneButton(viewModel: LauncherViewModel, widgetViewModel: WidgetAreaViewModel) {
    val isEditingFavorites: Boolean by viewModel.isEditingFavorites.collectAsState()
    val editDraft: WidgetEditDraft? by widgetViewModel.editState.collectAsState()
    val isEditingWidgets: Boolean = editDraft != null
    if (isEditingFavorites || isEditingWidgets) {
        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(doneButtonPadding),
            onClick = {
                if (isEditingWidgets) {
                    widgetViewModel.finishEditingWidgets()
                } else {
                    viewModel.finishEditingFavorites()
                }
            }
        ) {
            Text(stringResource(R.string.launcher_done))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherMenu(viewModel: LauncherViewModel) {
    val menuState: LauncherMenuState by viewModel.menuState.collectAsState()
    if (menuState is LauncherMenuState.Closed) {
        return
    }
    ModalBottomSheet(
        dragHandle = { Spacer(Modifier.fillMaxWidth().height(sheetDragHandleHeight)) },
        onDismissRequest = { viewModel.closeItemMenu() }
    ) {
        when (val currentMenuState: LauncherMenuState = menuState) {
            is LauncherMenuState.Menu -> LauncherItemMenu(item = currentMenuState.item, viewModel = viewModel)
            is LauncherMenuState.Rename -> LauncherItemEditName(item = currentMenuState.item, viewModel = viewModel)
            LauncherMenuState.Closed -> {} // this state is never reached
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherWidgetMenu(viewModel: WidgetAreaViewModel) {
    val menuState: WidgetMenuState by viewModel.menuState.collectAsState()
    val state: WidgetMenuState = menuState
    if (state is WidgetMenuState.Closed) {
        return
    }
    ModalBottomSheet(
        dragHandle = { Spacer(Modifier.fillMaxWidth().height(sheetDragHandleHeight)) },
        onDismissRequest = viewModel::closeMenu
    ) {
        WidgetMenu(
            state = state,
            onToggle = { action, checked ->
                when (action) {
                    WidgetMenuAction.Clock -> viewModel.setClockVisible(checked)

                    WidgetMenuAction.Date -> viewModel.setDateVisible(checked)

                    WidgetMenuAction.ExtendUnderRail -> (state as? WidgetMenuState.AppWidgetMenu)?.let { menu ->
                        viewModel.setExtendsUnderRail(widgetId = menu.widgetId, extendsUnderRail = checked)
                    }

                    WidgetMenuAction.Battery,
                    WidgetMenuAction.ClockStyle,
                    WidgetMenuAction.AddWidget,
                    WidgetMenuAction.MoveWidgetArea,
                    WidgetMenuAction.Resize,
                    WidgetMenuAction.Reconfigure,
                    WidgetMenuAction.ReplaceHeader,
                    WidgetMenuAction.RemoveHeader,
                    WidgetMenuAction.RemoveFromStack,
                    WidgetMenuAction.RestoreHeader,
                    WidgetMenuAction.RemoveWidget,
                    WidgetMenuAction.EditWidgets,
                    WidgetMenuAction.WidgetSettings,
                    WidgetMenuAction.OpenMediaPlayer,
                    WidgetMenuAction.ExcludeMediaApp,
                    WidgetMenuAction.MediaPlayerSettings -> Unit

                    WidgetMenuAction.MediaPlayer -> viewModel.setMediaPlayerEnabled(enabled = checked)

                    WidgetMenuAction.UpcomingEvents -> viewModel.setUpcomingEventsEnabled(checked)
                }
            },
            onChoiceClick = viewModel::toggleChoice,
            onChoiceSelected = viewModel::selectChoice,
            onAction = viewModel::onMenuAction,
            onClockStyleSelected = viewModel::selectClockStyle
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlacementSheet(canAddToStack: Boolean, onBelow: () -> Unit, onIntoStack: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        dragHandle = { Spacer(Modifier.fillMaxWidth().height(sheetDragHandleHeight)) },
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.add_widget_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            PlacementChoice(
                label = stringResource(R.string.add_below),
                supportingText = stringResource(R.string.add_below_supporting_text),
                onClick = onBelow
            )
            if (canAddToStack) {
                PlacementChoice(
                    label = stringResource(R.string.add_next_to),
                    supportingText = stringResource(R.string.add_next_to_supporting_text),
                    onClick = onIntoStack
                )
            }
        }
    }
}

@Composable
private fun PlacementChoice(label: String, supportingText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
        Text(text = supportingText, style = MaterialTheme.typography.bodySmall)
    }
}

private data class HomeInteraction(
    val menuEnabled: Boolean,
    val isResizing: Boolean,
    val isEditingWidgets: Boolean,
    val onResizeOutsideTap: () -> Unit
)

@Composable
fun LauncherHome(
    modifier: Modifier = Modifier,
    home: LauncherUiState.Home,
    widgetArea: WidgetAreaUi,
    itemActions: LauncherItemActions,
    onFavoriteMoved: (fromIndex: Int, toIndex: Int) -> Unit,
    onEmptyAreaLongPress: () -> Unit = {},
    isResizing: Boolean = false,
    isEditingWidgets: Boolean = false,
    onResizeOutsideTap: () -> Unit = {}
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val layout: WidgetAreaLayout = widgetArea.state.layout
    val layoutMode: Boolean = layout is WidgetAreaLayout.Capped
    val menuEnabled: Boolean = widgetArea.actions.menuEnabled
    val interaction: HomeInteraction = HomeInteraction(
        menuEnabled = menuEnabled,
        isResizing = isResizing,
        isEditingWidgets = isEditingWidgets,
        onResizeOutsideTap = onResizeOutsideTap
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        val homeHeightDp: Int = maxHeight.value.roundToInt()
        LaunchedEffect(homeHeightDp) {
            if (homeHeightDp > 0) {
                widgetArea.actions.onHomeHeight(homeHeightDp)
            }
        }
        LaunchedEffect(layoutMode) {
            home.lazyListState.scrollToItem(0)
        }
        when (layout) {
            is WidgetAreaLayout.Capped -> {
                val cappedHeight: Dp = (maxHeight * layout.fraction).coerceAtLeast(0.dp)
                Column(modifier = Modifier.fillMaxSize()) {
                    CappedWidgetArea(
                        ui = widgetArea,
                        maxHeight = cappedHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FavoritesBottomArea(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        home = home,
                        itemActions = itemActions,
                        onFavoriteMoved = onFavoriteMoved,
                        onEmptyAreaLongPress = onEmptyAreaLongPress,
                        interaction = interaction
                    )
                }
            }

            WidgetAreaLayout.Uncapped -> {
                val placeIds: List<Long> = remember(widgetArea.state.places) {
                    widgetArea.state.places.map { place -> place.id }
                }
                val favoriteKeys: List<String> = remember(home.favorites) { home.favorites.map { favorite -> favorite.key } }
                val reorderState: ReorderableLazyListState = rememberHomeReorderState(
                    listState = home.lazyListState,
                    favoriteKeys = favoriteKeys,
                    onMoveFavorite = onFavoriteMoved,
                    placeIds = placeIds,
                    onMovePlace = widgetArea.actions.onMovePlace
                )
                val mergeState: EditMergeState = remember { EditMergeState() }
                val density: Density = LocalDensity.current
                val spacerHeightPx: Int by remember(home.lazyListState) {
                    derivedStateOf {
                        val layoutInfo: LazyListLayoutInfo = home.lazyListState.layoutInfo
                        val spacerVisible: Boolean = layoutInfo.visibleItemsInfo.any { item -> item.key == FLEXIBLE_SPACER_KEY }
                        if (!spacerVisible || layoutInfo.visibleItemsInfo.size != layoutInfo.totalItemsCount) {
                            0
                        } else {
                            val viewport: Int = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset -
                                layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding
                            flexibleSpacerHeight(
                                viewport = viewport,
                                itemSizes = layoutInfo.visibleItemsInfo
                                    .filter { item -> item.key != FLEXIBLE_SPACER_KEY }
                                    .map { item -> item.size }
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("launcherHome"),
                    state = home.lazyListState,
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(
                        start = homeEdgePadding,
                        top = homeEdgePadding,
                        end = homeEdgePadding,
                        bottom = homeEdgePadding
                    )
                ) {
                    item(key = WIDGET_AREA_OFFSET_KEY) {
                        WidgetAreaTopOffsetSpacer(ui = widgetArea)
                    }
                    widgetAreaItems(
                        ui = widgetArea,
                        reorderableState = reorderState,
                        mergeState = mergeState
                    )
                    item(key = FLEXIBLE_SPACER_KEY) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(density) { spacerHeightPx.toDp() })
                                .pointerInput(menuEnabled, interaction.isResizing) {
                                    if (interaction.isResizing) {
                                        detectTapGestures(onTap = { interaction.onResizeOutsideTap() })
                                    } else if (menuEnabled) {
                                        detectTapGestures(
                                            onLongPress = {
                                                hapticFeedback.performLongPressHaptic()
                                                onEmptyAreaLongPress()
                                            }
                                        )
                                    }
                                }
                        )
                    }
                    favoriteItems(
                        home = home,
                        itemActions = itemActions,
                        reorderableState = reorderState,
                        blockInteraction = interaction.isResizing || interaction.isEditingWidgets,
                        onBlockedGestureEnd = if (interaction.isResizing) interaction.onResizeOutsideTap else null
                    )
                }
                WidgetAreaResizeAutoScroll(ui = widgetArea, listState = home.lazyListState)
            }
        }
    }
}

@Composable
private fun FavoritesBottomArea(
    modifier: Modifier,
    home: LauncherUiState.Home,
    itemActions: LauncherItemActions,
    onFavoriteMoved: (fromIndex: Int, toIndex: Int) -> Unit,
    onEmptyAreaLongPress: () -> Unit,
    interaction: HomeInteraction
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val favoriteKeys: List<String> = remember(home.favorites) { home.favorites.map { favorite -> favorite.key } }
    val reorderState: ReorderableLazyListState = rememberHomeReorderState(
        listState = home.lazyListState,
        favoriteKeys = favoriteKeys,
        onMoveFavorite = onFavoriteMoved
    )
    Box(
        modifier = modifier
            .pointerInput(interaction.menuEnabled) {
                if (interaction.menuEnabled) {
                    detectTapGestures(
                        onLongPress = {
                            hapticFeedback.performLongPressHaptic()
                            onEmptyAreaLongPress()
                        }
                    )
                }
            }
            .then(
                if (interaction.isResizing) {
                    Modifier.consumeTouches(onGestureEnd = interaction.onResizeOutsideTap)
                } else if (interaction.isEditingWidgets) {
                    Modifier.consumeTouches()
                } else {
                    Modifier
                }
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.BottomCenter)
                .alpha(if (interaction.isResizing || interaction.isEditingWidgets) RESIZE_DIMMED_ALPHA else 1f)
                .testTag("launcherHome"),
            state = home.lazyListState,
            verticalArrangement = Arrangement.Bottom,
            contentPadding = PaddingValues(start = homeEdgePadding, end = homeEdgePadding, bottom = homeEdgePadding)
        ) {
            favoriteItems(home = home, itemActions = itemActions, reorderableState = reorderState)
        }
    }
}

@Composable
private fun rememberHomeReorderState(
    listState: LazyListState,
    favoriteKeys: List<String>,
    onMoveFavorite: (Int, Int) -> Unit,
    placeIds: List<Long> = emptyList(),
    onMovePlace: (Int, Int) -> Unit = { _, _ -> }
): ReorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
    when (val reorder = resolveHomeReorder(from.key, to.key, placeIds, favoriteKeys)) {
        is HomeReorder.Place -> onMovePlace(reorder.fromIndex, reorder.toIndex)
        is HomeReorder.Favorite -> onMoveFavorite(reorder.fromIndex, reorder.toIndex)
        null -> Unit
    }
}

private fun LazyListScope.favoriteItems(
    home: LauncherUiState.Home,
    itemActions: LauncherItemActions,
    reorderableState: ReorderableLazyListState,
    blockInteraction: Boolean = false,
    onBlockedGestureEnd: (() -> Unit)? = null
) {
    if (home.favorites.isEmpty()) {
        item(key = FAVORITES_EMPTY_HINT_KEY) {
            OutlinedText(
                text = stringResource(R.string.favorites_empty_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        items(home.favorites, key = { favorite -> favoriteKey(favorite.key) }) { favorite ->
            ReorderableItem(
                state = reorderableState,
                key = favoriteKey(favorite.key),
                enabled = home.isEditingFavorites
            ) { _ ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (blockInteraction) {
                        Modifier.consumeTouches(onGestureEnd = onBlockedGestureEnd)
                    } else {
                        Modifier
                    }
                ) {
                    LauncherItem(
                        item = favorite,
                        isEditingFavorites = home.isEditingFavorites,
                        isFavorite = true,
                        itemActions = itemActions,
                        modifier = Modifier.weight(1f)
                    )
                    if (home.isEditingFavorites) {
                        DragHandle(
                            modifier = Modifier
                                .padding(end = dragHandleEndPadding)
                                .draggableHandle(),
                            contentDescription = stringResource(R.string.reorder_favorite, favorite.name)
                        )
                    }
                }
            }
        }
    }
}

// A name starting with a Rail label (e.g. "⌂") forms an Initial group that
// shares the Builtin label, so the App List key includes the group kind.
internal fun LaunchableGroup.Listed.launcherListKey(): String = when (this) {
    is LaunchableGroup.Builtin -> "builtin:$label"
    is LaunchableGroup.InitialGroup -> "initial:$label"
}

@Composable
fun LauncherList(
    modifier: Modifier = Modifier,
    launcherList: List<LaunchableGroup.Listed>,
    lazyListState: LazyListState,
    rowState: LauncherRowState,
    itemActions: LauncherItemActions,
    topOffset: Dp,
    bottomOffset: Dp
) {
    val defaultBuffer = 100.dp
    var animated by remember { mutableStateOf(false) }
    val topSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else topOffset,
        animationSpec = tween(durationMillis = 100, delayMillis = 275)
    )
    val bottomSpacerHeightAnimator by animateDpAsState(
        targetValue = if (animated) defaultBuffer else bottomOffset,
        animationSpec = tween(durationMillis = 400, delayMillis = 275)
    )
    LazyColumn(
        modifier = modifier.testTag("launcherList"),
        state = lazyListState,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Spacer(Modifier.height(topSpacerHeightAnimator))
        }
        items(launcherList, key = { itemGroup -> itemGroup.launcherListKey() }) { itemGroup ->
            LauncherItemGroup(
                group = itemGroup,
                rowState = rowState,
                itemActions = itemActions
            )
        }
        item {
            Spacer(Modifier.height(bottomSpacerHeightAnimator))
        }
    }
    LaunchedEffect(Unit) {
        animated = true
    }
}

@Composable
fun LauncherItemGroup(
    modifier: Modifier = Modifier,
    group: LaunchableGroup.Listed,
    rowState: LauncherRowState,
    itemActions: LauncherItemActions
) {
    Column(modifier = modifier) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
            OutlinedText(
                text = group.label,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 24.dp, 0.dp, 8.dp)
            )
        }
        group.items.forEach { item ->
            LauncherItem(
                item = item,
                isEditingFavorites = rowState.isEditingFavorites,
                isFavorite = item.key in rowState.favoriteKeys,
                itemActions = itemActions
            )
        }
    }
}

@Composable
fun LauncherItem(
    item: Launchable,
    isEditingFavorites: Boolean,
    isFavorite: Boolean,
    itemActions: LauncherItemActions,
    modifier: Modifier = Modifier
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val currentItem: Launchable by rememberUpdatedState(item)
    val currentItemActions: LauncherItemActions by rememberUpdatedState(itemActions)
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(isEditingFavorites) {
                    if (!isEditingFavorites) {
                        detectTapGestures(
                            onTap = { currentItemActions.onItemLaunch(currentItem) },
                            onLongPress = {
                                currentItemActions.onItemLongPress(currentItem)
                                hapticFeedback.performLongPressHaptic()
                            }
                        )
                    }
                }
        ) {
            if (isEditingFavorites) {
                FavoriteToggleStar(
                    isFavorite = isFavorite,
                    onClick = { currentItemActions.onToggleFavorite(currentItem) }
                )
            }
            AsyncImage(
                modifier = Modifier.size(64.dp).padding(10.dp),
                model = item.icon,
                contentDescription = item.name
            )
            OutlinedText(
                text = item.name
            )
        }
    }
}

@Composable
private fun FavoriteToggleStar(isFavorite: Boolean, onClick: () -> Unit) {
    val label: String = stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)
    val imageVector: ImageVector = if (isFavorite) FontAwesomeIcons.Solid.Star else FontAwesomeIcons.Regular.Star
    Box(
        modifier = Modifier
            .size(favoriteToggleSize)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.size(favoriteStarOutlineSize),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentDescription = null
        )
        Image(
            modifier = Modifier.size(favoriteStarSize),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            contentDescription = null
        )
    }
}

@Composable
private fun LauncherMenuAction(imageVector: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp, 10.dp)
    ) {
        Image(
            modifier = Modifier.size(56.dp).padding(10.dp),
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            contentDescription = label
        )
        Text(label)
    }
}

@Composable
fun LauncherItemMenu(item: Launchable, viewModel: LauncherViewModel = koinViewModel()) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, 10.dp)
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(64.dp)
                    .padding(10.dp),
                model = item.icon,
                contentDescription = item.name
            )

            Text(
                text = item.name,
                fontSize = 28.sp,
                modifier = Modifier.clickable {
                    viewModel.openItemRename(item)
                }
            )
        }
    }

    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
        LauncherMenuAction(
            imageVector = FontAwesomeIcons.Solid.GripVertical,
            label = stringResource(R.string.edit_favorites),
            onClick = { viewModel.editFavorites() }
        )
        if (item is App) {
            LauncherMenuAction(
                imageVector = FontAwesomeIcons.Solid.InfoCircle,
                label = stringResource(R.string.app_info),
                onClick = { item.openAppInfo(context) }
            )
        }
    }
}

@Composable
fun LauncherItemEditName(item: Launchable, viewModel: LauncherViewModel = koinViewModel()) {
    var nameEdit by remember { mutableStateOf(item.name) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineMedium) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .padding(20.dp, 10.dp)
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(10.dp),
                    model = item.icon,
                    contentDescription = item.name
                )
                Text(stringResource(R.string.rename_item, item.name))
            }
        }

        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
            OutlinedTextField(
                modifier = Modifier
                    .padding(48.dp, 10.dp)
                    .fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                value = nameEdit,
                onValueChange = { nameEdit = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = { nameEdit = item.defaultName }
                ) {
                    Text(stringResource(R.string.launcher_reset))
                }

                TextButton(
                    onClick = { viewModel.setItemName(item, nameEdit) }
                ) {
                    Text(stringResource(R.string.launcher_done))
                }
            }
        }
    }
}
