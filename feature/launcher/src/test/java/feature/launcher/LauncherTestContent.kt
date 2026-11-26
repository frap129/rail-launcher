package feature.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.prefs.RailSide
import core.ui.composables.AppListRowState
import core.ui.composables.LAUNCHER_ROW_TEST_MODIFIER
import core.ui.composables.LauncherItemActions
import core.ui.composables.setLauncherRowTestContent
import core.ui.testing.createAppListRowState
import core.ui.testing.createLauncherItemActions
import core.ui.testing.createTestAppList
import core.ui.widgets.MediaPlayerUiState
import core.ui.widgets.WidgetAreaLayout
import core.ui.widgets.WidgetAreaState
import core.ui.widgets.WidgetAreaUi
import core.ui.widgets.widgetAreaActions

/** Sets the launcher App List content in a lazy list over the contrasting test background. */
fun ComposeContentTestRule.setLauncherAppListContent(
    itemActions: LauncherItemActions,
    appList: List<LaunchableGroup.Listed> = createTestAppList(),
    lazyListState: LazyListState = LazyListState()
) {
    setLauncherRowTestContent { modifier ->
        LauncherAppListTestContent(
            modifier = modifier,
            appList = appList,
            lazyListState = lazyListState,
            topOffset = 0.dp,
            rowState = createAppListRowState(),
            itemActions = itemActions
        )
    }
}

/** Sets Home Favorites, optionally blocked as widget editing and resizing do. */
fun ComposeContentTestRule.setHomeFavoriteRows(
    favorites: List<Launchable>,
    itemActions: LauncherItemActions = createLauncherItemActions(),
    isResizing: Boolean = false,
    isEditingWidgets: Boolean = false,
    lazyListState: LazyListState = LazyListState()
) {
    val home: LauncherUiState.Home = createHomeState(favorites = favorites, lazyListState = lazyListState)
    setContent {
        LauncherHomeTestContent(
            modifier = LAUNCHER_ROW_TEST_MODIFIER,
            home = home,
            itemActions = itemActions,
            isPopupSwipeEnabled = false,
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets
        )
    }
}

/** A Home state showing [favorites] and [suggestions], as Home tests compose it. */
fun createHomeState(
    favorites: List<Launchable>,
    suggestions: List<Launchable> = emptyList(),
    lazyListState: LazyListState = LazyListState()
): LauncherUiState.Home = LauncherUiState.Home(
    lazyListState = lazyListState,
    favorites = favorites,
    isFavoritesLoaded = true,
    suggestions = suggestions,
    isSwipeUpEnabled = false,
    isSwipeDownEnabled = false
)

/** A loaded, uncapped Widget Area with no widgets, as Home tests compose it. */
fun createTestWidgetAreaUi(): WidgetAreaUi = WidgetAreaUi(
    state = WidgetAreaState(isLoaded = true, layout = WidgetAreaLayout.Uncapped, listState = LazyListState()),
    resize = null,
    editDraft = null,
    mediaPlayer = MediaPlayerUiState.Hidden,
    scrollToPlaceId = null,
    actions = widgetAreaActions()
)

/** Home composed with the test Widget Area and the callbacks tests do not exercise. */
@Composable
fun LauncherHomeTestContent(
    home: LauncherUiState.Home,
    itemActions: LauncherItemActions,
    isPopupSwipeEnabled: Boolean,
    modifier: Modifier = Modifier,
    isResizing: Boolean = false,
    isEditingWidgets: Boolean = false,
    isRowFocusEnabled: Boolean = true,
    onFavoriteIconSettled: (Launchable) -> Unit = {}
) {
    val widgetArea: WidgetAreaUi = remember { createTestWidgetAreaUi() }
    LauncherHome(
        modifier = modifier,
        home = home,
        widgetArea = widgetArea,
        railSide = RailSide.DEFAULT,
        itemActions = itemActions,
        folderSummaries = emptyMap(),
        onFavoriteIconSettled = onFavoriteIconSettled,
        onFavoriteMoved = { _, _ -> },
        onEmptyAreaLongPress = {},
        onSwipeUp = {},
        isSwipeUpEnabled = false,
        onSwipeDown = {},
        isSwipeDownEnabled = false,
        isResizing = isResizing,
        isEditingWidgets = isEditingWidgets,
        isRowFocusEnabled = isRowFocusEnabled,
        isPopupSwipeEnabled = isPopupSwipeEnabled,
        onResizeOutsideTap = {}
    )
}

/** The App List composed for tests, with [rowState] and [itemActions] shared by its rows. */
@Composable
fun LauncherAppListTestContent(
    appList: List<LaunchableGroup.Listed>,
    lazyListState: LazyListState,
    topOffset: Dp,
    rowState: AppListRowState,
    itemActions: LauncherItemActions,
    modifier: Modifier = Modifier.fillMaxSize(),
    recentlyInstalled: RecentlyInstalledSection? = null,
    railSide: RailSide = RailSide.END,
    showRail: Boolean = true
) {
    LauncherAppList(
        modifier = modifier,
        appList = appList,
        recentlyInstalled = recentlyInstalled,
        lazyListState = lazyListState,
        rowState = rowState,
        itemActions = itemActions,
        folderSummaries = emptyMap(),
        topOffset = topOffset,
        bottomOffset = 0.dp,
        railSide = railSide,
        showRail = showRail
    )
}
