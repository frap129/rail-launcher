package feature.launcher

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.popups.PopupAnchor
import core.popups.PopupEditState
import core.popups.PopupViewModel
import core.ui.R as CoreUiR
import core.ui.composables.FocusRevealContent
import core.ui.composables.scrollrail.ScrollRail
import core.ui.util.consumeTouches
import core.util.toDp
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.WidgetAreaViewModel
import feature.launcher.widgets.WidgetEditDraft
import feature.launcher.widgets.rememberWidgetAreaUi

private val doneButtonPadding: Dp = 24.dp
private val contentHorizontalPadding: Dp = 32.dp
private val railTopPadding: Dp = 200.dp
private val railWidth: Dp = 64.dp
private val railHorizontalOffset: Dp = (-16).dp
private const val DEFAULT_FADE_IN_MILLIS: Int = 5
private const val DEFAULT_FADE_IN_DELAY_MILLIS: Int = 5
private const val DEFAULT_FADE_OUT_MILLIS: Int = 5
private const val FOCUS_FADE_IN_MILLIS: Int = 200
private const val FOCUS_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_IN_MILLIS: Int = 200
private const val REVEAL_FADE_IN_DELAY_MILLIS: Int = 75
private const val REVEAL_FADE_OUT_MILLIS: Int = 1
private const val REVEAL_FADE_OUT_DELAY_MILLIS: Int = 275

@Composable
internal fun LauncherContent(
    modifier: Modifier,
    viewModel: LauncherViewModel,
    widgetViewModel: WidgetAreaViewModel,
    onItemLaunch: (Launchable) -> Unit,
    onItemOpenPopup: (Launchable, PopupAnchor) -> Unit,
    onItemAnchorChanged: (Launchable, PopupAnchor) -> Unit,
    isResizing: Boolean,
    isEditingWidgets: Boolean,
    isMovingWidgetArea: Boolean
) {
    val uiState: LauncherUiState by viewModel.uiState.collectAsState()
    val context: Context = LocalContext.current
    val itemActions: LauncherItemActions = remember(viewModel, onItemLaunch, onItemOpenPopup, onItemAnchorChanged) {
        LauncherItemActions(
            onItemLaunch = onItemLaunch,
            onItemLongPress = viewModel::openItemMenu,
            onToggleFavorite = viewModel::toggleFavorite,
            onItemOpenPopup = onItemOpenPopup,
            onItemAnchorChanged = onItemAnchorChanged,
            onNewFolder = viewModel::openNewFolderDialog
        )
    }
    val home: LauncherUiState.Home? = retainedHomeState(uiState)
    val isHomeVisible: Boolean = uiState is LauncherUiState.Home
    val isEditingHome: Boolean = home?.isEditingFavorites == true || isResizing || isEditingWidgets || isMovingWidgetArea
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
    Box(modifier) {
        // Kept composed under the App List so returning to Home never rebuilds it.
        if (home != null) {
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = contentHorizontalPadding),
                        appList = state.groups,
                        lazyListState = state.lazyListState,
                        rowState = rowState,
                        itemActions = itemActions,
                        topOffset = viewModel.listStartOffsetPx.toDp(context).dp,
                        bottomOffset = viewModel.listEndOffsetPx.toDp(context).dp
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

@Composable
internal fun BoxScope.LauncherRail(viewModel: LauncherViewModel, enabled: Boolean = true, onRailTouched: () -> Unit = {}) {
    val railLabels: List<String> by viewModel.railLabels.collectAsState()
    ScrollRail(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(0.dp, railTopPadding, 0.dp, 0.dp)
            .width(railWidth)
            .offset { IntOffset(railHorizontalOffset.toPx().toInt(), 0) },
        items = railLabels,
        onScroll = { index ->
            onRailTouched()
            viewModel.onRailPreview(index)
        },
        onScrollEnded = { index ->
            onRailTouched()
            viewModel.onRailReleased(index)
        },
        enabled = enabled
    )
}

@Composable
internal fun BoxScope.LauncherDoneButton(
    viewModel: LauncherViewModel,
    widgetViewModel: WidgetAreaViewModel,
    popupViewModel: PopupViewModel
) {
    val isEditingFavorites: Boolean by viewModel.isEditingFavorites.collectAsState()
    val editDraft: WidgetEditDraft? by widgetViewModel.editState.collectAsState()
    val popupEditState: PopupEditState by popupViewModel.editState.collectAsState()
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
