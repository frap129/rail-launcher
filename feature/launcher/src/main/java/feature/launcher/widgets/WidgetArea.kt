package feature.launcher.widgets

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.media.MediaCommand
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetWidth
import core.ui.composables.OutlinedText
import core.ui.layout.homeContentEndInset
import core.ui.layout.homeEdgePadding
import core.ui.layout.insetWidgetContentWidth
import core.ui.model.data.placeKey
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import core.widgets.AppWidgetModel
import core.widgets.HeaderContent
import core.widgets.HeaderModel
import core.widgets.HeaderTapTarget
import core.widgets.WidgetModel
import feature.launcher.HomeReorder
import feature.launcher.R
import feature.launcher.ReorderRow
import feature.launcher.resolvePlaceReorder
import kotlin.math.abs
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber

private val emptyWidgetAreaHeight: Dp = 96.dp
private val minWidgetAreaContentHeight: Dp = 48.dp
private const val WIDGET_AREA_HEIGHT_ANIMATION_MILLIS: Int = 200
private const val WIDGET_AREA_OFFSET_ANIMATION_MILLIS: Int = 150
private val widgetAreaFadeHeight: Dp = 16.dp
private const val WIDGET_AREA_EMPTY_KEY: String = "widgetAreaEmpty"
const val WIDGET_AREA_OFFSET_KEY: String = "widgetAreaOffset"
private val stackIndicatorHeight: Dp = ResizeBounds.STACK_INDICATOR_HEIGHT_DP.dp
private val stackIndicatorDotSize: Dp = 6.dp
private val stackIndicatorGap: Dp = 6.dp

/** The Widget Area callbacks shared by the Capped and Uncapped Home layouts. */
@Immutable
data class WidgetAreaActions(
    val menuEnabled: Boolean,
    val onHomeHeight: (Int) -> Unit,
    val onWidgetAreaWidth: (Int) -> Unit,
    val onTapTarget: (HeaderTapTarget) -> Unit,
    val onHeaderLongPress: (Long) -> Unit,
    val onHeaderHeight: (Int) -> Unit,
    val onAppWidgetLongPress: (Long) -> Unit,
    val onAppWidgetUnpause: (Long) -> Unit,
    val onPageSettled: (Long, Int) -> Unit,
    val onEmptyAreaLongPress: () -> Unit,
    val onFinishResize: () -> Unit,
    val onDragResize: (Float) -> Unit,
    val onResetResize: () -> Unit,
    val onMovePlace: (Int, Int) -> Unit,
    val onRemovePlace: (Long) -> Unit,
    val onToggleExpand: (Long) -> Unit,
    val onMovePage: (Long, Int, Int) -> Unit,
    val onRemovePage: (Long, Long) -> Unit,
    val onSplitPage: (Long, Long, Int) -> Unit,
    val onMergePlace: (Long, Long) -> Unit,
    val onMediaPlayerOpen: () -> Unit,
    val onMediaPlayerLongPress: () -> Unit,
    val onMediaCommand: (MediaCommand) -> Unit
)

/** The Widget Area state and callbacks read by Home. */
@Stable
class WidgetAreaUi(
    val state: WidgetAreaState,
    val resize: ResizeState?,
    val editDraft: WidgetEditDraft?,
    val mediaPlayer: MediaPlayerUiState,
    val actions: WidgetAreaActions
)

