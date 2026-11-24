package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.prefs.RailSide
import feature.launcher.widgets.MediaPlayerUiState
import feature.launcher.widgets.WidgetAreaLayout
import feature.launcher.widgets.WidgetAreaState
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.widgetAreaActions

/** A Home state showing [favorites] and [suggestions], as Home tests compose it. */
fun createHomeState(favorites: List<Launchable>, suggestions: List<Launchable> = emptyList()): LauncherUiState.Home = LauncherUiState.Home(
    lazyListState = LazyListState(),
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
        isPopupSwipeEnabled = isPopupSwipeEnabled,
        onResizeOutsideTap = {}
    )
}

/** A column of Home suggestions, sized by [modifier]. */
@Composable
fun HomeSuggestionRowsTestContent(
    suggestions: List<Launchable>,
    itemActions: LauncherItemActions,
    isPopupSwipeEnabled: Boolean,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Column(modifier = modifier) {
        suggestions.forEach { suggestion ->
            HomeSuggestionRow(
                suggestion = suggestion,
                railSide = RailSide.DEFAULT,
                itemActions = itemActions,
                isPopupSwipeEnabled = isPopupSwipeEnabled
            )
        }
    }
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
