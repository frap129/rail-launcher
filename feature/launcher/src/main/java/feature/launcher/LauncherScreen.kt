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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import core.ui.model.data.HOME_INTENT_REQUEST_KEY
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import core.util.openApplicationDetailsSettings
import core.util.toDp
import feature.widgets.AddWidgetFlowState
import feature.widgets.AddWidgetPlacement
import feature.widgets.AppWidgetPickerSheet
import feature.widgets.AppWidgetViewCache
import feature.widgets.AppWidgetViews
import feature.widgets.RESIZE_DIMMED_ALPHA
import feature.widgets.ResizeState
import feature.widgets.WidgetArea
import feature.widgets.WidgetAreaEvent
import feature.widgets.WidgetAreaViewModel
import feature.widgets.WidgetEditDraft
import feature.widgets.WidgetMenu
import feature.widgets.WidgetMenuAction
import feature.widgets.WidgetMenuState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
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

private const val WIDGETS_SETTINGS_ROUTE: String = "settings/widgets"

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
    val undoLabel: String = stringResource(R.string.undo)
    val calendarPermissionMessage: String = stringResource(CoreUiR.string.calendar_permission_needed)
    val openSettingsLabel: String = stringResource(CoreUiR.string.open_settings)
    var showCalendarPermissionDialog: Boolean by remember { mutableStateOf(false) }
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
        viewModel.onLaunchableLaunched()
    }
    DisposableEffect(lifecycleOwner, widgetViewModel) {
        val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> widgetViewModel.refreshCalendarPermission()

                Lifecycle.Event.ON_START -> appWidgetHostSource.startListening()

                Lifecycle.Event.ON_STOP -> {
                    widgetViewModel.finishResize()
                    widgetViewModel.finishEditingWidgets()
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
                    val result: SnackbarResult = snackbarHostState.showSnackbar(
                        message = headerRemovedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        widgetViewModel.undoRemoveHeader(removal = event.removal)
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
                    snackbarHostState.showSnackbar(message = context.getString(event.message))
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
                    val wasEditingWidgets: Boolean = widgetViewModel.editState.value != null
                    if (!widgetViewModel.onHomeIntent() || wasEditingWidgets) {
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
    val isResizing: Boolean = resizeState != null
    val isEditingWidgets: Boolean = editDraft != null
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
            isEditingWidgets = isEditingWidgets
        )
        LauncherRail(viewModel, enabled = !isResizing && !isEditingWidgets)
        LauncherDoneButton(viewModel, widgetViewModel)
        LauncherMenu(viewModel)
        LauncherWidgetMenu(widgetViewModel)
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
                canStack = (addFlowState as AddWidgetFlowState.ChoosingPlacement).canStack,
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
}

@Composable
private fun LauncherContent(
    modifier: Modifier,
    viewModel: LauncherViewModel,
    widgetViewModel: WidgetAreaViewModel,
    onItemLaunch: (Launchable) -> Unit,
    isResizing: Boolean,
    isEditingWidgets: Boolean
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
                itemActions = itemActions,
                onFavoriteMoved = viewModel::moveFavorite,
                onEmptyAreaLongPress = widgetViewModel::openEmptyAreaMenu,
                isResizing = isResizing,
                isEditingWidgets = isEditingWidgets,
                onResizeOutsideTap = widgetViewModel::finishResize,
                widgetContent = { WidgetArea(menuEnabled = !home.isEditingFavorites && !isResizing && !isEditingWidgets) }
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

                    WidgetMenuAction.Battery,
                    WidgetMenuAction.ClockStyle,
                    WidgetMenuAction.AddWidget,
                    WidgetMenuAction.Resize,
                    WidgetMenuAction.Reconfigure,
                    WidgetMenuAction.RemoveHeader,
                    WidgetMenuAction.RemoveFromStack,
                    WidgetMenuAction.RestoreHeader,
                    WidgetMenuAction.RemoveWidget,
                    WidgetMenuAction.EditWidgets,
                    WidgetMenuAction.WidgetSettings -> Unit

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
private fun AddPlacementSheet(canStack: Boolean, onBelow: () -> Unit, onIntoStack: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        dragHandle = { Spacer(Modifier.fillMaxWidth().height(sheetDragHandleHeight)) },
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(feature.widgets.R.string.add_widget_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(feature.widgets.R.string.add_below),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBelow)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge
            )
            if (canStack) {
                Text(
                    text = stringResource(feature.widgets.R.string.add_to_stack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onIntoStack)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
fun LauncherHome(
    modifier: Modifier = Modifier,
    home: LauncherUiState.Home,
    itemActions: LauncherItemActions,
    onFavoriteMoved: (fromIndex: Int, toIndex: Int) -> Unit,
    onEmptyAreaLongPress: () -> Unit = {},
    isResizing: Boolean = false,
    isEditingWidgets: Boolean = false,
    onResizeOutsideTap: () -> Unit = {},
    widgetContent: @Composable () -> Unit = {}
) {
    val reorderableListState = rememberReorderableLazyListState(home.lazyListState) { from, to ->
        onFavoriteMoved(from.index, to.index)
    }
    val hapticFeedback = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        widgetContent()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(home.isEditingFavorites, isResizing, isEditingWidgets) {
                    if (!home.isEditingFavorites && !isResizing && !isEditingWidgets) {
                        detectTapGestures(
                            onLongPress = {
                                hapticFeedback.performLongPressHaptic()
                                onEmptyAreaLongPress()
                            }
                        )
                    }
                }
                .then(
                    if (isResizing) {
                        Modifier.consumeTouches(onGestureEnd = onResizeOutsideTap)
                    } else {
                        Modifier
                    }
                )
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(if (isResizing || isEditingWidgets) RESIZE_DIMMED_ALPHA else 1f)
                .then(
                    if (isResizing) {
                        Modifier.consumeTouches(onGestureEnd = onResizeOutsideTap)
                    } else if (isEditingWidgets) {
                        Modifier.consumeTouches()
                    } else {
                        Modifier
                    }
                )
                .testTag("launcherHome"),
            state = home.lazyListState,
            verticalArrangement = Arrangement.Bottom,
            contentPadding = PaddingValues(start = homeEdgePadding, end = homeEdgePadding, bottom = homeEdgePadding)
        ) {
            if (home.favorites.isEmpty()) {
                item {
                    OutlinedText(
                        text = stringResource(R.string.favorites_empty_hint),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                items(home.favorites, key = { favorite -> favorite.key }) { favorite ->
                    ReorderableItem(reorderableListState, key = favorite.key, enabled = home.isEditingFavorites) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
    val hapticFeedback = LocalHapticFeedback.current
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
