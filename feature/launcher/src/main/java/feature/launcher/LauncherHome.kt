package feature.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.popups.NewFolderRow
import core.popups.R as PopupsR
import core.ui.composables.DragHandle
import core.ui.composables.OutlinedText
import core.ui.layout.dragHandleEndPadding
import core.ui.layout.homeEdgePadding
import core.ui.model.data.FAVORITES_EMPTY_HINT_KEY
import core.ui.model.data.favoriteKey
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import feature.launcher.widgets.CappedWidgetArea
import feature.launcher.widgets.EditMergeState
import feature.launcher.widgets.RESIZE_DIMMED_ALPHA
import feature.launcher.widgets.WIDGET_AREA_OFFSET_KEY
import feature.launcher.widgets.WidgetAreaLayout
import feature.launcher.widgets.WidgetAreaResizeAutoScroll
import feature.launcher.widgets.WidgetAreaScrollToPlaceEffect
import feature.launcher.widgets.WidgetAreaTopOffsetSpacer
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.uncappedListPrefix
import feature.launcher.widgets.widgetAreaItems
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val LAUNCHER_HOME_TEST_TAG: String = "launcherHome"
private const val NEW_FOLDER_KEY: String = "newFolder"

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
    onEmptyAreaLongPress: () -> Unit,
    isResizing: Boolean,
    isEditingWidgets: Boolean,
    isPopupSwipeEnabled: Boolean,
    onResizeOutsideTap: () -> Unit
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
                        isPopupSwipeEnabled = isPopupSwipeEnabled,
                        interaction = interaction
                    )
                }
            }

            WidgetAreaLayout.Uncapped -> {
                val placeIds: List<Long> = widgetArea.state.placeIds
                val favoriteKeys: List<String> = rememberFavoriteKeys(home.favorites)
                val reorderState: ReorderableLazyListState = rememberHomeReorderState(
                    listState = home.lazyListState,
                    favoriteKeys = favoriteKeys,
                    onMoveFavorite = onFavoriteMoved,
                    placeIds = placeIds,
                    onMovePlace = widgetArea.actions.onMovePlace
                )
                val mergeState: EditMergeState = remember { EditMergeState() }
                WidgetAreaScrollToPlaceEffect(
                    ui = widgetArea,
                    listState = home.lazyListState,
                    listPrefix = widgetArea.uncappedListPrefix
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(interaction.isResizing) {
                            if (interaction.isResizing) {
                                detectTapGestures(onTap = { interaction.onResizeOutsideTap() })
                            }
                        }
                        .homeEmptyAreaLongPress(
                            hapticFeedback = hapticFeedback,
                            enabled = menuEnabled && !interaction.isResizing,
                            onLongPress = onEmptyAreaLongPress
                        )
                        .testTag(LAUNCHER_HOME_TEST_TAG),
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
                    favoriteItems(
                        home = home,
                        itemActions = itemActions,
                        reorderableState = reorderState,
                        blockInteraction = interaction.isResizing || interaction.isEditingWidgets,
                        onBlockedGestureEnd = if (interaction.isResizing) interaction.onResizeOutsideTap else null,
                        isPopupSwipeEnabled = isPopupSwipeEnabled
                    )
                }
                WidgetAreaResizeAutoScroll(
                    ui = widgetArea,
                    listState = home.lazyListState,
                    listPrefix = widgetArea.uncappedListPrefix
                )
            }
        }
    }
}

private fun Modifier.homeEmptyAreaLongPress(hapticFeedback: HapticFeedback, enabled: Boolean, onLongPress: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (enabled) {
            detectTapGestures(
                onLongPress = {
                    hapticFeedback.performLongPressHaptic()
                    onLongPress()
                }
            )
        }
    }

@Composable
private fun FavoritesBottomArea(
    modifier: Modifier,
    home: LauncherUiState.Home,
    itemActions: LauncherItemActions,
    onFavoriteMoved: (fromIndex: Int, toIndex: Int) -> Unit,
    onEmptyAreaLongPress: () -> Unit,
    isPopupSwipeEnabled: Boolean,
    interaction: HomeInteraction
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val favoriteKeys: List<String> = rememberFavoriteKeys(home.favorites)
    val reorderState: ReorderableLazyListState = rememberHomeReorderState(
        listState = home.lazyListState,
        favoriteKeys = favoriteKeys,
        onMoveFavorite = onFavoriteMoved
    )
    Box(
        modifier = modifier
            .homeEmptyAreaLongPress(
                hapticFeedback = hapticFeedback,
                enabled = interaction.menuEnabled,
                onLongPress = onEmptyAreaLongPress
            )
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
                .align(Alignment.TopCenter)
                .alpha(if (interaction.isResizing || interaction.isEditingWidgets) RESIZE_DIMMED_ALPHA else 1f)
                .testTag(LAUNCHER_HOME_TEST_TAG),
            state = home.lazyListState,
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(start = homeEdgePadding, end = homeEdgePadding, bottom = homeEdgePadding)
        ) {
            favoriteItems(
                home = home,
                itemActions = itemActions,
                reorderableState = reorderState,
                isPopupSwipeEnabled = isPopupSwipeEnabled
            )
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
    isPopupSwipeEnabled: Boolean,
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
                        isPopupSwipeEnabled = isPopupSwipeEnabled,
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
    if (home.isEditingFavorites) {
        item(key = NEW_FOLDER_KEY) {
            NewFolderRow(
                label = stringResource(PopupsR.string.new_folder),
                onClick = itemActions.onNewFolder
            )
        }
    }
}
