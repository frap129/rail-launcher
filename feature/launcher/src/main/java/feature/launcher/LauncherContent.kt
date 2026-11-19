package feature.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.activity.FullyDrawnReporter
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.search.SearchResult
import core.ui.GroupMark
import core.ui.HOME_FULLY_DRAWN_TEST_TAG
import core.ui.HOME_ICONS_DRAWN_TEST_TAG
import core.ui.IHomeIconsDrawn
import core.ui.R as CoreUiR
import core.ui.composables.FocusRevealContent
import core.ui.composables.scrollrail.RailScrubState
import core.ui.composables.scrollrail.ScrollRail
import core.ui.composables.scrollrail.railScrubInput
import core.ui.composables.scrollrail.railScrubStripInput
import core.ui.composables.scrollrail.railWidth
import core.ui.composables.scrollrail.rememberRailScrubState
import core.ui.layout.HomeEditMode
import core.ui.layout.appListStartInset
import core.ui.layout.homeEdgePadding
import core.ui.popups.IPopupEditor
import core.ui.popups.PopupEditState
import core.ui.util.consumeTouches
import core.util.attemptLaunch
import core.util.toDp
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.WidgetEditDraft
import feature.launcher.widgets.rememberWidgetAreaUi

private val doneButtonPadding: Dp = 24.dp
private val railTopPadding: Dp = 200.dp
private val railHorizontalInset: Dp = 16.dp
private const val DEFAULT_FADE_IN_MILLIS: Int = 5
private const val DEFAULT_FADE_IN_DELAY_MILLIS: Int = 5
private const val DEFAULT_FADE_OUT_MILLIS: Int = 5
private const val FOCUS_FADE_IN_MILLIS: Int = 200
private const val FOCUS_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_IN_MILLIS: Int = 200
private const val REVEAL_FADE_IN_DELAY_MILLIS: Int = 75
private const val REVEAL_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_OUT_DELAY_MILLIS: Int = 275

/** The test tag on the Rail scrub strip on [side]. */
internal fun railScrubStripTestTag(side: RailSide): String = if (side.isStart) "railScrubStripStart" else "railScrubStripEnd"