/** Collects the Widget Area and builds its Home actions. */
@Composable
fun rememberWidgetAreaUi(viewModel: WidgetAreaViewModel, menuEnabled: Boolean = true): WidgetAreaUi {
    val state: WidgetAreaState by viewModel.widgetAreaState.collectAsState()
    val resize: ResizeState? by viewModel.resizeState.collectAsState()
    val editDraft: WidgetEditDraft? by viewModel.editState.collectAsState()
    val mediaPlayer: MediaPlayerUiState by viewModel.mediaPlayerState.collectAsState()
    val context: Context = LocalContext.current
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val actions: WidgetAreaActions = remember(viewModel, menuEnabled, context, hapticFeedback) {
        WidgetAreaActions(
            menuEnabled = menuEnabled,
            onHomeHeight = viewModel::setHomeHeight,
            onWidgetAreaWidth = viewModel::setWidgetAreaWidth,
            onTapTarget = context::openHeaderTapTarget,
            onHeaderLongPress = { widgetId ->
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openHeaderMenu(widgetId = widgetId)
                }
            },
            onHeaderHeight = viewModel::setHeaderHeight,
            onAppWidgetLongPress = { widgetId ->
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openAppWidgetMenu(widgetId = widgetId)
                }
            },
            onAppWidgetUnpause = viewModel::unpauseWorkProfile,
            onPageSettled = viewModel::onPageSettled,
            onEmptyAreaLongPress = {
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openEmptyAreaMenu()
                }
            },
            onFinishResize = viewModel::finishResize,
            onDragResize = viewModel::dragResize,
            onResetResize = viewModel::resetResize,
            onMovePlace = viewModel::moveWidgetPlace,
            onRemovePlace = viewModel::removeWidgetPlaceInEdit,
            onToggleExpand = viewModel::toggleStackExpanded,
            onMovePage = viewModel::movePage,
            onRemovePage = viewModel::removePageInEdit,
            onSplitPage = viewModel::splitPage,
            onMergePlace = viewModel::mergePlace,
            onMediaPlayerOpen = viewModel::openMediaPlayer,
            onMediaPlayerLongPress = {
                if (menuEnabled) {
                    hapticFeedback.performLongPressHaptic()
                    viewModel.openMediaPlayerMenu()
                }
            },
            onMediaCommand = viewModel::onMediaCommand
        )
    }
    return remember(state, resize, editDraft, mediaPlayer, actions) {
        WidgetAreaUi(state = state, resize = resize, editDraft = editDraft, mediaPlayer = mediaPlayer, actions = actions)
    }
}

/** The scrollable, height-capped Widget Area shown above Favorites when the limit is on. */
@Composable
fun CappedWidgetArea(ui: WidgetAreaUi, maxHeight: Dp, modifier: Modifier = Modifier) {
    val state: WidgetAreaState = ui.state
    val actions: WidgetAreaActions = ui.actions
    val mediaPlayerVisible: Boolean = ui.mediaPlayer is MediaPlayerUiState.Visible
    LaunchedEffect(mediaPlayerVisible, state.mediaPlayerPlacement) {
        if (mediaPlayerVisible && state.mediaPlayerPlacement == MediaPlayerPlacement.TOP_OF_WIDGET_AREA) {
            state.listState.animateScrollToItem(index = 0)
        }
    }
    val reorderState: ReorderableLazyListState = rememberReorderableLazyListState(state.listState) { from, to ->
        val rows: List<ReorderRow> = state.listState.layoutInfo.visibleItemsInfo.map { item -> ReorderRow(item.index, item.key) }
        val reorder: HomeReorder.Place? = resolvePlaceReorder(
            rows = rows,
            fromListIndex = from.index,
            toListIndex = to.index,
            placeIds = state.placeIds
        )
        if (reorder != null) {
            actions.onMovePlace(reorder.fromIndex, reorder.toIndex)
        }
    }
    val mergeState: EditMergeState = remember { EditMergeState() }
    val density: Density = LocalDensity.current
    val topOffsetDp: Int = rememberAnimatedTopOffsetDp(state = state)
    val contentHeightPx: Int? by remember(state.listState) {
        derivedStateOf {
            val layoutInfo: LazyListLayoutInfo = state.listState.layoutInfo
            if (layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.size == layoutInfo.totalItemsCount) {
                layoutInfo.visibleItemsInfo.sumOf { item -> item.size } +
                    layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
            } else {
                null
            }
        }
    }
    val areaHeight: Dp = contentHeightPx?.let { heightPx ->
        with(density) { heightPx.toDp() }.coerceAtMost(maxHeight)
    } ?: maxHeight
    val topPadding: Dp = (homeEdgePadding + topOffsetDp.dp)
        .coerceAtMost((maxHeight - minWidgetAreaContentHeight).coerceAtLeast(0.dp))
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widgetAreaWidthDp: Int = insetWidgetContentWidth(maxWidth).value.roundToInt()
        LaunchedEffect(widgetAreaWidthDp) {
            if (widgetAreaWidthDp > 0) {
                actions.onWidgetAreaWidth(widgetAreaWidthDp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = WIDGET_AREA_HEIGHT_ANIMATION_MILLIS))
                .height(areaHeight)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawWidgetAreaFade(listState = state.listState)
                }
        ) {
            LazyColumn(
                state = state.listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = homeEdgePadding,
                    top = topPadding,
                    end = homeEdgePadding
                )
            ) {
                widgetAreaItems(
                    ui = ui,
                    reorderableState = reorderState,
                    mergeState = mergeState
                )
            }
        }
    }
    WidgetAreaResizeAutoScroll(ui = ui, listState = state.listState)
}

