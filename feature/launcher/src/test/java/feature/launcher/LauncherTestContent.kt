package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.prefs.RailSide
import core.data.testing.createApp
import core.ui.composables.RowTestContent
import core.ui.popups.PopupRow
import feature.launcher.widgets.MediaPlayerUiState
import feature.launcher.widgets.WidgetAreaLayout
import feature.launcher.widgets.WidgetAreaState
import feature.launcher.widgets.WidgetAreaUi
import feature.launcher.widgets.widgetAreaActions

const val LAUNCHER_ROW_TEST_TAG: String = "launcherRow"
const val LAUNCHER_ACTION_ROW_TEST_TAG: String = "launcherActionRow"
const val PRIVATE_SPACE_ROW_TEST_TAG: String = "privateSpaceRow"

/** The first App every Launcher row test lays out. */
val testFirstApp: Launchable = createApp(name = "Alpha", packageName = "com.example.alpha")

/** The second App every Launcher row test lays out. */
val testSecondApp: Launchable = createApp(name = "Beta", packageName = "com.example.beta")

/** Creates the numbered App used by Favorite test fixtures. */
fun createFavorite(index: Int): App = createApp(name = "Favorite $index", packageName = "com.example.favorite$index")

/** Sets two Home suggestion rows over the contrasting test background. */
fun ComposeContentTestRule.setHomeSuggestionRows(itemActions: LauncherItemActions) {
    setContent {
        RowTestContent {
            HomeSuggestionRowsTestContent(itemActions = itemActions)
        }
    }
}

/** Sets App List groups of the test Apps over the contrasting test background. */
fun ComposeContentTestRule.setAppListRows(
    itemActions: LauncherItemActions,
    appList: List<LaunchableGroup.Listed> = listOf(
        LaunchableGroup.InitialGroup(label = "A", items = listOf(testFirstApp, testSecondApp))
    ),
    lazyListState: LazyListState = LazyListState()
) {
    setContent {
        RowTestContent {
            LauncherAppListTestContent(
                modifier = Modifier.size(width = 360.dp, height = 600.dp),
                appList = appList,
                lazyListState = lazyListState,
                topOffset = 0.dp,
                rowState = AppListRowState(isEditingFavorites = false, favoriteKeys = emptySet(), isPopupSwipeEnabled = false),
                itemActions = itemActions
            )
        }
    }
}

/** Sets one tagged Launcher row over the contrasting test background. */
fun ComposeContentTestRule.setLauncherItemRow(
    item: Launchable,
    itemActions: LauncherItemActions = createLauncherItemActions(),
    popupRow: PopupRow = PopupRow.Home,
    isRowFocusEnabled: Boolean = true
) {
    setContent {
        RowTestContent {
            LauncherItemTestContent(
                item = item,
                itemActions = itemActions,
                popupRow = popupRow,
                isRowFocusEnabled = isRowFocusEnabled
            )
        }
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
            modifier = Modifier.size(width = 360.dp, height = 600.dp),
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

/** A column of Home suggestions, sized by [modifier]. */
@Composable
fun HomeSuggestionRowsTestContent(
    itemActions: LauncherItemActions,
    suggestions: List<Launchable> = listOf(testFirstApp, testSecondApp),
    isPopupSwipeEnabled: Boolean = false,
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

/** One Launcher row composed for tests, tagged so tests can inspect and drive it. */
@Composable
fun LauncherItemTestContent(
    item: Launchable,
    itemActions: LauncherItemActions = createLauncherItemActions(),
    popupRow: PopupRow = PopupRow.Home,
    isRowFocusEnabled: Boolean = true
) {
    LauncherItem(
        item = item,
        isEditingFavorites = false,
        isFavorite = false,
        isRowFocusEnabled = isRowFocusEnabled,
        isPopupSwipeEnabled = false,
        popupRow = popupRow,
        itemActions = itemActions,
        modifier = Modifier.testTag(LAUNCHER_ROW_TEST_TAG)
    )
}

/** The Lock action row composed for tests, tagged so tests can inspect and drive it. */
@Composable
fun LauncherActionRowTestContent(onClick: () -> Unit = {}) {
    LauncherActionRow(
        imageVector = FontAwesomeIcons.Solid.Lock,
        label = "Lock Private Space",
        onClick = onClick,
        modifier = Modifier.testTag(LAUNCHER_ACTION_ROW_TEST_TAG)
    )
}

/** The locked Private Space container row composed for tests, tagged so tests can inspect and drive it. */
@Composable
fun PrivateSpaceRowTestContent(isLocked: Boolean = true, onUnlock: () -> Unit = {}) {
    PrivateSpaceRow(
        isLocked = isLocked,
        onUnlock = onUnlock,
        modifier = Modifier.testTag(PRIVATE_SPACE_ROW_TEST_TAG)
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