@Composable
internal fun LauncherContent(
    modifier: Modifier,
    viewModel: LauncherViewModel,
    widgetViewModel: WidgetAreaViewModel,
    railBounds: RailBounds,
    railSide: RailSide,
    showRail: Boolean,
    onItemLaunch: (Launchable) -> Unit,
    onItemOpenPopup: (Launchable, PopupAnchor) -> Unit,
    onItemAnchorChanged: (Launchable, PopupAnchor) -> Unit,
    onCopyCalculatorRow: (String) -> Unit,
    onOpenSearchRow: (SearchResult.ExternalRow) -> Unit,
    onContactRowAction: (SearchResult.ContactRow, ContactRowAction) -> Unit,
    onOpenSearchSettings: () -> Unit,
    homeIconsDrawn: IHomeIconsDrawn,
    isResizing: Boolean,
    isEditingWidgets: Boolean,
    homeEditMode: HomeEditMode
) {
    val uiState: LauncherUiState by viewModel.uiState.collectAsState()
    val folderSummaries: Map<String, String> by viewModel.folderSummaryLines.collectAsState()
    val privateSpaceSettingsIntent: IntentSender? by viewModel.privateSpaceSettingsIntent.collectAsState()
    val context: Context = LocalContext.current
    val itemActions: LauncherItemActions =
        remember(viewModel, onItemLaunch, onItemOpenPopup, onItemAnchorChanged, railBounds, railSide, context, privateSpaceSettingsIntent) {
            LauncherItemActions(
                onItemTap = { launchable, anchor ->
                    if (launchable is Folder) {
                        anchor?.let { popupAnchor -> onItemOpenPopup(launchable, popupAnchor) }
                    } else {
                        onItemLaunch(launchable)
                    }
                },
                onItemLongPress = viewModel::openItemMenu,
                onToggleFavorite = viewModel::toggleFavorite,
                onItemOpenPopup = onItemOpenPopup,
                onItemAnchorChanged = onItemAnchorChanged,
                privateSpaceActions = PrivateSpaceActions(
                    onUnlock = viewModel::requestPrivateSpaceUnlock,
                    onLock = viewModel::lockPrivateSpace,
                    onSettings = privateSpaceSettingsIntent?.let { intentSender ->
                        { context.tryStartIntentSender(intentSender = intentSender) }
                    }
                ),
                railBounds = { railBounds.current },
                railSide = railSide
            )
        }
    val searchOptionsActions: SearchOptionsActions = remember(viewModel, onOpenSearchSettings) {
        SearchOptionsActions(
            onOpenSearchOnSwipeUpChange = viewModel::setOpenSearchOnSwipeUp,
            onSearchSuggestionsChange = viewModel::setSearchSuggestions,
            onSearchWebSuggestionsChange = viewModel::setSearchWebSuggestions,
            onSearchContactsChange = viewModel::setSearchContacts,
            onSearchCalculatorRowChange = viewModel::setSearchCalculatorRow,
            onContactsPermissionResult = viewModel::onContactsPermissionResult,
            onRefreshContactsPermission = viewModel::refreshContactsPermission,
            onOpenSearchSettings = onOpenSearchSettings
        )
    }
    val home: LauncherUiState.Home? = retainedHomeState(uiState)
    val isHomeVisible: Boolean = uiState is LauncherUiState.Home
    val isEditingHome: Boolean = homeEditMode != HomeEditMode.None
    val isSwipeUpEnabled: Boolean = isHomeVisible && home?.isSwipeUpEnabled == true && !isEditingHome
    val isSwipeDownEnabled: Boolean = isHomeVisible && home?.isSwipeDownEnabled == true && !isEditingHome
    val homeAlpha: State<Float> = animateFloatAsState(
        targetValue = if (isHomeVisible) 1f else 0f,
        animationSpec = if (isHomeVisible) {
            tween(durationMillis = DEFAULT_FADE_IN_MILLIS, delayMillis = DEFAULT_FADE_IN_DELAY_MILLIS)
        } else {
            tween(durationMillis = DEFAULT_FADE_OUT_MILLIS)
        },
        label = "homeAlpha"
    )
    // Consumes touches that miss the App List and land on the hidden Home.
    val hiddenHomeModifier: Modifier = remember {
        Modifier
            .clearAndSetSemantics { hideFromAccessibility() }
            .consumeTouches()
    }
    val homePaint: Paint = remember { Paint() }
    val homeIconsDrawnReport: HomeIconsDrawnReport = remember(homeIconsDrawn) { HomeIconsDrawnReport(homeIconsDrawn = homeIconsDrawn) }

    Box(modifier.testTagWhen(isTagged = homeIconsDrawnReport.isReported, tag = HOME_ICONS_DRAWN_TEST_TAG)) {
        // Kept composed under the App List so returning to Home never rebuilds it.
        if (home != null) {
            val widgetArea: WidgetAreaUi = rememberWidgetAreaUi(
                viewModel = widgetViewModel,
                onLaunch = onItemLaunch,
                menuEnabled = !isEditingHome
            )
            val isHomeDrawable: Boolean = isHomeVisible && home.isFavoritesLoaded && widgetArea.state.isLoaded
            val isHomeFullyDrawn: Boolean = rememberHomeFullyDrawn(isHomeDrawable = isHomeDrawable)
            HomeIconsDrawnEffect(home = home, isHomeDrawable = isHomeDrawable, report = homeIconsDrawnReport)
            LauncherHome(
                modifier = Modifier
                    .fillMaxSize()
                    .testTagWhen(isTagged = isHomeFullyDrawn, tag = HOME_FULLY_DRAWN_TEST_TAG)
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
                railSide = railSide,
                itemActions = itemActions,
                folderSummaries = folderSummaries,
                onFavoriteIconSettled = homeIconsDrawnReport.settledIcons::settle,
                onFavoriteMoved = viewModel::moveFavorite,
                onEmptyAreaLongPress = widgetViewModel::openEmptyAreaMenu,
                onSwipeUp = viewModel::onHomeSwipeUp,
                isSwipeUpEnabled = isSwipeUpEnabled,
                onSwipeDown = viewModel::onHomeSwipeDown,
                isSwipeDownEnabled = isSwipeDownEnabled,
                isResizing = isResizing,
                isEditingWidgets = isEditingWidgets,
                isPopupSwipeEnabled = !isEditingHome,
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
                    is LauncherUiState.Search -> LauncherUiState.Search::class
                    LauncherUiState.Loading -> LauncherUiState.Loading
                }
            }
        ) { state ->
            when (state) {
                is LauncherUiState.Home, LauncherUiState.Loading -> {}

                is LauncherUiState.AppList.Browsing -> {
                    val rowState: AppListRowState = rememberAppListRowState(
                        isEditingFavorites = state.isEditingFavorites,
                        favorites = viewModel.favorites,
                        isPopupSwipeEnabled = !isEditingHome
                    )
                    LauncherAppList(
                        modifier = Modifier.fillMaxSize(),
                        appList = state.groups,
                        recentlyInstalled = state.recentlyInstalled,
                        lazyListState = state.lazyListState,
                        rowState = rowState,
                        itemActions = itemActions,
                        folderSummaries = folderSummaries,
                        topOffset = viewModel.listStartOffsetPx.toDp(context).dp,
                        bottomOffset = viewModel.listEndOffsetPx.toDp(context).dp,
                        railSide = railSide,
                        showRail = showRail
                    )
                }

                is LauncherUiState.AppList.Scrolling -> {
                    val rowState: AppListRowState = rememberAppListRowState(
                        isEditingFavorites = state.isEditingFavorites,
                        favorites = viewModel.favorites,
                        isPopupSwipeEnabled = false
                    )
                    LauncherItemGroup(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = appListStartInset(railSide = railSide, showRail = showRail),
                                top = viewModel.listStartOffsetPx.toDp(context).dp
                            ),
                        group = state.group,
                        rowState = rowState,
                        itemActions = itemActions,
                        folderSummaries = folderSummaries
                    )
                }

                is LauncherUiState.Search -> {
                    LauncherSearch(
                        search = state,
                        shouldAutoLaunch = viewModel::shouldAutoLaunch,
                        itemActions = itemActions,
                        contactsEvents = viewModel.events,
                        searchOptionsActions = searchOptionsActions,
                        onQueryChange = viewModel::setSearchQuery,
                        onSearchGo = viewModel::onSearchGo,
                        onCopyCalculatorRow = onCopyCalculatorRow,
                        onOpenSearchRow = onOpenSearchRow,
                        onContactRowAction = onContactRowAction
                    )
                }
            }
        }
    }
}

