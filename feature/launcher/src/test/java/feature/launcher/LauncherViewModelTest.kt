package feature.launcher

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.favorites.FavoriteRecord
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeFolderRepository
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupRepository
import core.data.testing.FakePreferencesRepository
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createEagerFavoritesRepository
import core.data.testing.createNotificationHandle
import core.data.testing.createNotificationRecord
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val START_OFFSET_PX: Int = 200
private const val END_OFFSET_PX: Int = 800
private const val RENAMED_NAME: String = "Renamed Launchable"
private const val FAVORITE_FOLDER_ID: Long = 42L
private const val SUMMARY_PACKAGE_NAME: String = "com.example.summary"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LauncherViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository()
    private val inputBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = listOf(createLaunchable("Rail Settings")))
    private val inputListedGroups: List<LaunchableGroup.Listed> = listOf(
        LaunchableGroup.InitialGroup(label = "A", items = listOf(createLaunchable("Alpha"))),
        LaunchableGroup.InitialGroup(label = "B", items = listOf(createLaunchable("Beta"))),
        inputBuiltinGroup
    )
    private val inputGroups: List<LaunchableGroup> = listOf(LaunchableGroup.Star) + inputListedGroups
    private val inputItem: Launchable = inputListedGroups.first().items.first()
    private val mockNotificationRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()

    @Test
    fun `shows Loading until the Launchables are loaded`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = inputGroups, isLoaded = false))
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `shows Home when the Launchables are loaded and there are none`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(FakeLaunchableRepository(groups = emptyList()))
            viewModel.uiState.test {
                expectHome()
            }
        }
    }

    @Test
    fun `shows Home with the Star and Builtin groups when there are no Launchables`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockLaunchableRepository = FakeLaunchableRepository(groups = listOf(LaunchableGroup.Star, inputBuiltinGroup))
            )
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                assertTrue(actualHome.favorites.isEmpty())
            }
            viewModel.railLabels.test {
                assertEquals(listOf("★", "⌂"), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `opens to Home`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                assertEquals(0, actualHome.lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `builds the App List from the launchable groups`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                val actualAppListState: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(inputListedGroups, actualAppListState.groups)
                assertEquals(1, actualAppListState.lazyListState.firstVisibleItemIndex)
                assertEquals(-START_OFFSET_PX, actualAppListState.lazyListState.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun `exposes the Rail labels`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.railLabels.test {
                assertEquals(listOf("★", "A", "B", "⌂"), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `maps Rail indexes through the Star, digit, initial and builtin groups`() {
        runTest {
            val inputDigitGroup: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(
                label = "#",
                items = listOf(createLaunchable("1Password"))
            )
            val viewModel: LauncherViewModel = createViewModel(
                mockLaunchableRepository = FakeLaunchableRepository(
                    groups = listOf(LaunchableGroup.Star, inputDigitGroup) + inputListedGroups
                )
            )
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailPreview(index = 1)
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputDigitGroup, isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onRailPreview(index = 2)
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[0], isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onRailPreview(index = 0)
                expectHome()
                viewModel.onRailPreview(index = 3)
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[1], isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onRailReleased(index = 4)
                assertEquals(4, expectAppList().lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `ignores a Rail index outside the groups`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailPreview(index = 99)
                runCurrent()
                expectNoEvents()
            }
        }
    }

    @Test
    fun `emits one uiState per Rail event`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailPreview(index = 1)
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[0], isEditingFavorites = false),
                    awaitItem()
                )
                runCurrent()
                expectNoEvents()
                viewModel.onRailPreview(index = 2)
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[1], isEditingFavorites = false),
                    awaitItem()
                )
                runCurrent()
                expectNoEvents()
                viewModel.onRailReleased(index = 2)
                assertEquals(2, awaitState<LauncherUiState.AppList.Browsing>().lazyListState.firstVisibleItemIndex)
                runCurrent()
                expectNoEvents()
                viewModel.onBack()
                awaitState<LauncherUiState.Home>()
                runCurrent()
                expectNoEvents()
                viewModel.onRailReleased(index = 1)
                awaitState<LauncherUiState.AppList.Browsing>()
                runCurrent()
                expectNoEvents()
                viewModel.onHomeIntent()
                awaitState<LauncherUiState.Home>()
                runCurrent()
                expectNoEvents()
                viewModel.onRailReleased(index = 1)
                awaitState<LauncherUiState.AppList.Browsing>()
                runCurrent()
                expectNoEvents()
                viewModel.onLaunchableLaunched()
                awaitState<LauncherUiState.Home>()
                runCurrent()
                expectNoEvents()
            }
        }
    }

    @Test
    fun `shows the selected group when the Rail is touched`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                viewModel.onRailPreview(railIndex(inputListedGroups[1]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[1], isEditingFavorites = false),
                    expectMostRecentItem()
                )
            }
        }
    }

    @Test
    fun `updates the selected group when the Rail moves`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                viewModel.onRailPreview(railIndex(inputListedGroups[0]))
                viewModel.onRailPreview(railIndex(inputListedGroups[2]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[2], isEditingFavorites = false),
                    expectMostRecentItem()
                )
            }
        }
    }

    @Test
    fun `returns to the App List at the selected group when the Rail is released`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                viewModel.onRailPreview(railIndex(inputListedGroups[1]))
                viewModel.onRailPreview(railIndex(inputListedGroups[2]))
                viewModel.onRailReleased(railIndex(inputListedGroups[2]))
                val actualAppListState: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(inputListedGroups, actualAppListState.groups)
                assertEquals(3, actualAppListState.lazyListState.firstVisibleItemIndex)
                assertEquals(-START_OFFSET_PX, actualAppListState.lazyListState.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test
    fun `returns Home when the Star is released during a scrub`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailPreview(railIndex(inputListedGroups[1]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[1], isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onRailPreview(railIndex(LaunchableGroup.Star))
                viewModel.onRailReleased(railIndex(LaunchableGroup.Star))
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
                viewModel.onRailReleased(railIndex(inputListedGroups[1]))
                expectAppList()
                viewModel.onRailPreview(railIndex(LaunchableGroup.Star))
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
                viewModel.onRailPreview(railIndex(inputListedGroups[0]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[0], isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onRailPreview(railIndex(LaunchableGroup.Star))
                expectHome()
                viewModel.onRailPreview(railIndex(inputListedGroups[2]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[2], isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onRailReleased(railIndex(inputListedGroups[2]))
                val actualAppListState: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(3, actualAppListState.lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `does not open the App List when the Star is touched on Home`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailPreview(railIndex(LaunchableGroup.Star))
                viewModel.onRailReleased(railIndex(LaunchableGroup.Star))
                expectNoEvents()
            }
        }
    }

    @Test
    fun `persists the edited order when Back is pressed on Home while editing Favorites`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 0),
                    FavoriteRecord(key = inputSecondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
                viewModel.onBack()
                val actualHome: LauncherUiState.Home = expectHome()
                assertFalse(actualHome.isEditingFavorites)
                assertEquals(listOf(inputSecondItem, inputItem), actualHome.favorites)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = inputSecondItem.key, position = 0),
                    FavoriteRecord(key = inputItem.key, position = 1)
                ),
                mockFavoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `keeps Home when Back is pressed outside Edit Favorites`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                viewModel.onBack()
                runCurrent()
                expectNoEvents()
                assertSame(actualHome.lazyListState, (viewModel.uiState.value as LauncherUiState.Home).lazyListState)
            }
        }
    }

    @Test
    fun `returns to Home when Back is pressed in the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[1]))
                expectAppList()
                viewModel.onBack()
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
                viewModel.onRailPreview(railIndex(inputListedGroups[1]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[1], isEditingFavorites = false),
                    expectMostRecentItem()
                )
                viewModel.onBack()
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
                viewModel.onRailReleased(railIndex(inputListedGroups[1]))
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
                val actualFirstHome: LauncherUiState.Home = expectHome()
                viewModel.onHomeIntent()
                val actualSecondHome: LauncherUiState.Home = expectHome()
                assertNotSame(actualFirstHome.lazyListState, actualSecondHome.lazyListState)
                assertEquals(0, actualSecondHome.lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `resets to Home when a Launchable is launched from the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[1]))
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
                viewModel.openItemMenu(inputItem)
                assertEquals(LauncherMenuState.Menu(item = inputItem), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `shows the rename action for the opened Launchable`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.openItemMenu(inputItem)
                viewModel.openItemRename(inputItem)
                assertEquals(LauncherMenuState.Rename(item = inputItem), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `closes the menu`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.openItemMenu(inputItem)
                viewModel.closeItemMenu()
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `renames a Launchable through the preferences repository`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.setItemName(inputItem, RENAMED_NAME)
            runCurrent()
            assertEquals(
                listOf(FakePreferencesRepository.ItemRename(item = inputItem, name = RENAMED_NAME)),
                mockPreferencesRepository.renames
            )
        }
    }

    @Test
    fun `shows the menu again after a Launchable is renamed`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.setItemName(inputItem, RENAMED_NAME)
                assertEquals(LauncherMenuState.Menu(item = inputItem), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `adds a Launchable to the Favorites shown on Home`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertTrue(expectHome().favorites.isEmpty())
                viewModel.toggleFavorite(inputItem)
                assertEquals(listOf(inputItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `adds Favorites in the order they were added`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(inputItem)
                viewModel.toggleFavorite(inputSecondItem)
                assertEquals(listOf(inputItem, inputSecondItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `orders Favorites by their stored position`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 1),
                    FavoriteRecord(key = inputSecondItem.key, position = 0)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `removes a Launchable from the Favorites`() {
        runTest {
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(inputItem)
                assertEquals(listOf(inputItem), expectHome().favorites)
                viewModel.toggleFavorite(inputItem)
                assertTrue(expectHome().favorites.isEmpty())
            }
            assertTrue(mockFavoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `toggles a Favorite twice in a row`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(inputItem)
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                assertTrue(expectHome().favorites.isEmpty())
            }
        }
    }

    @Test
    fun `shows the same Edit Favorites menu in the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                expectAppList()
                viewModel.menuState.test {
                    viewModel.openItemMenu(inputItem)
                    assertEquals(LauncherMenuState.Menu(item = inputItem), expectMostRecentItem())
                }
            }
        }
    }

    @Test
    fun `enters Edit Favorites mode from the menu`() {
        runTest {
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = inputItem.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                assertFalse(expectHome().isEditingFavorites)
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
            }
        }
    }

    @Test
    fun `exposes Edit Favorites mode`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.isEditingFavorites.test {
                assertFalse(expectMostRecentItem())
                viewModel.editFavorites()
                assertTrue(awaitItem())
                viewModel.finishEditingFavorites()
                assertFalse(awaitItem())
            }
        }
    }

    @Test
    fun `returns to Home still editing Favorites when Back is pressed in the App List`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 0),
                    FavoriteRecord(key = inputSecondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                expectAppList()
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                viewModel.onBack()
                val actualHome: LauncherUiState.Home = expectHome()
                assertTrue(actualHome.isEditingFavorites)
                assertEquals(listOf(inputSecondItem, inputItem), actualHome.favorites)
            }
            assertEquals(
                listOf(FavoriteRecord(key = inputItem.key, position = 0), FavoriteRecord(key = inputSecondItem.key, position = 1)),
                mockFavoriteDao.records
            )
        }
    }

    @Test
    fun `keeps the App List LazyListState when a Favorite is toggled`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                val actualBefore: LauncherUiState.AppList.Browsing = expectAppList()
                viewModel.toggleFavorite(inputItem)
                assertEquals(listOf(inputItem), viewModel.favorites.first { favorites -> favorites.isNotEmpty() })
                expectNoEvents()
                val actualAfter: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertSame(actualBefore.lazyListState, actualAfter.lazyListState)
                assertEquals(actualBefore.groups, actualAfter.groups)
            }
        }
    }

    @Test
    fun `enters Edit Favorites mode in the App List without leaving it`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                assertFalse(expectAppList().isEditingFavorites)
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
            }
        }
    }

    @Test
    fun `exposes Edit Favorites mode during the Rail preview`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.onRailPreview(railIndex(inputListedGroups[1]))
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = inputListedGroups[1], isEditingFavorites = true),
                    expectMostRecentItem()
                )
            }
        }
    }

    @Test
    fun `toggles a Favorite from the App List in Edit Favorites mode`() {
        runTest {
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                val actualBefore: LauncherUiState.AppList.Browsing = expectAppList()
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
                viewModel.toggleFavorite(inputItem)
                assertEquals(listOf(inputItem), viewModel.favorites.first { favorites -> favorites.isNotEmpty() })
                val actualAfter: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertTrue(actualAfter.isEditingFavorites)
                assertSame(actualBefore.lazyListState, actualAfter.lazyListState)
                assertEquals(actualBefore.groups, actualAfter.groups)
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                assertTrue(viewModel.favorites.value.isEmpty())
            }
            assertTrue(mockFavoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `toggles a Favorite from Home in Edit Favorites mode`() {
        runTest {
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = inputItem.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(inputItem)
                val actualHome: LauncherUiState.Home = expectHome()
                assertTrue(actualHome.isEditingFavorites)
                assertTrue(actualHome.favorites.isEmpty())
            }
            assertTrue(mockFavoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `stops observing Favorites when nothing is subscribed`() {
        runTest {
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)

            val actualCollectionJob: Job = viewModel.favorites.launchIn(this)
            runCurrent()
            actualCollectionJob.cancel()
            advanceUntilIdle()

            mockFavoriteDao.add(inputItem.key)
            advanceUntilIdle()

            assertTrue(viewModel.favorites.value.isEmpty())
        }
    }

    @Test
    fun `reorders a Favorite added during Edit Favorites`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 0),
                    FavoriteRecord(key = inputSecondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(inputRailItem)
                advanceUntilIdle()
                assertEquals(listOf(inputItem, inputSecondItem, inputRailItem), viewModel.favorites.value)
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                advanceUntilIdle()
                assertEquals(listOf(inputRailItem, inputItem, inputSecondItem), viewModel.favorites.value)
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                assertFalse(expectHome().isEditingFavorites)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = inputRailItem.key, position = 0),
                    FavoriteRecord(key = inputItem.key, position = 1),
                    FavoriteRecord(key = inputSecondItem.key, position = 2)
                ),
                mockFavoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `exits Edit Favorites in place from the App List when Done is pressed`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 0),
                    FavoriteRecord(key = inputSecondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                assertFalse(expectAppList().isEditingFavorites)
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(
                    listOf(inputSecondItem, inputItem),
                    viewModel.favorites.first { favorites -> favorites.firstOrNull() == inputSecondItem }
                )
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                assertFalse(expectAppList().isEditingFavorites)
                val actualAfter: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertFalse(actualAfter.isEditingFavorites)
                assertEquals(inputListedGroups, actualAfter.groups)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = inputSecondItem.key, position = 0),
                    FavoriteRecord(key = inputItem.key, position = 1)
                ),
                mockFavoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `moves a Favorite in Edit Favorites mode without persisting it`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 0),
                    FavoriteRecord(key = inputSecondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
            }
            assertEquals(
                listOf(FavoriteRecord(key = inputItem.key, position = 0), FavoriteRecord(key = inputSecondItem.key, position = 1)),
                mockFavoriteDao.records
            )
        }
    }

    @Test
    fun `persists the edited order when Edit Favorites mode is done`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = inputItem.key, position = 0),
                    FavoriteRecord(key = inputSecondItem.key, position = 4),
                    FavoriteRecord(key = inputRailItem.key, position = 7)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                expectHome()
                viewModel.finishEditingFavorites()
                val actualHome: LauncherUiState.Home = expectHome()
                assertFalse(actualHome.isEditingFavorites)
                assertEquals(listOf(inputRailItem, inputItem, inputSecondItem), actualHome.favorites)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = inputRailItem.key, position = 0),
                    FavoriteRecord(key = inputItem.key, position = 1),
                    FavoriteRecord(key = inputSecondItem.key, position = 2)
                ),
                mockFavoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `does not open the item menu in Edit Favorites mode`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.toggleFavorite(inputItem)
            advanceUntilIdle()
            viewModel.editFavorites()
            viewModel.menuState.test {
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
                viewModel.openItemMenu(inputItem)
                advanceUntilIdle()
                expectNoEvents()
            }
        }
    }

    @Test
    fun `enters and exits the Favorites Home edit mode`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: LauncherViewModel = createViewModel(inputHomeEditState = inputHomeEditState)

        viewModel.editFavorites()
        assertEquals(HomeEditMode.Favorites, inputHomeEditState.mode.value)

        viewModel.finishEditingFavorites()
        advanceUntilIdle()
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
    }

    @Test
    fun `closes the menu when entering Edit Favorites mode`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.toggleFavorite(inputItem)
            viewModel.menuState.test {
                viewModel.openItemMenu(inputItem)
                viewModel.editFavorites()
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `adds a Rail deeplink to the Favorites`() {
        runTest {
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(inputRailItem)
                assertEquals(listOf(inputRailItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `removes a Favorite whose Launchable no longer exists`() {
        runTest {
            val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = inputGroups)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(
                mockLaunchableRepository = mockLaunchableRepository,
                mockFavoriteDao = mockFavoriteDao
            )
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(inputItem)
                assertEquals(listOf(inputItem), expectHome().favorites)
                mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.Star) + inputListedGroups.drop(1))
                assertTrue(expectHome().favorites.isEmpty())
            }
            advanceUntilIdle()
            assertTrue(mockFavoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `does not restore a Favorite whose Launchable reappears`() {
        runTest {
            val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.Star) + inputListedGroups.drop(1)
            )
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = inputItem.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(
                mockLaunchableRepository = mockLaunchableRepository,
                mockFavoriteDao = mockFavoriteDao
            )
            viewModel.uiState.test {
                assertTrue(expectHome().favorites.isEmpty())
                runCurrent()
                mockLaunchableRepository.setGroups(groups = inputGroups)
                advanceUntilIdle()
                val actualHome: LauncherUiState.Home = viewModel.uiState.value as LauncherUiState.Home
                assertTrue(actualHome.favorites.isEmpty())
            }
            assertTrue(mockFavoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() {
        runTest {
            val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = inputItem.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(
                mockLaunchableRepository = mockLaunchableRepository,
                mockFavoriteDao = mockFavoriteDao
            )
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                mockLaunchableRepository.setGroups(groups = inputGroups)
                assertEquals(listOf(inputItem), expectHome().favorites)
            }
            assertEquals(listOf(FavoriteRecord(key = inputItem.key, position = 0)), mockFavoriteDao.records)
        }
    }

    @Test
    fun `removes a Favorite when no Launchables remain`() {
        runTest {
            val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = inputItem.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(
                mockLaunchableRepository = FakeLaunchableRepository(groups = listOf(LaunchableGroup.Star, inputBuiltinGroup)),
                mockFavoriteDao = mockFavoriteDao
            )
            viewModel.uiState.test {
                expectHome()
                advanceUntilIdle()
            }
            assertTrue(mockFavoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `editFavorites is ignored while a widget is resizing`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Resizing(placeId = 2L))
        val viewModel: LauncherViewModel = createViewModel(inputHomeEditState = inputHomeEditState)

        viewModel.editFavorites()

        assertEquals(HomeEditMode.Resizing(placeId = 2L), inputHomeEditState.mode.value)
        assertFalse(viewModel.isEditingFavorites.value)
    }

    @Test
    fun `editFavorites is ignored while Edit Widgets is active`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: LauncherViewModel = createViewModel(inputHomeEditState = inputHomeEditState)
        viewModel.editFavorites()
        assertEquals(HomeEditMode.Widgets, inputHomeEditState.mode.value)
        assertFalse(viewModel.isEditingFavorites.value)
    }

    @Test
    fun `unstarring a Folder in Edit Favorites keeps the Folder`() = runTest {
        val folder: Folder = Folder(folderId = 42L)
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
            groups = inputGroups,
            folders = listOf(folder)
        )
        val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = folder.key, position = 0))
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = launchableRepository,
            mockFavoriteDao = favoriteDao
        )
        viewModel.uiState.test {
            assertEquals(listOf(folder), expectHome().favorites)
            viewModel.editFavorites()
            assertTrue(expectHome().isEditingFavorites)
            viewModel.toggleFavorite(folder)
            advanceUntilIdle()
            assertTrue(expectHome().favorites.isEmpty())
        }
        assertTrue(launchableRepository.launchableState.value.launchables.any { launchable -> launchable.key == folder.key })
    }

    @Test
    fun `summarises the Folder members with notifications in item order`() = runTest {
        val inputSecondApp: App = createApp(name = "Signal", packageName = "com.example.signal")
        val fixture: FolderSummaryFixture = createFolderSummaryFixture(
            members = listOf(createApp(name = "WhatsApp", packageName = SUMMARY_PACKAGE_NAME), inputSecondApp)
        )
        backgroundScope.launch { fixture.viewModel.folderSummaries.collect { } }
        backgroundScope.launch { fixture.viewModel.folderSummaryLines.collect { } }
        runCurrent()

        mockNotificationRelay.post(
            record = createNotificationRecord(packageName = SUMMARY_PACKAGE_NAME),
            handle = createNotificationHandle()
        )
        runCurrent()
        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp")), fixture.viewModel.folderSummaries.value)

        mockNotificationRelay.post(
            record = createNotificationRecord(key = "second", packageName = inputSecondApp.packageName),
            handle = createNotificationHandle()
        )
        runCurrent()
        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp", "Signal")), fixture.viewModel.folderSummaries.value)
        assertEquals(mapOf(fixture.folder.key to "WhatsApp, Signal"), fixture.viewModel.folderSummaryLines.value)
    }

    @Test
    fun `drops the Folder summary when its last notification is gone`() = runTest {
        val fixture: FolderSummaryFixture = createFolderSummaryFixture(
            members = listOf(createApp(name = "WhatsApp", packageName = SUMMARY_PACKAGE_NAME))
        )
        backgroundScope.launch { fixture.viewModel.folderSummaries.collect { } }
        backgroundScope.launch { fixture.viewModel.folderSummaryLines.collect { } }
        runCurrent()
        mockNotificationRelay.post(
            record = createNotificationRecord(packageName = SUMMARY_PACKAGE_NAME),
            handle = createNotificationHandle()
        )
        runCurrent()
        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp")), fixture.viewModel.folderSummaries.value)

        mockNotificationRelay.remove(key = TEST_NOTIFICATION_KEY)
        runCurrent()

        assertEquals(emptyMap<String, List<String>>(), fixture.viewModel.folderSummaries.value)
        assertEquals(emptyMap<String, String>(), fixture.viewModel.folderSummaryLines.value)
    }

    @Test
    fun `keeps the Folder summary when another App is notified`() = runTest {
        val fixture: FolderSummaryFixture = createFolderSummaryFixture(
            members = listOf(createApp(name = "WhatsApp", packageName = SUMMARY_PACKAGE_NAME))
        )
        backgroundScope.launch { fixture.viewModel.folderSummaries.collect { } }
        runCurrent()
        mockNotificationRelay.post(
            record = createNotificationRecord(packageName = SUMMARY_PACKAGE_NAME),
            handle = createNotificationHandle()
        )
        runCurrent()

        mockNotificationRelay.post(
            record = createNotificationRecord(key = "other", packageName = "com.example.other"),
            handle = createNotificationHandle()
        )
        runCurrent()

        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp")), fixture.viewModel.folderSummaries.value)
    }

    // The production repository with FakeFavoriteDao exercises the resolution and
    // cleanup flow through the ViewModel seam; a fake repository would duplicate it.
    private fun TestScope.createViewModel(
        mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = inputGroups),
        mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(),
        mockFolderRepository: FakeFolderRepository = FakeFolderRepository(),
        inputHomeEditState: HomeEditState = HomeEditState(),
        mockPopupRepository: FakePopupRepository = FakePopupRepository()
    ): LauncherViewModel = LauncherViewModel(
        listOffsets = ListOffsets(startPx = START_OFFSET_PX, endPx = END_OFFSET_PX),
        launcherItemRepo = mockLaunchableRepository,
        prefsRepo = mockPreferencesRepository,
        // Eagerly resolved so ViewModel tests need no runCurrent per test; dispatcher-sensitive
        // await semantics are covered by repository tests.
        favoritesRepo = createEagerFavoritesRepository(
            favoriteDao = mockFavoriteDao,
            launchableRepository = mockLaunchableRepository
        ),
        folderRepo = mockFolderRepository,
        popupRepo = mockPopupRepository,
        notificationSource = createNotificationSource(mockPopupRepository = mockPopupRepository),
        homeEditState = inputHomeEditState
    )

    private fun TestScope.createNotificationSource(mockPopupRepository: FakePopupRepository): INotificationSource =
        createTestNotificationSource(
            scope = backgroundScope,
            popupRepository = mockPopupRepository,
            relay = mockNotificationRelay
        )

    private suspend fun TestScope.createFolderSummaryFixture(members: List<App>): FolderSummaryFixture {
        val folder: Folder = Folder(folderId = FAVORITE_FOLDER_ID)
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        mockPopupRepository.setItems(ownerKey = folder.key, items = members)
        return FolderSummaryFixture(
            folder = folder,
            viewModel = createViewModel(
                mockFolderRepository = FakeFolderRepository(initialFolders = listOf(folder)),
                mockPopupRepository = mockPopupRepository
            )
        )
    }

    private inline fun <reified T : LauncherUiState> checkState(actualState: LauncherUiState): T {
        assertTrue("Expected ${T::class.simpleName} but was $actualState", actualState is T)
        return actualState as T
    }

    private inline fun <reified T : LauncherUiState> TurbineTestContext<LauncherUiState>.expectState(): T =
        checkState(expectMostRecentItem())

    private suspend inline fun <reified T : LauncherUiState> TurbineTestContext<LauncherUiState>.awaitState(): T = checkState(awaitItem())

    private fun TurbineTestContext<LauncherUiState>.expectHome(): LauncherUiState.Home = expectState()

    private fun TurbineTestContext<LauncherUiState>.expectAppList(): LauncherUiState.AppList.Browsing = expectState()

    private fun railIndex(group: LaunchableGroup): Int = inputGroups.indexOf(group)

    private fun listedItem(index: Int): Launchable = inputListedGroups[index].items.first()

    private fun createLaunchable(name: String): Launchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail://$name"
    )
}

private data class FolderSummaryFixture(val folder: Folder, val viewModel: LauncherViewModel)
