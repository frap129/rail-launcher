package feature.launcher

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val START_OFFSET_PX: Int = 200
private const val END_OFFSET_PX: Int = 800
private const val RENAMED_NAME: String = "Renamed Launchable"

class LauncherViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val preferencesRepository: FakePreferencesRepository = FakePreferencesRepository()
    private val listedGroups: List<LaunchableGroup.Listed> = createListedGroups()
    private val groups: List<LaunchableGroup> = listOf(LaunchableGroup.Star) + listedGroups
    private val item: Launchable = listedGroups.first().items.first()

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
    fun `opens to Home`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val home: LauncherUiState.Home = expectHome()
                assertEquals(0, home.lazyListState.firstVisibleItemIndex)
                assertTrue(home.widgets.isEmpty())
            }
        }
    }

    @Test
    fun `builds the App List from the launchable groups`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[0])
                val appListState: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(listedGroups, appListState.groups)
                assertEquals(1, appListState.lazyListState.firstVisibleItemIndex)
                assertEquals(-START_OFFSET_PX, appListState.lazyListState.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun `shows the selected group when the Rail is touched`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                viewModel.onRailTouched(listedGroups[1])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[1]), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `updates the selected group when the Rail moves`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                viewModel.onRailTouched(listedGroups[0])
                viewModel.onRailMoved(listedGroups[2])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[2]), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `returns to the App List at the selected group when the Rail is released`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                viewModel.onRailTouched(listedGroups[1])
                viewModel.onRailMoved(listedGroups[2])
                viewModel.onRailReleased(listedGroups[2])
                val appListState: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(listedGroups, appListState.groups)
                assertEquals(3, appListState.lazyListState.firstVisibleItemIndex)
                assertEquals(-START_OFFSET_PX, appListState.lazyListState.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun `returns Home when the Star is released during a scrub`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailTouched(listedGroups[1])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[1]), expectMostRecentItem())
                viewModel.onRailMoved(LaunchableGroup.Star)
                viewModel.onRailReleased(LaunchableGroup.Star)
                expectHome()
            }
        }
    }

    @Test
    fun `returns Home when the Star is touched from the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[1])
                expectAppList()
                viewModel.onRailTouched(LaunchableGroup.Star)
                expectHome()
            }
        }
    }

    @Test
    fun `resumes the scrub preview after moving off the Star`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailTouched(listedGroups[0])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[0]), expectMostRecentItem())
                viewModel.onRailMoved(LaunchableGroup.Star)
                expectHome()
                viewModel.onRailMoved(listedGroups[2])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[2]), expectMostRecentItem())
                viewModel.onRailReleased(listedGroups[2])
                val appListState: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(3, appListState.lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `does not open the App List when the Star is touched on Home`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailTouched(LaunchableGroup.Star)
                viewModel.onRailReleased(LaunchableGroup.Star)
                expectNoEvents()
            }
        }
    }

    @Test
    fun `does not consume Back on Home`() {
        val viewModel: LauncherViewModel = createViewModel()

        assertFalse(viewModel.onBack())
    }

    @Test
    fun `returns to Home when Back is pressed in the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[1])
                expectAppList()
                assertTrue(viewModel.onBack())
                assertEquals(0, expectHome().lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `returns to Home when Back is pressed during the scrub preview`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailTouched(listedGroups[1])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[1]), expectMostRecentItem())
                assertTrue(viewModel.onBack())
                expectHome()
            }
        }
    }

    @Test
    fun `returns to Home when the launcher is re-selected from the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[1])
                expectAppList()
                viewModel.onHomeIntent()
                expectHome()
            }
        }
    }

    @Test
    fun `keeps Home scrolled to the top when the launcher is re-selected`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val firstHome: LauncherUiState.Home = expectHome()
                viewModel.onHomeIntent()
                val secondHome: LauncherUiState.Home = expectHome()
                assertNotSame(firstHome.lazyListState, secondHome.lazyListState)
                assertEquals(0, secondHome.lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `resets to Home when a Launchable is launched from the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[1])
                expectAppList()
                viewModel.onLaunchableLaunched()
                expectHome()
            }
        }
    }

    @Test
    fun `opens the menu of the Launchable that was long pressed`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.openItemMenu(item)
                assertEquals(LauncherMenuState.Menu(item = item), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `shows the rename action for the opened Launchable`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.openItemMenu(item)
                viewModel.openItemRename(item)
                assertEquals(LauncherMenuState.Rename(item = item), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `closes the menu`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.openItemMenu(item)
                viewModel.closeItemMenu()
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `renames a Launchable through the preferences repository`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.setItemName(item, RENAMED_NAME)
            assertEquals(
                listOf(FakePreferencesRepository.ItemRename(item = item, name = RENAMED_NAME)),
                preferencesRepository.renames
            )
        }
    }

    @Test
    fun `shows the menu again after a Launchable is renamed`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.setItemName(item, RENAMED_NAME)
                assertEquals(LauncherMenuState.Menu(item = item), expectMostRecentItem())
            }
        }
    }

    private fun createViewModel(
        launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = groups)
    ): LauncherViewModel = LauncherViewModel(
        listOffsets = ListOffsets(startPx = START_OFFSET_PX, endPx = END_OFFSET_PX),
        launcherItemRepo = launchableRepository,
        prefsRepo = preferencesRepository
    )

    private inline fun <reified T : LauncherUiState> TurbineTestContext<LauncherUiState>.expectState(): T {
        val actualState: LauncherUiState = expectMostRecentItem()
        assertTrue("Expected ${T::class.simpleName} but was $actualState", actualState is T)
        return actualState as T
    }

    private fun TurbineTestContext<LauncherUiState>.expectHome(): LauncherUiState.Home = expectState()

    private fun TurbineTestContext<LauncherUiState>.expectAppList(): LauncherUiState.AppList.Browsing = expectState()

    private fun createListedGroups(): List<LaunchableGroup.Listed> = listOf(
        LaunchableGroup.InitialGroup(label = "A", items = listOf(createLaunchable("Alpha"))),
        LaunchableGroup.InitialGroup(label = "B", items = listOf(createLaunchable("Beta"))),
        LaunchableGroup.Builtin(items = listOf(createLaunchable("Rail Settings")))
    )

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail://$name"
    )
}