// Keeps the latest Home state while the App List, Search or a loading state covers Home, so
// returning to Home never rebuilds it.
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
        is LauncherUiState.AppList, is LauncherUiState.Search, LauncherUiState.Loading -> retained
    }
}

/**
 * Reports the activity fully drawn once Home can be drawn, and returns whether it has reported.
 * Home can be drawn when it is shown with its Header, Widget Area and Favorites loaded; a loaded
 * empty Home counts. The activity's fully-drawn reporter fires at most once per cold start,
 * after the first frame.
 */
@Composable
private fun rememberHomeFullyDrawn(isHomeDrawable: Boolean): Boolean {
    var isReported: Boolean by remember { mutableStateOf(false) }
    val fullyDrawnReporter: FullyDrawnReporter? = LocalFullyDrawnReporterOwner.current?.fullyDrawnReporter
    DisposableEffect(fullyDrawnReporter) {
        val onReported: () -> Unit = { isReported = true }
        fullyDrawnReporter?.addOnReportDrawnListener(onReported)
        onDispose { fullyDrawnReporter?.removeOnReportDrawnListener(onReported) }
    }
    if (isHomeDrawable) {
        ReportDrawnWhen { true }
    }
    return isReported
}

/** Applies [tag] to this modifier only while [isTagged], so the benchmark can wait for the milestone. */
private fun Modifier.testTagWhen(isTagged: Boolean, tag: String): Modifier = then(if (isTagged) Modifier.testTag(tag) else Modifier)