/** Adds the Widget Area places to an existing Home column. */
fun LazyListScope.widgetAreaItems(ui: WidgetAreaUi, reorderableState: ReorderableLazyListState, mergeState: EditMergeState) {
    val state: WidgetAreaState = ui.state
    val placement: MediaPlayerPlacement = (ui.mediaPlayer as? MediaPlayerUiState.Visible)?.placement
        ?: state.mediaPlayerPlacement
    val mediaAtTop: Boolean = placement == MediaPlayerPlacement.TOP_OF_WIDGET_AREA
    val spacing: Dp = state.widgetSpacingDp.dp
    val mediaTopSpacing: Dp = if (mediaAtTop) 0.dp else spacing
    val mediaBottomSpacing: Dp = if (mediaAtTop && state.hasWidgets) spacing else 0.dp
    if (mediaAtTop) {
        mediaPlayerItem(ui = ui, topSpacing = mediaTopSpacing, bottomSpacing = mediaBottomSpacing)
    }
    if (!state.hasWidgets) {
        item(key = WIDGET_AREA_EMPTY_KEY) {
            EmptyWidgetArea(
                menuEnabled = ui.actions.menuEnabled && ui.resize == null && !state.isEditing,
                showNoWidgets = state.isEditing,
                onLongPress = ui.actions.onEmptyAreaLongPress
            )
        }
    } else if (state.isEditing) {
        itemsIndexed(state.places, key = { _, place -> placeKey(place.id) }) { index, place ->
            ReorderableItem(state = reorderableState, key = placeKey(place.id)) { isDragging ->
                Column {
                    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
                    val editablePlace: WidgetPlaceModel = place.copy(widthMode = WidgetWidth.Inset)
                    EditablePlaceItem(
                        place = editablePlace,
                        index = index,
                        places = state.places,
                        mergeState = mergeState,
                        isDragging = isDragging,
                        isExpanded = place.id in ui.editDraft?.expandedPlaceIds.orEmpty(),
                        maxStackPages = state.maxStackPages,
                        onMovePlace = ui.actions.onMovePlace,
                        onRemovePlace = ui.actions.onRemovePlace,
                        onToggleExpand = ui.actions.onToggleExpand,
                        onMovePage = ui.actions.onMovePage,
                        onRemovePage = ui.actions.onRemovePage,
                        onSplitPage = ui.actions.onSplitPage,
                        onMergePlace = ui.actions.onMergePlace,
                        handleModifier = Modifier.draggableHandle(
                            onDragStarted = { hapticFeedback.performLongPressHaptic() }
                        )
                    ) { contentModifier ->
                        placeContent(
                            ui = ui,
                            place = editablePlace,
                            heightDp = place.heightDp,
                            menuEnabled = false,
                            isResizing = false,
                            modifier = contentModifier
                        )
                    }
                    if (index != state.places.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(state.widgetSpacingDp.dp))
                    }
                }
            }
        }
    } else {
        itemsIndexed(state.places, key = { _, place -> placeKey(place.id) }) { index, place ->
            WidgetAreaPlace(
                ui = ui,
                place = place,
                bottomSpacing = if (index != state.places.lastIndex) state.widgetSpacingDp.dp else 0.dp
            )
        }
    }
    if (!mediaAtTop) {
        mediaPlayerItem(ui = ui, topSpacing = mediaTopSpacing, bottomSpacing = mediaBottomSpacing)
    }
}

/** The leading Home spacer that moves the uncapped Widget Area down by the set offset. */
@Composable
fun WidgetAreaTopOffsetSpacer(ui: WidgetAreaUi, modifier: Modifier = Modifier) {
    val state: WidgetAreaState = ui.state
    val offsetDp: Int = rememberAnimatedTopOffsetDp(state = state)
    Box(modifier = modifier.fillMaxWidth().height(offsetDp.dp))
}

