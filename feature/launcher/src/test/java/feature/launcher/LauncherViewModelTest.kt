package feature.launcher

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val START_OFFSET_PX: Int = 200
private const val END_OFFSET_PX: Int = 800

class LauncherViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val preferencesRepository: FakePreferencesRepository = FakePreferencesRepository()

    private fun createViewModel(launchableRepository: FakeLaunchableRepository): LauncherViewModel = LauncherViewModel(
        listOffsets = ListOffsets(startPx = START_OFFSET_PX, endPx = END_OFFSET_PX),
        launcherItemRepo = launchableRepository,
        prefsRepo = preferencesRepository
    )

    @Test
    fun `shows Loading when there are no groups`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = emptyList()))
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `builds the App List from the launchable groups`() {
        runTest {
            val groups: List<LaunchableGroup> = listOf(LaunchableGroup(name = "A", items = listOf(createLaunchable("Alpha"))))
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.uiState.test {
                val actualState: LauncherUiState = expectMostRecentItem()
                assertTrue(actualState is LauncherUiState.AppList)
                val appListState: LauncherUiState.AppList = actualState as LauncherUiState.AppList
                assertEquals(groups, appListState.groups)
                assertEquals(0, appListState.lazyListState.firstVisibleItemIndex)
                assertEquals(-START_OFFSET_PX, appListState.lazyListState.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun `raises Scrolling with the selected group when the Rail scroll starts`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onScrollStarted(1)
                val actualState: LauncherUiState = expectMostRecentItem()
                assertTrue(actualState is LauncherUiState.Scrolling)
                assertEquals(LauncherUiState.Scrolling(group = groups[1]), actualState)
            }
        }
    }

    @Test
    fun `updates the Scrolling group when the Rail scroll moves`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onScrollStarted(0)
                viewModel.onScroll(2)
                val actualState: LauncherUiState = expectMostRecentItem()
                assertTrue(actualState is LauncherUiState.Scrolling)
                assertEquals(LauncherUiState.Scrolling(group = groups[2]), actualState)
            }
        }
    }

    @Test
    fun `returns to the App List at the selected group when the Rail scroll ends`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onScrollStarted(1)
                viewModel.onScroll(2)
                viewModel.onScrollEnded(2)
                val actualState: LauncherUiState = expectMostRecentItem()
                assertTrue(actualState is LauncherUiState.AppList)
                val appListState: LauncherUiState.AppList = actualState as LauncherUiState.AppList
                assertEquals(groups, appListState.groups)
                assertEquals(3, appListState.lazyListState.firstVisibleItemIndex)
                assertEquals(-START_OFFSET_PX, appListState.lazyListState.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun `opens the menu of the Launchable that was long pressed`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val item: Launchable = groups.first().items.first()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.menuState.test {
                expectMostRecentItem()
                viewModel.openItemMenu(item)
                assertEquals(LauncherMenuState.Menu(item = item), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `shows the rename action for the opened Launchable`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val item: Launchable = groups.first().items.first()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.menuState.test {
                expectMostRecentItem()
                viewModel.openItemMenu(item)
                viewModel.openItemRename(item)
                assertEquals(LauncherMenuState.Rename(item = item), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `closes the menu`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val item: Launchable = groups.first().items.first()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.menuState.test {
                expectMostRecentItem()
                viewModel.openItemMenu(item)
                viewModel.closeItemMenu()
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `renames a Launchable through the preferences repository`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val item: Launchable = groups.first().items.first()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.setItemName(item, "Renamed Launchable")
            assertEquals(
                listOf(FakePreferencesRepository.ItemRename(item = item, name = "Renamed Launchable")),
                preferencesRepository.renames
            )
        }
    }

    @Test
    fun `shows the menu again after a Launchable is renamed`() {
        runTest {
            val groups: List<LaunchableGroup> = createGroups()
            val item: Launchable = groups.first().items.first()
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = groups))
            viewModel.menuState.test {
                expectMostRecentItem()
                viewModel.setItemName(item, "Renamed Launchable")
                assertEquals(LauncherMenuState.Menu(item = item), expectMostRecentItem())
            }
        }
    }

    private fun createGroups(): List<LaunchableGroup> = listOf(
        LaunchableGroup(name = "A", items = listOf(createLaunchable("Alpha"))),
        LaunchableGroup(name = "B", items = listOf(createLaunchable("Beta"))),
        LaunchableGroup(name = "C", items = listOf(createLaunchable("Gamma")))
    )

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail://$name"
    )
}