@Composable
internal fun BoxScope.LauncherRail(
    railItems: List<GroupMark>,
    railBounds: RailBounds,
    railSide: RailSide,
    scrubEdges: ScrubEdges,
    showRail: Boolean,
    isCardOpen: () -> Boolean,
    railHaptics: Boolean,
    onRailTouched: () -> Unit,
    onRailPreview: (Int) -> Unit,
    onRailReleased: (Int) -> Unit,
    homeEditMode: HomeEditMode = HomeEditMode.None
) {
    val railGesture: RailGesture = remember { RailGesture() }
    // Edit Favorites keeps the Rail so Apps can be found in the App List and added.
    val isEnabled: Boolean = homeEditMode == HomeEditMode.None || homeEditMode == HomeEditMode.Favorites
    // Re-arms the policy whenever ScrollRail restarts its pointer input mid-gesture.
    LaunchedEffect(railItems.size, isEnabled, railSide, scrubEdges, showRail) {
        railGesture.reset()
    }
    // The drawn Rail takes touches on its own edge; every other enabled edge, including both while
    // the Rail is hidden, gets an invisible strip that scrubs it.
    val scrubStripSides: List<RailSide> = RailSide.entries.filter { side ->
        scrubEdges.includes(side) && !(showRail && side == railSide)
    }
    // Stored bounds always describe what takes Rail touches: the drawn Rail, the Rail-side strip, or nothing.
    val takesRailTouches: Boolean = showRail || railSide in scrubStripSides
    LaunchedEffect(takesRailTouches) {
        if (!takesRailTouches) railBounds.clear()
    }
    val railScrubState: RailScrubState = rememberRailScrubState(
        items = railItems,
        onScroll = { index ->
            if (railGesture.onScroll(isCardOpen = isCardOpen(), closeCard = onRailTouched) == RailIndexAction.Act) {
                onRailPreview(index)
            }
        },
        onScrollEnded = { index ->
            if (railGesture.onScrollEnded(isCardOpen = isCardOpen(), closeCard = onRailTouched) == RailIndexAction.Act) {
                onRailReleased(index)
            }
        },
        railHaptics = railHaptics,
        enabled = isEnabled
    )
    if (showRail) {
        val drawnRailInput: Modifier = if (scrubEdges.includes(railSide)) {
            Modifier.railScrubInput(state = railScrubState)
        } else {
            Modifier
        }
        val railHorizontalOffset: Dp = if (railSide.isStart) railHorizontalInset else -railHorizontalInset
        ScrollRail(
            state = railScrubState,
            items = railItems,
            modifier = Modifier
                .align(railSide.centerAlignment())
                .padding(0.dp, railTopPadding, 0.dp, 0.dp)
                .width(railWidth)
                .offset { IntOffset(railHorizontalOffset.toPx().toInt(), 0) }
                .then(drawnRailInput)
                .publishRailBounds(railBounds = railBounds),
            railSide = railSide
        )
    }
    scrubStripSides.forEach { stripSide ->
        val hiddenRailBounds: Modifier = if (stripSide == railSide) {
            Modifier.publishRailBounds(railBounds = railBounds)
        } else {
            Modifier
        }
        RailScrubStrip(state = railScrubState, railSide = railSide, stripSide = stripSide, modifier = hiddenRailBounds)
    }
}

/** The horizontal alignment that puts content on [this] side. */
private fun RailSide.centerAlignment(): Alignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd

/** Publishes this layout's bounds into [railBounds], so row swipes stop at the Rail's touch column. */
private fun Modifier.publishRailBounds(railBounds: RailBounds): Modifier =
    onGloballyPositioned { coordinates -> railBounds.update(bounds = coordinates.boundsInRoot()) }

/** An invisible strip on [stripSide] that scrubs the Rail as if the finger were on its own edge. */
@Composable
private fun BoxScope.RailScrubStrip(state: RailScrubState, railSide: RailSide, stripSide: RailSide, modifier: Modifier) {
    Box(
        modifier = Modifier
            .align(stripSide.centerAlignment())
            .padding(0.dp, railTopPadding, 0.dp, 0.dp)
            .width(homeEdgePadding)
            .height(state.railHeight)
            .then(modifier)
            .railScrubStripInput(state = state, railSide = railSide)
            .testTag(railScrubStripTestTag(side = stripSide))
            .clearAndSetSemantics { hideFromAccessibility() }
    )
}

@Composable
internal fun BoxScope.LauncherDoneButton(viewModel: LauncherViewModel, widgetViewModel: WidgetAreaViewModel, popupEditor: IPopupEditor) {
    val isEditingFavorites: Boolean by viewModel.isEditingFavorites.collectAsState()
    val editDraft: WidgetEditDraft? by widgetViewModel.editState.collectAsState()
    val popupEditState: PopupEditState by popupEditor.editState.collectAsState()
    val isEditingWidgets: Boolean = editDraft != null
    val isEditingPopup: Boolean = popupEditState is PopupEditState.Editing
    if ((isEditingFavorites || isEditingWidgets) && !isEditingPopup) {
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
            Text(stringResource(CoreUiR.string.done))
        }
    }
}

/**
 * Activity options for opening a system screen or store page Rail reaches through one of the
 * system's IntentSenders, which only Android 16 offers.
 * [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE] is the least permissive mode
 * that still lets the visible launcher start the screen behind the system's IntentSender.
 */
internal fun privateSpaceLaunchOptions(): ActivityOptions? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
} else {
    null
}

/** Starts the system's [intentSender], returning whether the system accepted it. */
internal fun Context.tryStartIntentSender(intentSender: IntentSender): Boolean =
    attemptLaunch { startIntentSender(intentSender, null, 0, 0, 0, privateSpaceLaunchOptions()?.toBundle()) }