@Composable
private fun rememberAnimatedTopOffsetDp(state: WidgetAreaState): Int = animateIntAsState(
    targetValue = state.topOffsetDp,
    animationSpec = if (state.animateOffset) {
        tween(durationMillis = WIDGET_AREA_OFFSET_ANIMATION_MILLIS)
    } else {
        snap()
    },
    label = "widgetAreaTopOffset"
).value

@Composable
private fun WidgetAreaPlace(ui: WidgetAreaUi, place: WidgetPlaceModel, bottomSpacing: Dp) {
    val actions: WidgetAreaActions = ui.actions
    val placeResize: ResizeState? = ui.resize?.takeIf { resize -> resize.placeId == place.id }
    if (placeResize != null) {
        ResizeOverlay(
            state = placeResize,
            onDrag = actions.onDragResize,
            onReset = actions.onResetResize
        ) {
            placeContent(
                ui = ui,
                place = place,
                heightDp = placeResize.draftHeightDp,
                menuEnabled = false,
                isResizing = true,
                modifier = Modifier.consumeTouches().padding(bottom = bottomSpacing)
            )
        }
    } else {
        val dimmed: Boolean = ui.resize != null
        placeContent(
            ui = ui,
            place = place,
            heightDp = place.heightDp,
            menuEnabled = actions.menuEnabled && !dimmed,
            isResizing = false,
            modifier = Modifier
                .alpha(if (dimmed) RESIZE_DIMMED_ALPHA else 1f)
                .then(
                    if (dimmed) {
                        Modifier.consumeTouches(onGestureEnd = actions.onFinishResize)
                    } else {
                        Modifier
                    }
                )
                .padding(bottom = bottomSpacing)
        )
    }
}

@Composable
private fun placeContent(
    ui: WidgetAreaUi,
    place: WidgetPlaceModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    modifier: Modifier
) {
    WidgetPlaceContent(
        place = place,
        heightDp = heightDp,
        menuEnabled = menuEnabled,
        isResizing = isResizing,
        stackIndicatorVisible = ui.state.stackIndicatorVisible,
        onTapTarget = ui.actions.onTapTarget,
        onHeaderLongPress = ui.actions.onHeaderLongPress,
        onHeaderHeight = ui.actions.onHeaderHeight,
        onAppWidgetLongPress = ui.actions.onAppWidgetLongPress,
        onAppWidgetUnpause = ui.actions.onAppWidgetUnpause,
        onPageSettled = ui.actions.onPageSettled,
        userScrollEnabled = menuEnabled && !isResizing,
        modifier = modifier
    )
}

/** Keeps the bottom of the place being resized inside [listState]'s viewport. */
@Composable
fun WidgetAreaResizeAutoScroll(ui: WidgetAreaUi, listState: LazyListState) {
    val resize: ResizeState = ui.resize ?: return
    val placeIndex: Int = ui.state.places.indexOfFirst { place -> place.id == resize.placeId }
    if (placeIndex < 0) {
        return
    }
    LaunchedEffect(listState, resize.placeId, resize.draftHeightDp) {
        val placeItemKey: String = placeKey(resize.placeId)
        val initialLayoutInfo: LazyListLayoutInfo = listState.layoutInfo
        if (initialLayoutInfo.visibleItemsInfo.none { item -> item.key == placeItemKey }) {
            listState.animateScrollToItem(index = placeIndex)
        }
        val layoutInfo: LazyListLayoutInfo = listState.layoutInfo
        val visibleInfo: LazyListItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.key == placeItemKey }
            ?: return@LaunchedEffect
        val viewportStart: Int = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
        val viewportEnd: Int = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val overflow: Int = visibleInfo.offset + visibleInfo.size - viewportEnd
        if (overflow > 0) {
            listState.scrollBy(overflow.toFloat())
        } else if (visibleInfo.size <= viewportEnd - viewportStart && visibleInfo.offset < viewportStart) {
            listState.scrollBy((visibleInfo.offset - viewportStart).toFloat())
        }
    }
}

