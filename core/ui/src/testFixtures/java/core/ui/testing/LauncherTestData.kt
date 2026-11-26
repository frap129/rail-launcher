package core.ui.testing

import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.RailSide
import core.data.testing.createApp
import core.ui.composables.AppListRowState
import core.ui.composables.LauncherItemActions
import core.ui.composables.MenuSuggestion
import core.ui.composables.PrivateSpaceActions

/** The first App every Launcher row test lays out. */
val testFirstApp: Launchable = createApp(name = "Alpha", packageName = "com.example.alpha")

/** The second App every Launcher row test lays out. */
val testSecondApp: Launchable = createApp(name = "Beta", packageName = "com.example.beta")

/** Creates the numbered App used by Favorite test fixtures. */
fun createFavorite(index: Int): App = createApp(name = "Favorite $index", packageName = "com.example.favorite$index")

/** Creates the default App List group used by Launcher row tests. */
fun createTestAppList(): List<LaunchableGroup.Listed> = listOf(
    LaunchableGroup.InitialGroup(label = "A", items = listOf(testFirstApp, testSecondApp))
)

/** Creates the default non-editing App List row state used by Launcher tests. */
fun createAppListRowState(isPopupSwipeEnabled: Boolean = false): AppListRowState = AppListRowState(
    isEditingFavorites = false,
    favoriteKeys = emptySet(),
    isPopupSwipeEnabled = isPopupSwipeEnabled
)

/** Creates no-op actions for a Launcher row. */
fun createLauncherItemActions(railSide: RailSide = RailSide.DEFAULT): LauncherItemActions = LauncherItemActions(
    onItemTap = { _, _ -> },
    onItemLongPress = { _, _ -> },
    onToggleFavorite = {},
    onItemOpenPopup = { _, _ -> },
    onItemAnchorChanged = { _, _ -> },
    privateSpaceActions = PrivateSpaceActions(onUnlock = {}, onLock = {}, onSettings = null),
    railBounds = { null },
    railSide = railSide
)

/** Creates actions that record every long-press and its menu suggestion. */
fun createLongPressRecordingActions(actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>>): LauncherItemActions =
    createLauncherItemActions().copy(
        onItemLongPress = { item, suggestion -> actualLongPresses += item to suggestion }
    )
