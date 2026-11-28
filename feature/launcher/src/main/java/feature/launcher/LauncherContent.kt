package feature.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentSender
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
import core.data.launchables.model.Launchable
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.search.SearchResult
import core.ui.GroupMark
import core.ui.HOME_FULLY_DRAWN_TEST_TAG
import core.ui.HOME_ICONS_DRAWN_TEST_TAG
import core.ui.HomeIconsDrawnReport
import core.ui.IHomeIconsDrawn
import core.ui.R as CoreUiR
import core.ui.composables.AppListRowState
import core.ui.composables.FocusRevealContent
import core.ui.composables.LauncherItemActions
import core.ui.composables.LauncherItemGroup
import core.ui.composables.PrivateSpaceActions
import core.ui.composables.rememberAppListRowState
import core.ui.composables.scrollrail.RailBounds
import core.ui.composables.scrollrail.RailGesture
import core.ui.composables.scrollrail.RailIndexAction
import core.ui.composables.scrollrail.RailPopupCard
import core.ui.composables.scrollrail.RailScrubState
import core.ui.composables.scrollrail.RailWheelStep
import core.ui.composables.scrollrail.ScrollRail
import core.ui.composables.scrollrail.railScrubInput
import core.ui.composables.scrollrail.railScrubStripInput
import core.ui.composables.scrollrail.railWidth
import core.ui.composables.scrollrail.rememberRailScrubState
import core.ui.layout.HomeEditMode
import core.ui.layout.appListStartInset
import core.ui.layout.homeEdgePadding
import core.ui.popups.IPopupEditor
import core.ui.popups.PopupAnchor
import core.ui.popups.PopupEditState
import core.ui.search.ContactRowAction
import core.ui.search.SearchOptionsActions
import core.ui.util.consumeTouches
import core.ui.widgets.WidgetAreaUi
import core.ui.widgets.WidgetEditDraft
import core.util.toDp
import core.util.tryStartActivity
import core.util.tryStartIntentSender
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.rememberWidgetAreaUi
import timber.log.Timber

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
private const val HOME_RAIL_INDEX: Int = 0
private const val FIRST_GROUP_RAIL_INDEX: Int = 1

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
    onRowTap: (Launchable, PopupAnchor?) -> Unit,
    onOpenAgenda: () -> Unit,
    onItemOpenPopup: (Launchable, PopupAnchor?) -> Unit,
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
    val defaultLauncherPromptActions: DefaultLauncherPromptActions = remember(viewModel, context) {
        DefaultLauncherPromptActions(
            onRequestHomeRole = {
                val homeRoleRequest: Intent? = viewModel.requestHomeRole()
                if (homeRoleRequest != null) {
                    context.tryStartActivity(homeRoleRequest)
                } else {
                    Timber.w("The system offers no Home role request Intent")
                }
            },
            onDismiss = viewModel::dismissDefaultLauncherPrompt,
            onTurnOff = viewModel::turnOffDefaultLauncherPrompt
        )
    }
    val itemActions: LauncherItemActions =
        remember(viewModel, onRowTap, onItemOpenPopup, onItemAnchorChanged, railBounds, railSide, context, privateSpaceSettingsIntent) {
            LauncherItemActions(
                onItemTap = onRowTap,
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
                onOpenAgenda = onOpenAgenda,
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
                defaultLauncherPromptActions = defaultLauncherPromptActions,
                onFavoriteIconSettled = homeIconsDrawnReport.settledIcons::settle,
                onFavoriteMoved = viewModel::moveFavorite,
                onEmptyAreaLongPress = widgetViewModel::openEmptyAreaMenu,
                onSwipeUp = viewModel::onHomeSwipeUp,
                isSwipeUpEnabled = isSwipeUpEnabled,
                onSwipeDown = viewModel::onHomeSwipeDown,
                isSwipeDownEnabled = isSwipeDownEnabled,
                isResizing = isResizing,
                isEditingWidgets = isEditingWidgets,
                isRowFocusEnabled = isHomeVisible,
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
                        isRowFocusEnabled = state == uiState,
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
                        isRowFocusEnabled = state == uiState,
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

/** The Rail mark for the group this launcher state shows, or the Star outside the App List. */
internal fun LauncherUiState.findShownRailIndex(railItems: List<GroupMark>): Int {
    val firstVisibleItemIndex: Int = (this as? LauncherUiState.AppList.Browsing)
        ?.lazyListState
        ?.firstVisibleItemIndex
        ?: FIRST_GROUP_RAIL_INDEX
    // App List item 0 is the top spacer; item 1 is the first group.
    val groupIndex: Int = (firstVisibleItemIndex - FIRST_GROUP_RAIL_INDEX).coerceAtLeast(HOME_RAIL_INDEX)
    return when (this) {
        LauncherUiState.Loading, is LauncherUiState.Home, is LauncherUiState.Search -> HOME_RAIL_INDEX
        is LauncherUiState.AppList.Scrolling -> railIndex
        is LauncherUiState.AppList.Browsing -> (groupIndex + FIRST_GROUP_RAIL_INDEX).coerceAtMost(railItems.lastIndex)
    }
}

@Composable
internal fun BoxScope.LauncherRail(
    railItems: List<GroupMark>,
    uiState: () -> LauncherUiState,
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
    // Reads the open Pop-up card, if any, when a Rail gesture starts: an open card takes it first.
    val currentRailPopupCard: () -> RailPopupCard = { RailPopupCard(isOpen = isCardOpen(), close = onRailTouched) }
    val railScrubState: RailScrubState = rememberRailScrubState(
        items = railItems,
        onScroll = { index ->
            if (railGesture.onScroll(card = currentRailPopupCard()) == RailIndexAction.Act) {
                onRailPreview(index)
            }
        },
        onScrollEnded = { index ->
            if (railGesture.onScrollEnded(card = currentRailPopupCard()) == RailIndexAction.Act) {
                onRailReleased(index)
            }
        },
        onWheelScroll = { deltaY ->
            railGesture.onWheelScroll(
                deltaY = deltaY,
                wheelStep = RailWheelStep(
                    currentMarkIndex = uiState().findShownRailIndex(railItems = railItems),
                    markCount = railItems.size,
                    card = currentRailPopupCard()
                )
            ).forEach(onRailReleased)
        },
        onPointerExit = railGesture::resetWheelScroll,
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