private fun DrawScope.drawWidgetAreaFade(listState: LazyListState) {
    val fadeHeightPx: Float = widgetAreaFadeHeight.toPx()
    if (fadeHeightPx <= 0f) {
        return
    }
    val isScrolledFromTop: Boolean = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    if (isScrolledFromTop) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = 0f,
                endY = fadeHeightPx
            ),
            topLeft = Offset.Zero,
            size = Size(width = size.width, height = fadeHeightPx.coerceAtMost(size.height)),
            blendMode = BlendMode.DstOut
        )
    }
    if (listState.canScrollForward) {
        val fadeTop: Float = (size.height - fadeHeightPx).coerceAtLeast(0f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = fadeTop,
                endY = size.height
            ),
            topLeft = Offset(x = 0f, y = fadeTop),
            size = Size(width = size.width, height = size.height - fadeTop),
            blendMode = BlendMode.DstOut
        )
    }
}

@Composable
private fun EmptyWidgetArea(menuEnabled: Boolean, showNoWidgets: Boolean, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .insetWidgetWidth()
            .height(emptyWidgetAreaHeight)
            .pointerInput(menuEnabled) {
                if (menuEnabled) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
            }
    ) {
        if (showNoWidgets) {
            OutlinedText(
                text = stringResource(R.string.no_widgets),
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun WidgetPlaceContent(
    place: WidgetPlaceModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    stackIndicatorVisible: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: (Long) -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit,
    onPageSettled: (Long, Int) -> Unit,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (place.isStack) {
        WidgetStack(
            place = place,
            heightDp = heightDp,
            menuEnabled = menuEnabled,
            isResizing = isResizing,
            indicatorVisible = stackIndicatorVisible,
            onTapTarget = onTapTarget,
            onHeaderLongPress = onHeaderLongPress,
            onHeaderHeight = onHeaderHeight,
            onAppWidgetLongPress = onAppWidgetLongPress,
            onAppWidgetUnpause = onAppWidgetUnpause,
            onPageSettled = onPageSettled,
            userScrollEnabled = menuEnabled && !isResizing,
            modifier = modifier
        )
    } else {
        Column(modifier = modifier.insetWidgetWidth(place.widthMode)) {
            place.widgets.forEach { widget ->
                WidgetPageContent(
                    widget = widget,
                    heightDp = heightDp,
                    menuEnabled = menuEnabled,
                    isResizing = isResizing,
                    onTapTarget = onTapTarget,
                    onHeaderLongPress = onHeaderLongPress,
                    onHeaderHeight = onHeaderHeight,
                    onAppWidgetLongPress = onAppWidgetLongPress,
                    onAppWidgetUnpause = onAppWidgetUnpause
                )
            }
        }
    }
}

@Composable
private fun WidgetStack(
    place: WidgetPlaceModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    indicatorVisible: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: (Long) -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit,
    onPageSettled: (Long, Int) -> Unit,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val pageCount: Int = place.widgets.size
    val pagerState = rememberPagerState(initialPage = place.currentPage.coerceIn(0, pageCount - 1)) { pageCount }
    val totalHeight: Dp = (heightDp ?: ResizeBounds.MIN_HEIGHT_DP).dp
    val indicatorHeight: Dp = if (indicatorVisible) stackIndicatorHeight else 0.dp
    val pageHeight: Dp = (totalHeight - indicatorHeight).coerceAtLeast(1.dp)
    val pageHeightDp: Int = pageHeight.value.roundToInt()
    LaunchedEffect(place.currentPage, pageCount) {
        val target: Int = place.currentPage.coerceIn(0, pageCount - 1)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        onPageSettled(place.id, pagerState.settledPage)
    }
    val dominanceFilter: NestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && abs(available.y) >= abs(available.x)) {
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
        }
    }
    Column(modifier = modifier.insetWidgetWidth(place.widthMode)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .nestedScroll(dominanceFilter),
            userScrollEnabled = userScrollEnabled,
            key = { page -> place.widgets[page].id }
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                WidgetPageContent(
                    widget = place.widgets[page],
                    heightDp = pageHeightDp,
                    menuEnabled = menuEnabled,
                    isResizing = isResizing,
                    onTapTarget = onTapTarget,
                    onHeaderLongPress = onHeaderLongPress,
                    onHeaderHeight = onHeaderHeight,
                    onAppWidgetLongPress = onAppWidgetLongPress,
                    onAppWidgetUnpause = onAppWidgetUnpause
                )
            }
        }
        if (indicatorVisible) {
            StackPageIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage
            )
        }
    }
}

