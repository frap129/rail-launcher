package feature.launcher

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.favorites.FavoriteRecord
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[1], isEditingFavorites = false), expectMostRecentItem())
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
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[2], isEditingFavorites = false), expectMostRecentItem())
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
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[1], isEditingFavorites = false), expectMostRecentItem())
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
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[0], isEditingFavorites = false), expectMostRecentItem())
                viewModel.onRailMoved(LaunchableGroup.Star)
                expectHome()
                viewModel.onRailMoved(listedGroups[2])
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[2], isEditingFavorites = false), expectMostRecentItem())
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
                assertEquals(LauncherUiState.AppList.Scrolling(group = listedGroups[1], isEditingFavorites = false), expectMostRecentItem())
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

    @Test
    fun `adds a Launchable to the Favorites shown on Home`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertTrue(expectHome().favorites.isEmpty())
                viewModel.toggleFavorite(item)
                assertEquals(listOf(item), expectHome().favorites)
            }
        }
    }

    @Test
    fun `adds Favorites in the order they were added`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(item)
                viewModel.toggleFavorite(secondItem)
                assertEquals(listOf(item, secondItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `orders Favorites by their stored position`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = item.key, position = 1),
                    FavoriteRecord(key = secondItem.key, position = 0)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                assertEquals(listOf(secondItem, item), expectHome().favorites)
            }
        }
    }

    @Test
    fun `removes a Launchable from the Favorites`() {
        runTest {
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(item)
                assertEquals(listOf(item), expectHome().favorites)
                viewModel.toggleFavorite(item)
                assertTrue(expectHome().favorites.isEmpty())
            }
            assertTrue(favoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `shows the same Edit Favorites menu in the App List`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.onRailReleased(listedGroups[0])
            viewModel.menuState.test {
                viewModel.openItemMenu(item)
                assertEquals(LauncherMenuState.Menu(item = item), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `enters Edit Favorites mode from the menu`() {
        runTest {
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = item.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                assertFalse(expectHome().isEditingFavorites)
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
            }
        }
    }

    @Test
    fun `initializes the edited Favorites when entering Edit Favorites from the App List`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = item.key, position = 0),
                    FavoriteRecord(key = secondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[0])
                expectAppList()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                viewModel.onBack()
                assertEquals(listOf(secondItem, item), expectHome().favorites)
            }
        }
    }

    @Test
    fun `keeps the App List LazyListState when a Favorite is toggled`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[0])
                val before: LauncherUiState.AppList.Browsing = expectAppList()
                viewModel.toggleFavorite(item)
                assertEquals(listOf(item), viewModel.favorites.first { favorites -> favorites.isNotEmpty() })
                expectNoEvents()
                val after: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertSame(before.lazyListState, after.lazyListState)
                assertEquals(before.groups, after.groups)
            }
        }
    }

    @Test
    fun `enters Edit Favorites mode in the App List without leaving it`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[0])
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
                viewModel.onRailTouched(listedGroups[1])
                assertEquals(
                    LauncherUiState.AppList.Scrolling(group = listedGroups[1], isEditingFavorites = true),
                    expectMostRecentItem()
                )
            }
        }
    }

    @Test
    fun `toggles a Favorite from the App List in Edit Favorites mode`() {
        runTest {
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[0])
                val before: LauncherUiState.AppList.Browsing = expectAppList()
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
                viewModel.toggleFavorite(item)
                assertEquals(listOf(item), viewModel.favorites.first { favorites -> favorites.isNotEmpty() })
                val after: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertTrue(after.isEditingFavorites)
                assertSame(before.lazyListState, after.lazyListState)
                assertEquals(before.groups, after.groups)
                viewModel.toggleFavorite(item)
                advanceUntilIdle()
                assertTrue(viewModel.favorites.value.isEmpty())
            }
            assertTrue(favoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `toggles a Favorite from Home in Edit Favorites mode`() {
        runTest {
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = item.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(item)
                val home: LauncherUiState.Home = expectHome()
                assertTrue(home.isEditingFavorites)
                assertTrue(home.favorites.isEmpty())
            }
            assertTrue(favoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `stops collecting Favorites when nothing is subscribed`() {
        runTest {
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)

            val collectionJob: Job = viewModel.favorites.launchIn(this)
            runCurrent()
            assertEquals(1, favoriteDao.activeCollectorCount)

            collectionJob.cancel()
            advanceUntilIdle()
            assertEquals(0, favoriteDao.activeCollectorCount)
        }
    }

    @Test
    fun `reorders a Favorite added during Edit Favorites`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val railItem: Launchable = listedGroups.last().items.first()
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = item.key, position = 0),
                    FavoriteRecord(key = secondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(railItem)
                advanceUntilIdle()
                assertEquals(listOf(item, secondItem, railItem), viewModel.favorites.value)
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                advanceUntilIdle()
                assertEquals(listOf(railItem, item, secondItem), viewModel.favorites.value)
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                assertFalse(expectHome().isEditingFavorites)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = railItem.key, position = 0),
                    FavoriteRecord(key = item.key, position = 1),
                    FavoriteRecord(key = secondItem.key, position = 2)
                ),
                favoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `exits Edit Favorites in place from the App List when Done is pressed`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = item.key, position = 0),
                    FavoriteRecord(key = secondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(listedGroups[0])
                assertFalse(expectAppList().isEditingFavorites)
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(
                    listOf(secondItem, item),
                    viewModel.favorites.first { favorites -> favorites.firstOrNull() == secondItem }
                )
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                assertFalse(expectAppList().isEditingFavorites)
                val after: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertFalse(after.isEditingFavorites)
                assertEquals(listedGroups, after.groups)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = secondItem.key, position = 0),
                    FavoriteRecord(key = item.key, position = 1)
                ),
                favoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `moves a Favorite in Edit Favorites mode without persisting it`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = item.key, position = 0),
                    FavoriteRecord(key = secondItem.key, position = 1)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(listOf(secondItem, item), expectHome().favorites)
            }
            assertEquals(
                listOf(FavoriteRecord(key = item.key, position = 0), FavoriteRecord(key = secondItem.key, position = 1)),
                favoriteDao.records
            )
        }
    }

    @Test
    fun `persists the edited order when Edit Favorites mode is done`() {
        runTest {
            val secondItem: Launchable = listedGroups[1].items.first()
            val railItem: Launchable = listedGroups.last().items.first()
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(
                    FavoriteRecord(key = item.key, position = 0),
                    FavoriteRecord(key = secondItem.key, position = 4),
                    FavoriteRecord(key = railItem.key, position = 7)
                )
            )
            val viewModel: LauncherViewModel = createViewModel(favoriteDao = favoriteDao)
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                expectHome()
                viewModel.finishEditingFavorites()
                val home: LauncherUiState.Home = expectHome()
                assertFalse(home.isEditingFavorites)
                assertEquals(listOf(railItem, item, secondItem), home.favorites)
            }
            assertEquals(
                listOf(
                    FavoriteRecord(key = railItem.key, position = 0),
                    FavoriteRecord(key = item.key, position = 1),
                    FavoriteRecord(key = secondItem.key, position = 2)
                ),
                favoriteDao.records.sortedBy { it.position }
            )
        }
    }

    @Test
    fun `does not open the item menu in Edit Favorites mode`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.toggleFavorite(item)
            advanceUntilIdle()
            viewModel.editFavorites()
            viewModel.menuState.test {
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
                viewModel.openItemMenu(item)
                advanceUntilIdle()
                expectNoEvents()
            }
        }
    }

    @Test
    fun `closes the menu when entering Edit Favorites mode`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.toggleFavorite(item)
            viewModel.menuState.test {
                viewModel.openItemMenu(item)
                viewModel.editFavorites()
                assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `adds a Rail deeplink to the Favorites`() {
        runTest {
            val railItem: Launchable = listedGroups.last().items.first()
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(railItem)
                assertEquals(listOf(railItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `removes a Favorite whose Launchable no longer exists`() {
        runTest {
            val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = groups)
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
            val viewModel: LauncherViewModel = createViewModel(
                launchableRepository = launchableRepository,
                favoriteDao = favoriteDao
            )
            viewModel.uiState.test {
                expectHome()
                viewModel.toggleFavorite(item)
                assertEquals(listOf(item), expectHome().favorites)
                launchableRepository.setGroups(groups = listOf(LaunchableGroup.Star) + listedGroups.drop(1))
                assertTrue(expectHome().favorites.isEmpty())
            }
            assertTrue(favoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `does not restore a Favorite whose Launchable reappears`() {
        runTest {
            val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.Star) + listedGroups.drop(1)
            )
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = item.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(
                launchableRepository = launchableRepository,
                favoriteDao = favoriteDao
            )
            viewModel.uiState.test {
                assertTrue(expectHome().favorites.isEmpty())
                launchableRepository.setGroups(groups = groups)
                advanceUntilIdle()
                val home: LauncherUiState.Home = viewModel.uiState.value as LauncherUiState.Home
                assertTrue(home.favorites.isEmpty())
            }
            assertTrue(favoriteDao.records.isEmpty())
        }
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() {
        runTest {
            val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = item.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(
                launchableRepository = launchableRepository,
                favoriteDao = favoriteDao
            )
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                launchableRepository.setGroups(groups = groups)
                assertEquals(listOf(item), expectHome().favorites)
            }
            assertEquals(listOf(FavoriteRecord(key = item.key, position = 0)), favoriteDao.records)
        }
    }

    @Test
    fun `removes a Favorite when no Launchables remain`() {
        runTest {
            val favoriteDao: FakeFavoriteDao = FakeFavoriteDao(
                initialRecords = listOf(FavoriteRecord(key = item.key, position = 0))
            )
            val viewModel: LauncherViewModel = createViewModel(
                launchableRepository = FakeLaunchableRepository(groups = emptyList()),
                favoriteDao = favoriteDao
            )
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                advanceUntilIdle()
            }
            assertTrue(favoriteDao.records.isEmpty())
        }
    }

    // The production repository with FakeFavoriteDao exercises the resolution and
    // cleanup flow through the ViewModel seam; a fake repository would duplicate it.
    private fun createViewModel(
        launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = groups),
        favoriteDao: FakeFavoriteDao = FakeFavoriteDao()
    ): LauncherViewModel = LauncherViewModel(
        listOffsets = ListOffsets(startPx = START_OFFSET_PX, endPx = END_OFFSET_PX),
        launcherItemRepo = launchableRepository,
        prefsRepo = preferencesRepository,
        favoritesRepo = IFavoritesRepositoryImpl(favoriteDao = favoriteDao, launchableRepository = launchableRepository)
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