@Composable
private fun StackPageIndicator(pageCount: Int, currentPage: Int) {
    val description: String = stringResource(R.string.stack_page_of, currentPage + 1, pageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stackIndicatorHeight)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = stackIndicatorGap / 2)
                    .size(stackIndicatorDotSize)
                    .background(
                        color = if (page == currentPage) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun WidgetPageContent(
    widget: WidgetModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: (Long) -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit
) {
    when (widget) {
        is HeaderModel -> {
            val density: Density = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> onHeaderHeight(with(density) { size.height.toDp().value.roundToInt() }) }
            ) {
                HeaderContent(
                    model = widget,
                    menuEnabled = menuEnabled,
                    interactive = menuEnabled,
                    onTapTarget = onTapTarget,
                    onLongPress = { onHeaderLongPress(widget.id) }
                )
            }
        }

        is AppWidgetModel -> AppWidgetContent(
            widget = widget,
            heightDp = heightDp,
            menuEnabled = menuEnabled,
            isResizing = isResizing,
            onLongPress = onAppWidgetLongPress,
            onUnpause = onAppWidgetUnpause
        )
    }
}

/** Fills the content width and keeps the Widget clear of the Rail's touch column according to [widthMode]. */
internal fun Modifier.insetWidgetWidth(widthMode: WidgetWidth = WidgetWidth.Inset): Modifier =
    fillMaxWidth().padding(end = widthMode.endInset())

internal fun WidgetWidth.endInset(): Dp = when (this) {
    WidgetWidth.Inset -> homeContentEndInset
    WidgetWidth.UnderRail -> 0.dp
}

private fun Context.openHeaderTapTarget(target: HeaderTapTarget) {
    when (target) {
        HeaderTapTarget.None -> Unit

        is HeaderTapTarget.LaunchLaunchable -> openLaunchable(target.launchable)

        HeaderTapTarget.SystemAlarms -> openSystemIntent(
            intent = Intent(AlarmClock.ACTION_SHOW_ALARMS),
            target = target,
            messageResource = R.string.no_clock_app
        )

        is HeaderTapTarget.SystemCalendar -> openSystemIntent(
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${target.atEpochMillis}")),
            target = target,
            messageResource = R.string.no_calendar_app
        )

        HeaderTapTarget.SystemBatteryUsage -> openSystemIntent(
            intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            target = target,
            messageResource = R.string.no_battery_usage_app
        )

        is HeaderTapTarget.OpenEvent -> openEvent(target)
    }
}

private fun Context.openEvent(target: HeaderTapTarget.OpenEvent) {
    if (target.isWorkProfile) {
        try {
            val opened: Boolean = CalendarContract.startViewCalendarEventInManagedProfile(
                this,
                target.eventId,
                target.beginMillis,
                target.endMillis,
                false,
                0
            )
            if (!opened) {
                Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
            }
        } catch (exception: SecurityException) {
            Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
            Timber.w(exception, "Could not open work-profile event %s", target.eventId)
        }
        return
    }
    val intent: Intent = Intent(Intent.ACTION_VIEW)
        .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.endMillis)
    openSystemIntent(intent = intent, target = target, messageResource = R.string.no_calendar_app)
}

private fun Context.openLaunchable(launchable: Launchable) {
    try {
        launchable.launch(this)
    } catch (exception: SecurityException) {
        showLaunchFailure(launchable = launchable, exception = exception)
    } catch (exception: ActivityNotFoundException) {
        showLaunchFailure(launchable = launchable, exception = exception)
    }
}

private fun Context.showLaunchFailure(launchable: Launchable, exception: Exception) {
    Toast.makeText(this, getString(R.string.cannot_open_launchable, launchable.name), Toast.LENGTH_SHORT).show()
    Timber.w(exception, "Could not open Launchable %s", launchable.key)
}

private fun Context.openSystemIntent(intent: Intent, target: HeaderTapTarget, messageResource: Int) {
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, messageResource, Toast.LENGTH_SHORT).show()
        Timber.w(exception, "No activity for %s", target)
    }
}
