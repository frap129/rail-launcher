package feature.launcher

import android.content.IntentSender
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.favorites.IFavoritesRepository
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.prefs.IPreferencesRepository
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.Settings
import core.data.profiles.IPrivateSpaceLock
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.IProfileSource
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.QuietRequestResult
import core.data.room.AppDatabase
import core.data.search.ISearchHistoryRepository
import core.data.search.ISearchHistoryRepositoryImpl
import core.data.search.IShortcutSearchSource
import core.data.search.SearchHistoryRecord
import core.data.search.SearchResult
import core.data.search.SearchShortcut
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.FavoritesRepositoryFixture
import core.data.testing.MainDispatcherRule
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.PopupRepositoryState
import core.data.testing.ProfileSourceState
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createBuiltinGroup
import core.data.testing.createClockReading
import core.data.testing.createClockSourceMock
import core.data.testing.createFavoritesRepositoryFixture
import core.data.testing.createFolderRepositoryMock
import core.data.testing.createInMemoryDatabase
import core.data.testing.createIntentSender
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createNotificationHandle
import core.data.testing.createNotificationRecord
import core.data.testing.createPausedWorkApp
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateApp
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createPrivateSpaceStatusMock
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcut
import core.data.testing.createShortcutSearchSourceMock
import core.data.testing.createStandaloneShortcutRepositoryMock
import core.data.testing.createTestNotificationSource
import core.data.time.IClockSource
import core.ui.GroupMark
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val START_OFFSET_PX: Int = 200
private const val END_OFFSET_PX: Int = 800
private const val RENAMED_NAME: String = "Renamed Launchable"
private const val FAVORITE_FOLDER_ID: Long = 42L
private const val SUMMARY_PACKAGE_NAME: String = "com.example.summary"
private const val TEST_NOW_EPOCH_MS: Long = 1_791_720_000_000L

// The matching tests assert only the rows they are about; the row tests pass Settings with the
// web and Play Store rows on, and the Home-press test asserts them with the production defaults.
private val inputSearchRowsOff: Settings = Settings(searchWebRow = false, searchPlayStoreRow = false)
private val inputAutoLaunchRows: Settings = inputSearchRowsOff.copy(searchAutoLaunch = true)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LauncherViewModelTest {
    private companion object {
        private val searchHistoryDispatcher: TestDispatcher = UnconfinedTestDispatcher()
        private val searchHistoryDatabase: Lazy<AppDatabase> = lazy {
            createInMemoryDatabase(
                context = RuntimeEnvironment.getApplication(),
                queryContext = searchHistoryDispatcher
            )
        }
        private val searchHistoryRepository: Lazy<ISearchHistoryRepository> = lazy {
            ISearchHistoryRepositoryImpl(
                searchHistoryDao = searchHistoryDatabase.value.getSearchHistoryDao(),
                clockSource = createClockSourceMock(reading = createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS))),
                dispatcher = searchHistoryDispatcher
            )
        }

        @JvmStatic
        @AfterClass
        fun closeSearchHistory() {
            if (searchHistoryDatabase.isInitialized()) {
                searchHistoryDatabase.value.close()
            }
        }
    }

    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val mockClockSource: IClockSource = createClockSourceMock(
        reading = createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS))
    )

    private val mockSearchHistoryRepository: ISearchHistoryRepository
        get() = searchHistoryRepository.value

    private val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(settings = inputSearchRowsOff)
    private val mockProfileSource: IProfileSource = createProfileSourceMock()
    private val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
    private val inputBuiltinGroup: LaunchableGroup.Builtin = createBuiltinGroup()
    private val inputListedGroups: List<LaunchableGroup.Listed> = listOf(
        LaunchableGroup.InitialGroup(label = "A", items = listOf(createRailLaunchable("Alpha"))),
        LaunchableGroup.InitialGroup(label = "B", items = listOf(createRailLaunchable("Beta"))),
        inputBuiltinGroup
    )
    private val inputGroups: List<LaunchableGroup> = listOf(LaunchableGroup.Star) + inputListedGroups
    private val inputItem: Launchable = inputListedGroups.first().items.first()
    private val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
    private val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputLaunchableState)
    private val mockFavoritesFixture: FavoritesRepositoryFixture = createFavoritesRepositoryFixture()
    private val inputFolders: MutableStateFlow<List<Folder>> = MutableStateFlow(emptyList())
    private val mockFolderRepository: IFolderRepository = createFolderRepositoryMock(inputFolders)
    private val mockPopupRepository: IPopupRepository = createPopupRepositoryMock()
    private val notificationRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()

    @Before
    fun clearSearchHistory() {
        searchHistoryDatabase.value.clearAllTables()
    }

    @Test
    fun `shows Loading until the Launchables are loaded`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = inputGroups, isLoaded = false)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `shows Home when the Launchables are loaded and there are none`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = emptyList())
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
            }
        }
    }

    @Test
    fun `shows Home with the Star and Builtin groups when there are no Launchables`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = listOf(LaunchableGroup.Star, inputBuiltinGroup))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                assertTrue(actualHome.favorites.isEmpty())
            }
            viewModel.railItems.test {
                assertEquals(listOf(GroupMark.Label("★"), GroupMark.Label("⌂")), expectMostRecentItem())
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
    fun `exposes the Rail items`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.railItems.test {
                assertEquals(
                    listOf(GroupMark.Label("★"), GroupMark.Label("A"), GroupMark.Label("B"), GroupMark.Label("⌂")),
                    expectMostRecentItem()
                )
            }
        }
    }

    @Test
    fun `exposes the Scrub edges setting`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(
                    settings = Settings(scrubEdges = ScrubEdges.START)
                )
            )

            viewModel.scrubEdges.test {
                assertEquals(ScrubEdges.START, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `exposes the Rail side setting`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(
                    settings = Settings(railSide = RailSide.START)
                )
            )

            viewModel.railSide.test {
                assertEquals(RailSide.START, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `maps Rail indexes through the Star, digit, initial and builtin groups`() {
        runTest {
            val inputDigitGroup: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(
                label = "#",
                items = listOf(createRailLaunchable("1Password"))
            )
            inputLaunchableState.value = createLaunchableState(groups = listOf(LaunchableGroup.Star, inputDigitGroup) + inputListedGroups)
            val viewModel: LauncherViewModel = createViewModel()
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
                viewModel.onHomeIntent(wasResumed = true)
                awaitState<LauncherUiState.Home>()
                runCurrent()
                expectNoEvents()
                viewModel.onRailReleased(index = 1)
                awaitState<LauncherUiState.AppList.Browsing>()
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
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
                viewModel.onBack()
                advanceUntilIdle()
                mockFavoritesFixture.state.favorites.value = listOf(inputSecondItem, inputItem)
                val actualHome: LauncherUiState.Home = expectHome()
                assertFalse(actualHome.isEditingFavorites)
                assertEquals(listOf(inputSecondItem, inputItem), actualHome.favorites)
            }
            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.reorder(listOf(inputSecondItem, inputItem)) }
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
                viewModel.onHomeIntent(wasResumed = true)
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
                viewModel.onHomeIntent(wasResumed = true)
                val actualSecondHome: LauncherUiState.Home = expectHome()
                assertNotSame(actualFirstHome.lazyListState, actualSecondHome.lazyListState)
                assertEquals(0, actualSecondHome.lazyListState.firstVisibleItemIndex)
            }
        }
    }

    @Test
    fun `opens the menu of the Launchable that was long pressed`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.openItemMenu(inputItem)
                assertEquals(
                    LauncherMenuState.Menu(item = inputItem, isSearchSuggestion = false, isStandaloneShortcut = false),
                    expectMostRecentItem()
                )
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
    fun `removes a Standalone shortcut and closes its menu`() = runTest {
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.site")
        val mockStandaloneShortcutRepository: IStandaloneShortcutRepository = createStandaloneShortcutRepositoryMock()
        val viewModel: LauncherViewModel = createViewModel(
            mockStandaloneShortcutRepository = mockStandaloneShortcutRepository
        )

        viewModel.removeShortcut(inputShortcut)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockStandaloneShortcutRepository.remove(keys = listOf(inputShortcut.key)) }
        assertEquals(LauncherMenuState.Closed, viewModel.menuState.value)
    }

    @Test
    fun `renames a Launchable through the preferences repository`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.setItemName(inputItem, RENAMED_NAME)
            runCurrent()
            verify(exactly = 1) { mockPreferencesRepository.setItemName(item = inputItem, name = RENAMED_NAME) }
        }
    }

    @Test
    fun `logs a private Launchable rename failure without its package or key`() {
        runTest {
            val inputPrivateApp: App = createPrivateApp()
            every {
                mockPreferencesRepository.setItemName(any(), any())
            } throws RuntimeException("Could not rename the Launchable")
            val viewModel: LauncherViewModel = createViewModel()
            val logRecorder: TimberLogRecorder = TimberLogRecorder()

            logRecorder.recordSuspending {
                viewModel.setItemName(inputPrivateApp, RENAMED_NAME)
                runCurrent()
            }

            logRecorder.assertLoggedWithout(inputPrivateApp.key, inputPrivateApp.packageName)
        }
    }

    @Test
    fun `shows the menu again after a Launchable is renamed`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.menuState.test {
                viewModel.setItemName(inputItem, RENAMED_NAME)
                assertEquals(
                    LauncherMenuState.Menu(item = inputItem, isSearchSuggestion = false, isStandaloneShortcut = false),
                    expectMostRecentItem()
                )
            }
        }
    }

    @Test
    fun `adds a Launchable to the Favorites`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.toggleFavorite(inputItem)
            advanceUntilIdle()

            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputItem) }
        }
    }

    @Test
    fun `adds Favorites in the order they were added`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.toggleFavorite(inputItem)
            viewModel.toggleFavorite(inputSecondItem)
            advanceUntilIdle()

            coVerifyOrder {
                mockFavoritesFixture.mockFavoritesRepository.add(inputItem)
                mockFavoritesFixture.mockFavoritesRepository.add(inputSecondItem)
            }
        }
    }

    @Test
    fun `shows Favorites in the repository order`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.favorites.value = listOf(inputSecondItem, inputItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `removes a Launchable from the Favorites`() {
        runTest {
            mockFavoritesFixture.state.favorites.value = listOf(inputItem)
            mockFavoritesFixture.state.isFavorite.value = true
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.toggleFavorite(inputItem)
            advanceUntilIdle()

            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.remove(inputItem) }
        }
    }

    @Test
    fun `toggles a Favorite twice in a row`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.toggleFavorite(inputItem)
            advanceUntilIdle()
            mockFavoritesFixture.state.favorites.value = listOf(inputItem)
            mockFavoritesFixture.state.isFavorite.value = true
            viewModel.toggleFavorite(inputItem)
            advanceUntilIdle()

            coVerifyOrder {
                mockFavoritesFixture.mockFavoritesRepository.add(inputItem)
                mockFavoritesFixture.mockFavoritesRepository.remove(inputItem)
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
                    assertEquals(
                        LauncherMenuState.Menu(item = inputItem, isSearchSuggestion = false, isStandaloneShortcut = false),
                        expectMostRecentItem()
                    )
                }
            }
        }
    }

    @Test
    fun `enters Edit Favorites mode from the menu`() {
        runTest {
            mockFavoritesFixture.state.favorites.value = listOf(inputItem)
            val viewModel: LauncherViewModel = createViewModel()
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
    fun `exposes the Home edit mode while editing Favorites`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.homeEditMode.test {
                assertEquals(HomeEditMode.None, expectMostRecentItem())
                viewModel.editFavorites()
                assertEquals(HomeEditMode.Favorites, awaitItem())
                viewModel.finishEditingFavorites()
                assertEquals(HomeEditMode.None, awaitItem())
            }
        }
    }

    @Test
    fun `returns to Home still editing Favorites when Back is pressed in the App List`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem)
            val viewModel: LauncherViewModel = createViewModel()
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
                listOf(inputItem, inputSecondItem),
                mockFavoritesFixture.state.favorites.value
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
                advanceUntilIdle()
                coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputItem) }
                expectNoEvents()
                assertAppListStateRetained(before = actualBefore, after = viewModel.uiState.value)
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
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(railIndex(inputListedGroups[0]))
                val actualBefore: LauncherUiState.AppList.Browsing = expectAppList()
                viewModel.editFavorites()
                assertTrue(expectAppList().isEditingFavorites)
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputItem) }
                val actualAfter: LauncherUiState.AppList.Browsing = viewModel.uiState.value as LauncherUiState.AppList.Browsing
                assertTrue(actualAfter.isEditingFavorites)
                assertAppListStateRetained(before = actualBefore, after = actualAfter)
                mockFavoritesFixture.state.favorites.value = listOf(inputItem)
                mockFavoritesFixture.state.isFavorite.value = true
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                mockFavoritesFixture.state.favorites.value = emptyList()
                advanceUntilIdle()
                assertTrue(viewModel.favorites.value.isEmpty())
            }
            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.remove(inputItem) }
        }
    }

    @Test
    fun `toggles a Favorite from Home in Edit Favorites mode`() {
        runTest {
            mockFavoritesFixture.state.favorites.value = listOf(inputItem)
            mockFavoritesFixture.state.isFavorite.value = true
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                mockFavoritesFixture.state.favorites.value = emptyList()
                val actualHome: LauncherUiState.Home = expectHome()
                assertTrue(actualHome.isEditingFavorites)
                assertTrue(actualHome.favorites.isEmpty())
            }
            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.remove(inputItem) }
        }
    }

    @Test
    fun `stops observing Favorites when nothing is subscribed`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel()

            val actualCollectionJob: Job = viewModel.favorites.launchIn(this)
            runCurrent()
            actualCollectionJob.cancel()
            advanceUntilIdle()

            mockFavoritesFixture.state.favorites.value = listOf(inputItem)
            advanceUntilIdle()

            assertTrue(viewModel.favorites.value.isEmpty())
        }
    }

    @Test
    fun `reorders a Favorite added during Edit Favorites`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(inputRailItem)
                advanceUntilIdle()
                mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem, inputRailItem)
                advanceUntilIdle()
                assertEquals(listOf(inputItem, inputSecondItem, inputRailItem), viewModel.favorites.value)
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                advanceUntilIdle()
                assertEquals(listOf(inputRailItem, inputItem, inputSecondItem), viewModel.favorites.value)
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                assertFalse(expectHome().isEditingFavorites)
            }
            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputRailItem) }
            coVerify(exactly = 1) {
                mockFavoritesFixture.mockFavoritesRepository.reorder(listOf(inputRailItem, inputItem, inputSecondItem))
            }
        }
    }

    @Test
    fun `exits Edit Favorites in place from the App List when Done is pressed`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem)
            val viewModel: LauncherViewModel = createViewModel()
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
            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.reorder(listOf(inputSecondItem, inputItem)) }
        }
    }

    @Test
    fun `moves a Favorite in Edit Favorites mode without persisting it`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
            }
            coVerify(exactly = 0) { mockFavoritesFixture.mockFavoritesRepository.reorder(any()) }
        }
    }

    @Test
    fun `persists the edited order when Edit Favorites mode is done`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem, inputRailItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                expectHome()
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                mockFavoritesFixture.state.favorites.value = listOf(inputRailItem, inputItem, inputSecondItem)
                val actualHome: LauncherUiState.Home = expectHome()
                assertFalse(actualHome.isEditingFavorites)
                assertEquals(listOf(inputRailItem, inputItem, inputSecondItem), actualHome.favorites)
            }
            coVerify(exactly = 1) {
                mockFavoritesFixture.mockFavoritesRepository.reorder(listOf(inputRailItem, inputItem, inputSecondItem))
            }
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
    fun `tapping a paused work App opens the paused prompt instead of launching`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.pausedPrompt.test {
            assertNull(awaitItem())
            val actualCanLaunch: Boolean = viewModel.requestLaunch(inputPausedApp)
            assertFalse(actualCanLaunch)
            assertEquals(inputPausedApp, awaitItem())
        }
    }

    @Test
    fun `tapping an available App allows launching without a prompt`() = runTest {
        val inputApp: App = createApp(name = "Gmail", packageName = "com.example.gmail")
        val viewModel: LauncherViewModel = createViewModel()

        assertTrue(viewModel.requestLaunch(inputApp))
        assertNull(viewModel.pausedPrompt.value)
    }

    @Test
    fun `tapping a paused work Shortcut opens the paused prompt and turns work apps on`() = runTest {
        val inputWorkProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work, isQuiet = true)
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.work",
            profile = inputWorkProfile.user,
            longLabel = "Work shortcut",
            profileInfo = inputWorkProfile
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputShortcut)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.pausedPrompt.test {
            assertNull(awaitItem())
            assertFalse(viewModel.requestLaunch(inputShortcut))
            assertEquals(inputShortcut, awaitItem())
        }
        viewModel.turnOnPausedLaunchable()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockProfileSource.requestQuiet(user = inputWorkProfile.user, quiet = false) }
    }

    @Test
    fun `turning on a paused App asks its Profile to leave quiet mode`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.requestLaunch(inputPausedApp)
        advanceUntilIdle()
        viewModel.turnOnPausedLaunchable()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockProfileSource.requestQuiet(user = inputPausedApp.profile, quiet = false) }
    }

    @Test
    fun `turning on a Profile by serial asks it to leave quiet mode`() = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputProfileState: ProfileSourceState = ProfileSourceState().apply {
            usersBySerial[inputProfile.serial] = inputProfile.user
            serialsByUser[inputProfile.user] = inputProfile.serial
        }
        val mockProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val viewModel: LauncherViewModel = createViewModel(mockProfileSource = mockProfileSource)

        viewModel.turnOnProfile(profileSerial = inputProfile.serial)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockProfileSource.requestQuiet(user = inputProfile.user, quiet = false) }
    }

    @Test
    fun `long-pressing a paused work App still opens its menu`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.menuState.test {
            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            viewModel.openItemMenu(inputPausedApp)
            assertEquals(
                LauncherMenuState.Menu(item = inputPausedApp, isSearchSuggestion = false, isStandaloneShortcut = false),
                expectMostRecentItem()
            )
        }
    }

    @Test
    fun `clears the paused prompt when the App becomes available`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.pausedPrompt.test {
            assertNull(awaitItem())
            viewModel.requestLaunch(inputPausedApp)
            assertEquals(inputPausedApp, awaitItem())
            inputState.value =
                createLoadedLaunchableState(listOf(inputPausedApp.copy(profileInfo = inputPausedApp.profileInfo.copy(isQuiet = false))))
            assertNull(awaitItem())
        }
    }

    @Test
    fun `does not reopen the paused prompt when the Profile pauses again after resuming`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.pausedPrompt.test {
            assertNull(awaitItem())
            viewModel.requestLaunch(inputPausedApp)
            assertEquals(inputPausedApp, awaitItem())
            val inputAvailableApp: App = inputPausedApp.copy(profileInfo = inputPausedApp.profileInfo.copy(isQuiet = false))
            inputState.value = createLoadedLaunchableState(listOf(inputAvailableApp))
            assertNull(awaitItem())
            inputState.value = createLoadedLaunchableState(listOf(inputPausedApp))
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `does not reopen the paused prompt when the Profile resumes and pauses while unsubscribed`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )
        assertFalse(viewModel.requestLaunch(inputPausedApp))
        val inputAvailableApp: App = inputPausedApp.copy(profileInfo = inputPausedApp.profileInfo.copy(isQuiet = false))
        inputState.value = createLoadedLaunchableState(listOf(inputAvailableApp))
        advanceUntilIdle()
        inputState.value = createLoadedLaunchableState(listOf(inputPausedApp))
        advanceUntilIdle()

        viewModel.pausedPrompt.test {
            assertNull(expectMostRecentItem())
        }
    }

    @Test
    fun `does not reopen the paused prompt when a stale paused tap arrives after the App became available`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputAvailableApp: App = inputPausedApp.copy(profileInfo = inputPausedApp.profileInfo.copy(isQuiet = false))
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.pausedPrompt.test {
            assertNull(awaitItem())
            inputState.value = createLoadedLaunchableState(listOf(inputAvailableApp))
            advanceUntilIdle()
            val actualCanLaunch: Boolean = viewModel.requestLaunch(inputPausedApp)
            assertFalse(actualCanLaunch)
            assertNull(viewModel.pausedPrompt.value)
            inputState.value = createLoadedLaunchableState(listOf(inputPausedApp))
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `dismissing the paused prompt closes it`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputPausedApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.pausedPrompt.test {
            assertNull(awaitItem())
            viewModel.requestLaunch(inputPausedApp)
            assertEquals(inputPausedApp, awaitItem())
            viewModel.dismissPausedPrompt()
            assertNull(awaitItem())
        }
    }

    @Test
    fun `tapping the Locked Private Space row requests unlock and stays Locked while awaiting credentials`() = runTest {
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Locked
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile)).apply {
            quietRequestResult = QuietRequestResult.AwaitingCredential
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState),
            mockProfileSource = mockPrivateProfileSource,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(
                status = MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true))
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(inputGroups.lastIndex)
            assertTrue(expectAppList().groups.filterIsInstance<LaunchableGroup.PrivateSpace>().single().isLocked)
            viewModel.requestPrivateSpaceUnlock()
            advanceUntilIdle()
            val actualGroup: LaunchableGroup.PrivateSpace = (viewModel.uiState.value as LauncherUiState.AppList.Browsing)
                .groups.filterIsInstance<LaunchableGroup.PrivateSpace>().single()
            assertTrue(actualGroup.isLocked)
            assertTrue(actualGroup.items.isEmpty())
        }
        coVerify(exactly = 1) { mockPrivateProfileSource.requestQuiet(user = inputPrivateProfile.user, quiet = false) }
    }

    @Test
    fun `locking Private Space requests the lock before the Profile reports itself quiet`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        val inputPrivateSpaceLock: IPrivateSpaceLock = createPrivateSpaceLock(profileSource = mockPrivateProfileSource)
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState),
            mockProfileSource = mockPrivateProfileSource,
            privateSpaceLock = inputPrivateSpaceLock,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(
                status = MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
            )
        )

        viewModel.lockPrivateSpace()
        assertTrue(inputPrivateSpaceLock.isLockRequested.value)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockPrivateProfileSource.requestQuiet(user = inputPrivateProfile.user, quiet = true) }
    }

    @Test
    fun `clears the lock request when the system refuses the lock`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile)).apply {
            quietRequestResult = QuietRequestResult.Failed
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val inputPrivateSpaceLock: IPrivateSpaceLock = createPrivateSpaceLock(profileSource = mockPrivateProfileSource)
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState),
            mockProfileSource = mockPrivateProfileSource,
            privateSpaceLock = inputPrivateSpaceLock,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(
                status = MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
            )
        )

        viewModel.lockPrivateSpace()
        advanceUntilIdle()

        assertFalse(inputPrivateSpaceLock.isLockRequested.value)
        coVerify(exactly = 1) { mockPrivateProfileSource.requestQuiet(user = inputPrivateProfile.user, quiet = true) }
    }

    @Test
    fun `shows the private Apps when the Private Space becomes available`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Locked
        )
        val inputUnlockedGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(inputGroups.lastIndex)
            assertTrue(expectAppList().groups.filterIsInstance<LaunchableGroup.PrivateSpace>().single().isLocked)
            inputState.value = createLaunchableState(groups = inputUnlockedGroups)
            val actualGroup: LaunchableGroup.PrivateSpace = expectAppList().groups
                .filterIsInstance<LaunchableGroup.PrivateSpace>().single()
            assertFalse(actualGroup.isLocked)
            assertEquals(listOf(inputPrivateApp), actualGroup.items)
        }
    }

    @Test
    fun `hides the private Apps when the Private Space locks elsewhere`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
        )
        val inputLockedGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Locked
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(inputGroups.lastIndex)
            assertEquals(listOf(inputPrivateApp), expectAppList().groups.filterIsInstance<LaunchableGroup.PrivateSpace>().single().items)
            inputState.value = createLaunchableState(groups = inputLockedGroups)
            val actualGroup: LaunchableGroup.PrivateSpace = expectAppList().groups
                .filterIsInstance<LaunchableGroup.PrivateSpace>().single()
            assertTrue(actualGroup.isLocked)
            assertTrue(actualGroup.items.isEmpty())
        }
    }

    @Test
    fun `drops the Private Space group and Rail item when the Launchables leave the container out`() = runTest {
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Locked
        )
        val inputHiddenGroups: List<LaunchableGroup> = inputGroups.dropLast(1)
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )
        val inputRailItems: List<GroupMark> = expectedRailItems(privateSpace = GroupMark.PrivateSpaceShield)
        val expectedHiddenRailItems: List<GroupMark> = inputRailItems.dropLast(1)

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(inputGroups.lastIndex)
            awaitAppListMatching { groups -> groups.any { group -> group is LaunchableGroup.PrivateSpace } }
            inputState.value = createLaunchableState(groups = inputHiddenGroups)
            awaitAppListMatching { groups -> groups.none { group -> group is LaunchableGroup.PrivateSpace } }
            inputState.value = createLaunchableState(groups = inputGroups)
            awaitAppListMatching { groups -> groups.any { group -> group is LaunchableGroup.PrivateSpace } }
        }
        viewModel.railItems.test {
            assertEquals(inputRailItems, awaitRailItems(inputRailItems))
            inputState.value = createLaunchableState(groups = inputHiddenGroups)
            assertEquals(expectedHiddenRailItems, awaitRailItems(expectedHiddenRailItems))
            inputState.value = createLaunchableState(groups = inputGroups)
            assertEquals(inputRailItems, awaitRailItems(inputRailItems))
        }
    }

    @Test
    fun `keeps the Private Space group last on the Rail`() = runTest {
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Locked
        )
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLaunchableState(groups = inputGroups))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.railItems.test {
            assertEquals(expectedRailItems(privateSpace = GroupMark.PrivateSpaceShield), expectMostRecentItem())
        }
    }

    @Test
    fun `offers the Private Space settings action while the space is Unlocked`() = runTest {
        val expectedIntentSender: IntentSender = createIntentSender(context = RuntimeEnvironment.getApplication())
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile)).apply {
            privateSpaceSettingsIntent = expectedIntentSender
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val viewModel: LauncherViewModel = createViewModel(
            mockProfileSource = mockPrivateProfileSource,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(
                status = MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
            )
        )

        viewModel.privateSpaceSettingsIntent.test {
            assertEquals(expectedIntentSender, awaitIntentSender())
        }
    }

    @Test
    fun `does not query the Private Space settings action again while the space stays Unlocked`() = runTest {
        val expectedIntentSender: IntentSender = createIntentSender(context = RuntimeEnvironment.getApplication())
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile)).apply {
            privateSpaceSettingsIntent = expectedIntentSender
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val inputStatus: MutableStateFlow<PrivateSpaceStatus> =
            MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
        val viewModel: LauncherViewModel = createViewModel(
            mockProfileSource = mockPrivateProfileSource,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(status = inputStatus)
        )

        viewModel.privateSpaceSettingsIntent.test {
            assertEquals(expectedIntentSender, awaitIntentSender())
            inputStatus.value = PrivateSpaceStatus(profile = inputPrivateProfile.copy(serial = 1L))
            advanceUntilIdle()
            expectNoEvents()
        }
        coVerify(exactly = 1) { mockPrivateProfileSource.privateSpaceSettingsIntent() }
    }

    @Test
    fun `offers no Private Space settings action without a system intent`() = runTest {
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        val viewModel: LauncherViewModel = createViewModel(
            mockProfileSource = mockPrivateProfileSource,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(
                status = MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
            )
        )

        viewModel.privateSpaceSettingsIntent.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `drops the Private Space settings action when the space locks without a reload`() = runTest {
        val expectedIntentSender: IntentSender = createIntentSender(context = RuntimeEnvironment.getApplication())
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile)).apply {
            privateSpaceSettingsIntent = expectedIntentSender
        }
        val mockPrivateProfileSource: IProfileSource = createProfileSourceMock(state = inputProfileState)
        val inputStatus: MutableStateFlow<PrivateSpaceStatus> =
            MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
        val viewModel: LauncherViewModel = createViewModel(
            mockProfileSource = mockPrivateProfileSource,
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(status = inputStatus)
        )

        viewModel.privateSpaceSettingsIntent.test {
            assertEquals(expectedIntentSender, awaitIntentSender())
            inputStatus.value = PrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `adds a Rail deeplink to the Favorites`() {
        runTest {
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.toggleFavorite(inputRailItem)
            advanceUntilIdle()

            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputRailItem) }
        }
    }

    @Test
    fun `drops a Favorite from Home when the repository stops emitting it`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem, inputSecondItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(listOf(inputItem, inputSecondItem), expectHome().favorites)
                mockFavoritesFixture.state.favorites.value = listOf(inputSecondItem)
                assertEquals(listOf(inputSecondItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `does not restore a Favorite the repository stopped emitting`() {
        runTest {
            val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(
                createLaunchableState(groups = listOf(LaunchableGroup.Star) + inputListedGroups.drop(1))
            )
            val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputState)
            val viewModel: LauncherViewModel = createViewModel(mockLaunchableRepository = mockLaunchableRepository)
            viewModel.uiState.test {
                assertTrue(expectHome().favorites.isEmpty())
                runCurrent()
                inputState.value = createLaunchableState(groups = inputGroups)
                advanceUntilIdle()
                val actualHome: LauncherUiState.Home = viewModel.uiState.value as LauncherUiState.Home
                assertTrue(actualHome.favorites.isEmpty())
            }
        }
    }

    @Test
    fun `does not remove Favorites while the Launchables are loading`() {
        runTest {
            val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(
                createLaunchableState(groups = emptyList(), isLoaded = false)
            )
            val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputState)
            mockFavoritesFixture.state.favorites.value = listOf(inputItem)
            val viewModel: LauncherViewModel = createViewModel(mockLaunchableRepository = mockLaunchableRepository)
            viewModel.uiState.test {
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                inputState.value = createLaunchableState(groups = inputGroups)
                assertEquals(listOf(inputItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `keeps the repository Favorite order when the Launchables reload`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.favorites.value = listOf(inputSecondItem, inputItem)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
                inputLaunchableState.value = createLaunchableState(groups = listOf(LaunchableGroup.Star) + inputListedGroups.reversed())
                advanceUntilIdle()
                val actualHome: LauncherUiState.Home = viewModel.uiState.value as LauncherUiState.Home
                assertEquals(listOf(inputSecondItem, inputItem), actualHome.favorites)
            }
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
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = inputGroups, folders = listOf(folder))
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputState)
        mockFavoritesFixture.state.favorites.value = listOf(folder)
        mockFavoritesFixture.state.isFavorite.value = true
        val viewModel: LauncherViewModel = createViewModel(mockLaunchableRepository = mockLaunchableRepository)
        viewModel.uiState.test {
            assertEquals(listOf(folder), expectHome().favorites)
            viewModel.editFavorites()
            assertTrue(expectHome().isEditingFavorites)
            viewModel.toggleFavorite(folder)
            advanceUntilIdle()
            mockFavoritesFixture.state.favorites.value = emptyList()
            assertTrue(expectHome().favorites.isEmpty())
        }
        coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.remove(folder) }
        assertTrue(mockLaunchableRepository.launchableState.value.launchables.any { launchable -> launchable.key == folder.key })
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

        notificationRelay.post(
            record = createNotificationRecord(packageName = SUMMARY_PACKAGE_NAME),
            handle = createNotificationHandle()
        )
        runCurrent()
        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp")), fixture.viewModel.folderSummaries.value)

        notificationRelay.post(
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
        notificationRelay.post(
            record = createNotificationRecord(packageName = SUMMARY_PACKAGE_NAME),
            handle = createNotificationHandle()
        )
        runCurrent()
        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp")), fixture.viewModel.folderSummaries.value)

        notificationRelay.remove(key = TEST_NOTIFICATION_KEY)
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
        notificationRelay.post(
            record = createNotificationRecord(packageName = SUMMARY_PACKAGE_NAME),
            handle = createNotificationHandle()
        )
        runCurrent()

        notificationRelay.post(
            record = createNotificationRecord(key = "other", packageName = "com.example.other"),
            handle = createNotificationHandle()
        )
        runCurrent()

        assertEquals(mapOf(fixture.folder.key to listOf("WhatsApp")), fixture.viewModel.folderSummaries.value)
    }

    @Test
    fun `opens Search from the App List`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()

            viewModel.openSearch()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals("", actualSearch.query)
            assertTrue(actualSearch.results.isEmpty())
        }
    }

    @Test
    fun `updates the Search results for the current query`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            expectSearch()

            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)

            viewModel.setSearchQuery("be")
            assertEquals(launchableResults(listedItem(1)), expectSearch().results)
        }
    }

    @Test
    fun `opens Search with a starting query`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch(startingQuery = "al")

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals("al", actualSearch.query)
            assertEquals(launchableResults(inputItem), actualSearch.results)
        }
    }

    @Test
    fun `typing on Home opens Search with the typed character`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()

            viewModel.onTypedCharacter(character = "a", isPopupOpen = false)

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals("a", actualSearch.query)
            assertEquals(launchableResults(inputItem), actualSearch.results)
        }
    }

    @Test
    fun `typing on the App List does not open Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()

            viewModel.onTypedCharacter(character = "a", isPopupOpen = false)
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `rapid typed characters append to the query in order`() = runTest {
        val inputCalendar: App = createApp(name = "Calendar", packageName = "com.example.calendar")
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputCalendar))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()

            viewModel.onTypedCharacter(character = "c", isPopupOpen = false)
            assertEquals("c", expectSearch().query)
            viewModel.onTypedCharacter(character = "a", isPopupOpen = false)
            assertEquals("ca", expectSearch().query)
            viewModel.onTypedCharacter(character = "l", isPopupOpen = false)

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals("cal", actualSearch.query)
            assertEquals(launchableResults(inputCalendar), actualSearch.results)
        }
    }

    @Test
    fun `typing a space appends to an open Search query`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onTypedCharacter(character = "a", isPopupOpen = false)
            assertEquals("a", expectSearch().query)

            viewModel.onTypedCharacter(character = " ", isPopupOpen = false)

            assertEquals("a ", expectSearch().query)
        }
    }

    @Test
    fun `typing while a Pop-up is open does not open Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()

            viewModel.onTypedCharacter(character = "a", isPopupOpen = true)
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `typing during a Home edit mode does not open Search`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: LauncherViewModel = createViewModel(inputHomeEditState = inputHomeEditState)

        viewModel.uiState.test {
            expectHome()

            viewModel.onTypedCharacter(character = "a", isPopupOpen = false)
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `Go opens the Top result and resets Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals(launchableResults(inputItem), actualSearch.results)
            assertEquals(SearchResult.LaunchableResult(inputItem), actualSearch.topResult)

            val actualTopResult: SearchResult? = viewModel.onSearchGo()
            assertEquals(SearchResult.LaunchableResult(inputItem), actualTopResult)
            assertTrue(viewModel.requestLaunch(inputItem))

            viewModel.onLaunched(inputItem)

            expectAppList()
            viewModel.openSearch()
            assertEquals("", expectSearch().query)
        }
    }

    @Test
    fun `Go returns the Calculator row when no Launchable matches`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("12*7")

            val actualSearch: LauncherUiState.Search = expectSearch()
            val expectedResult: SearchResult = SearchResult.CalculatorRow(expression = "12*7", value = "84")
            assertEquals(listOf(expectedResult), actualSearch.results)
            assertEquals(expectedResult, actualSearch.topResult)
            assertEquals(expectedResult, viewModel.onSearchGo())
        }
    }

    @Test
    fun `an App named 99 stays the Top result`() = runTest {
        val inputApp: App = createApp(name = "99", packageName = "com.example.ninety-nine")
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputApp))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("99")

            val actualSearch: LauncherUiState.Search = expectSearch()
            val expectedTopResult: SearchResult = SearchResult.LaunchableResult(inputApp)
            assertEquals(listOf(expectedTopResult), actualSearch.results)
            assertEquals(expectedTopResult, actualSearch.topResult)
        }
    }

    @Test
    fun `Go returns the typed-URL row when no Launchable matches`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings())
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("example.com")

            val expectedResult: SearchResult = SearchResult.TypedUrlRow(url = "https://example.com")
            assertEquals(expectedResult, expectSearch().topResult)
            assertEquals(expectedResult, viewModel.onSearchGo())
        }
    }

    @Test
    fun `Go returns the web search row when nothing else matches`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings())
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("rail launcher")

            val expectedResult: SearchResult = SearchResult.WebSearchRow(url = "https://www.google.com/search?q=rail%20launcher")
            assertEquals(expectedResult, expectSearch().topResult)
            assertEquals(expectedResult, viewModel.onSearchGo())
        }
    }

    @Test
    fun `Go returns the lone Play Store row`() = runTest {
        val inputSettings: Settings = Settings(searchCalculatorRow = false, searchWebRow = false)
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSettings)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("12*7")

            val expectedResult: SearchResult = SearchResult.PlayStoreRow(
                marketUrl = "market://search?q=12%2A7",
                webUrl = "https://play.google.com/store/search?q=12%2A7&c=apps"
            )
            assertEquals(expectedResult, expectSearch().topResult)
            assertEquals(expectedResult, viewModel.onSearchGo())
        }
    }

    @Test
    fun `the Search row settings remove their rows`() = runTest {
        val inputSettings: Settings = Settings(searchCalculatorRow = false, searchWebRow = false, searchPlayStoreRow = false)
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSettings)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("example.com")

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertTrue(actualSearch.results.isEmpty())
            assertNull(actualSearch.topResult)
        }
    }

    @Test
    fun `auto-launches the only Search result`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchAutoLaunch = true))
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")

            val actualResult: SearchResult.LaunchableResult = requireNotNull(expectSearch().autoLaunchResult)
            assertEquals(SearchResult.LaunchableResult(inputItem), actualResult)
            assertTrue(viewModel.shouldAutoLaunch(actualResult))
        }
    }

    @Test
    fun `does not auto-launch when the setting is off`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `does not auto-launch either of two results`() = runTest {
        inputLaunchableState.value = createLoadedLaunchableState(
            listOf(createRailLaunchable("Alpha"), createRailLaunchable("Alfred"))
        )
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchAutoLaunch = true))
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `does not auto-launch while the Calculator row is showing`() = runTest {
        val inputApp: App = createApp(name = "12*7", packageName = "com.example.calculator")
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputApp))
        val viewModel: LauncherViewModel = createViewModelWithSettings(inputAutoLaunchRows)
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("12*7")

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `does not auto-launch a Paused result`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputPausedApp))
        val viewModel: LauncherViewModel = createViewModelWithSettings(inputAutoLaunchRows)
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("gmail")

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `does not auto-launch a disabled result`() = runTest {
        val inputDisabledShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.site", isEnabled = false)
        inputLaunchableState.value = createLaunchableState(groups = inputGroups, folders = listOf(inputDisabledShortcut))
        val viewModel: LauncherViewModel = createViewModelWithSettings(inputAutoLaunchRows)
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("shortcut")

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `does not auto-launch before the Shortcuts load`() = runTest {
        val inputShortcuts: MutableSharedFlow<List<SearchShortcut>> = MutableSharedFlow()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(searchAutoLaunch = true)),
            mockShortcutSearchSource = createShortcutSearchSourceMock(shortcuts = inputShortcuts)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            advanceUntilIdle()
            assertNull(expectSearch().autoLaunchResult)

            inputShortcuts.emit(emptyList())

            assertEquals(SearchResult.LaunchableResult(inputItem), awaitAutoLaunch())
        }
    }

    @Test
    fun `does not auto-launch when a backspace leaves a single match`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchAutoLaunch = true))
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("alpha x")
            viewModel.setSearchQuery("alpha")

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `auto-launches when typing forward after a backspace`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchAutoLaunch = true))
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("alx")
            viewModel.setSearchQuery("al")
            assertNull(expectSearch().autoLaunchResult)

            viewModel.setSearchQuery("alp")

            val actualResult: SearchResult.LaunchableResult = requireNotNull(awaitAutoLaunch())
            assertEquals(SearchResult.LaunchableResult(inputItem), actualResult)
            assertTrue(viewModel.shouldAutoLaunch(actualResult))
        }
    }

    @Test
    fun `does not auto-launch the same result again after a recreation`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchAutoLaunch = true))
        val expectedResult: SearchResult.LaunchableResult = SearchResult.LaunchableResult(inputItem)

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(expectedResult, expectSearch().autoLaunchResult)
        }
        assertTrue(viewModel.shouldAutoLaunch(expectedResult))

        viewModel.uiState.test {
            assertEquals(expectedResult, expectSearch().autoLaunchResult)
        }
        assertFalse(viewModel.shouldAutoLaunch(expectedResult))
    }

    @Test
    fun `does not auto-launch the same result again after a re-emission`() = runTest {
        val inputSettings: Settings = Settings(searchAutoLaunch = true)
        val inputSettingsFlow: MutableStateFlow<Settings> = MutableStateFlow(inputSettings)
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSettings, settingsFlow = inputSettingsFlow)
        )
        val expectedResult: SearchResult.LaunchableResult = SearchResult.LaunchableResult(inputItem)

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(expectedResult, expectSearch().autoLaunchResult)
            assertTrue(viewModel.shouldAutoLaunch(expectedResult))

            inputSettingsFlow.value = inputSettings.copy(searchBottomAnchoredResults = true)

            val actualReemittedResult: SearchResult.LaunchableResult = awaitAutoLaunch()
            assertEquals(expectedResult, actualReemittedResult)
            assertFalse(viewModel.shouldAutoLaunch(actualReemittedResult))
        }
    }

    @Test
    fun `does not auto-launch a Search suggestion`() = runTest {
        val fixture: SuggestionFixture = createSuggestionFixture(settings = Settings(searchAutoLaunch = true))
        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()

            assertNull(expectSearch().autoLaunchResult)
        }
    }

    @Test
    fun `exposes Bottom-anchored results from the setting`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchBottomAnchoredResults = true))

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()

            assertTrue(expectSearch().isBottomAnchored)
        }
    }

    @Test
    fun `opening an external Search row closes and resets Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings())
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("example.com")
            assertEquals(
                SearchResult.TypedUrlRow(url = "https://example.com"),
                expectSearch().topResult
            )

            viewModel.closeSearch()

            expectHome()
            viewModel.openSearch()
            assertEquals("", expectSearch().query)
        }
    }

    @Test
    fun `Back clears the query before closing Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)

            viewModel.onBack()

            val actualCleared: LauncherUiState.Search = expectSearch()
            assertEquals("", actualCleared.query)
            assertTrue(actualCleared.results.isEmpty())

            viewModel.onBack()

            expectAppList()
        }
    }

    @Test
    fun `Home closes Search and resets the query`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)

            viewModel.onHomeIntent(wasResumed = true)

            expectHome()
            viewModel.openSearch()
            assertEquals("", expectSearch().query)
        }
    }

    @Test
    fun `a launch closes and resets Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")

            viewModel.onLaunched(inputItem)

            expectAppList()
            viewModel.openSearch()
            assertEquals("", expectSearch().query)
        }
    }

    @Test
    fun `a Search launch records it in Search history`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)

            viewModel.onLaunched(inputItem)

            expectAppList()
        }
        advanceUntilIdle()

        val actualRecord: SearchHistoryRecord? = mockSearchHistoryRepository.history.first().singleOrNull { record ->
            record.key == inputItem.key
        }
        assertEquals(1, actualRecord?.launchCount)
        assertEquals(TEST_NOW_EPOCH_MS, actualRecord?.lastLaunchedAt)
        assertFalse(actualRecord?.isRemoved ?: true)
    }

    @Test
    fun `a launch outside Search does not record Search history`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
        }
        viewModel.onLaunched(inputItem)
        advanceUntilIdle()

        assertTrue(mockSearchHistoryRepository.history.first().isEmpty())
    }

    @Test
    fun `Go does nothing while Search suggestions are shown`() = runTest {
        val fixture: SuggestionFixture = createSuggestionFixture()

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals(launchableResults(fixture.app), actualSearch.results)
            assertNull(actualSearch.topResult)
            assertNull(fixture.viewModel.onSearchGo())
        }
    }

    @Test
    fun `removing a Search suggestion from its menu hides it and keeps its launch count`() = runTest {
        val fixture: SuggestionFixture = createSuggestionFixture()
        mockSearchHistoryRepository.recordLaunch(key = fixture.app.key)
        advanceUntilIdle()
        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()
            assertEquals(launchableResults(fixture.app), expectSearch().results)
        }

        fixture.viewModel.menuState.test {
            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            fixture.viewModel.openItemMenu(fixture.app)
            val actualMenu: LauncherMenuState.Menu = expectMostRecentItem() as LauncherMenuState.Menu
            assertTrue(actualMenu.isSearchSuggestion)

            fixture.viewModel.removeSearchSuggestion(fixture.app)

            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
        }
        advanceUntilIdle()

        val actualRecord: SearchHistoryRecord = mockSearchHistoryRepository.history.first().single()
        assertTrue(actualRecord.isRemoved)
        assertEquals(1, actualRecord.launchCount)
        fixture.viewModel.uiState.test {
            assertTrue(expectSearch().results.isEmpty())
        }
    }

    @Test
    fun `a typed Search result's menu is not a Search suggestion`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()
        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)
        }

        viewModel.menuState.test {
            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            viewModel.openItemMenu(inputItem)
            val actualMenu: LauncherMenuState.Menu = expectMostRecentItem() as LauncherMenuState.Menu
            assertFalse(actualMenu.isSearchSuggestion)
        }
    }

    @Test
    fun `shows Search suggestions before typing when they are on`() = runTest {
        val fixture: SuggestionFixture = createSuggestionFixture()

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertTrue(actualSearch.isShowingSuggestions)
            assertEquals(launchableResults(fixture.app), actualSearch.results)
        }
    }

    @Test
    fun `hides Search suggestions when the setting is off`() = runTest {
        val fixture: SuggestionFixture = createSuggestionFixture(settings = Settings(searchSuggestions = false))

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertFalse(actualSearch.isShowingSuggestions)
            assertTrue(actualSearch.results.isEmpty())
        }
    }

    @Test
    fun `does not open Search while editing Favorites`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.editFavorites()
            assertTrue(expectHome().isEditingFavorites)

            viewModel.openSearch()
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `entering Edit Favorites from Search closes Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)

            viewModel.editFavorites()

            assertTrue(expectHome().isEditingFavorites)
            viewModel.finishEditingFavorites()
            advanceUntilIdle()
            viewModel.openSearch()
            assertEquals("", expectSearch().query)
        }
    }

    @Test
    fun `Go on a paused result opens the paused prompt instead of launching`() = runTest {
        val inputPausedApp: App = createPausedWorkApp()
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputPausedApp))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("gmail")
            assertEquals(launchableResults(inputPausedApp), expectSearch().results)

            val actualTopResult: SearchResult.LaunchableResult = requireNotNull(viewModel.onSearchGo()).launchableResult()
            assertEquals(SearchResult.LaunchableResult(inputPausedApp), actualTopResult)
            assertFalse(viewModel.requestLaunch(actualTopResult.launchable))
            val actualRemainingSearch: LauncherUiState.Search = checkState(viewModel.uiState.value)
            assertEquals("gmail", actualRemainingSearch.query)
        }
    }

    @Test
    fun `Go on a disabled Shortcut result never launches it`() = runTest {
        val inputDisabledShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.site", isEnabled = false)
        inputLaunchableState.value = createLaunchableState(groups = inputGroups, folders = listOf(inputDisabledShortcut))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("shortcut")
            assertEquals(launchableResults(inputDisabledShortcut), expectSearch().results)

            val actualTopResult: SearchResult.LaunchableResult = requireNotNull(viewModel.onSearchGo()).launchableResult()
            assertEquals(SearchResult.LaunchableResult(inputDisabledShortcut), actualTopResult)
            assertTrue(actualTopResult.launchable.isDisabled)
            assertFalse(viewModel.requestLaunch(actualTopResult.launchable))
            val actualRemainingSearch: LauncherUiState.Search = checkState(viewModel.uiState.value)
            assertEquals("shortcut", actualRemainingSearch.query)
        }
    }

    @Test
    fun `the first Search state for a two character query contains a Shortcut result`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "ne")

            val actualSearch: LauncherUiState.Search = awaitFirstSearch()
            val expectedResult: ShortcutLaunchable = fixture.shortcut.copy(publisherName = fixture.publisher.name)
            assertEquals(launchableResults(expectedResult), actualSearch.results)
            assertEquals(SearchResult.LaunchableResult(expectedResult), actualSearch.topResult)
        }
    }

    @Test
    fun `Go runs a Shortcut Top result when no Launchable matches`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()
        val expectedResult: ShortcutLaunchable = fixture.shortcut.copy(publisherName = fixture.publisher.name)

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "new")

            val actualSearch: LauncherUiState.Search = awaitFirstSearch()
            assertEquals(launchableResults(expectedResult), actualSearch.results)
            assertEquals(SearchResult.LaunchableResult(expectedResult), actualSearch.topResult)

            val actualTopResult: SearchResult? = fixture.viewModel.onSearchGo()
            assertEquals(SearchResult.LaunchableResult(expectedResult), actualTopResult)
            assertTrue(fixture.viewModel.requestLaunch(expectedResult))
            fixture.viewModel.onLaunched(expectedResult)

            expectHome()
        }
        advanceUntilIdle()

        val actualRecord: SearchHistoryRecord? = mockSearchHistoryRepository.history.first()
            .singleOrNull { record -> record.key == fixture.shortcut.key }
        assertEquals(1, actualRecord?.launchCount)
    }

    @Test
    fun `leaves a Hidden Shortcut out of the Search results`() = runTest {
        val inputPublisher: App = createApp(name = "Messages", packageName = "com.example.messages")
        val inputVisibleShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPublisher.packageName,
            shortLabel = "New message",
            longLabel = "New message"
        )
        val inputHiddenShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPublisher.packageName,
            shortcutId = SECOND_SHORTCUT_ID,
            shortLabel = "New call",
            longLabel = "New call"
        )
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputPublisher))
        val inputShortcuts: MutableStateFlow<List<SearchShortcut>> = MutableStateFlow(
            listOf(
                SearchShortcut(shortcut = inputVisibleShortcut, publisherName = inputPublisher.name, isHidden = false),
                SearchShortcut(shortcut = inputHiddenShortcut, publisherName = inputPublisher.name, isHidden = true)
            )
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockShortcutSearchSource = createShortcutSearchSourceMock(shortcuts = inputShortcuts)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch(startingQuery = "new")

            val actualSearch: LauncherUiState.Search = awaitFirstSearch()
            val expectedResult: ShortcutLaunchable = inputVisibleShortcut.copy(publisherName = inputPublisher.name)
            assertEquals(launchableResults(expectedResult), actualSearch.results)
        }
    }

    @Test
    fun `long-pressing a Shortcut Search result opens its item menu`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()
        val expectedResult: ShortcutLaunchable = fixture.shortcut.copy(publisherName = fixture.publisher.name)

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "new")
            assertEquals(launchableResults(expectedResult), awaitFirstSearch().results)
        }

        fixture.viewModel.menuState.test {
            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())

            fixture.viewModel.openItemMenu(expectedResult)

            val actualMenu: LauncherMenuState.Menu = expectMostRecentItem() as LauncherMenuState.Menu
            assertEquals(fixture.shortcut, actualMenu.item)
            assertFalse(actualMenu.isStandaloneShortcut)
        }
    }

    @Test
    fun `Search hides a private App while its Private Space is Locked`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(privateSpace = LaunchableGroup.PrivateSpace.Locked)
        inputLaunchableState.value = createLaunchableState(groups = inputGroups, folders = listOf(inputPrivateApp))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.openSearch()
        viewModel.setSearchQuery("secret")

        viewModel.uiState.test {
            assertTrue(expectSearch().results.isEmpty())
        }
    }

    @Test
    fun `Search shows a private App while its Private Space is Unlocked`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val inputGroups: List<LaunchableGroup> = createPrivateSpaceGroups(
            privateSpace = LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
        )
        inputLaunchableState.value = createLaunchableState(groups = inputGroups)
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.openSearch()
        viewModel.setSearchQuery("secret")

        viewModel.uiState.test {
            assertEquals(launchableResults(inputPrivateApp), expectSearch().results)
        }
    }

    @Test
    fun `Search finds an App held in a Folder`() = runTest {
        val inputMember: App = createApp(name = "Tetris", packageName = "com.example.tetris")
        inputLaunchableState.value = createLaunchableState(groups = inputGroups, folders = listOf(inputMember))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.openSearch()
        viewModel.setSearchQuery("tetris")

        viewModel.uiState.test {
            assertEquals(launchableResults(inputMember), expectSearch().results)
        }
    }

    @Test
    fun `Home opens Search when the Home-press entry is on`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnHomePress = true))

        viewModel.uiState.test {
            expectHome()

            viewModel.onHomeIntent(wasResumed = true)

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals("", actualSearch.query)
        }
    }

    @Test
    fun `Home does not open Search when the Home-press entry is off`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnHomePress = false))

        viewModel.uiState.test {
            val actualFirstHome: LauncherUiState.Home = expectHome()

            viewModel.onHomeIntent(wasResumed = true)

            val actualSecondHome: LauncherUiState.Home = expectHome()
            assertNotSame(actualFirstHome.lazyListState, actualSecondHome.lazyListState)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `Home intent from another app does not open Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnHomePress = true))

        viewModel.uiState.test {
            val actualFirstHome: LauncherUiState.Home = expectHome()

            viewModel.onHomeIntent(wasResumed = false)

            val actualSecondHome: LauncherUiState.Home = expectHome()
            assertNotSame(actualFirstHome.lazyListState, actualSecondHome.lazyListState)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `Home closes an open Search when the Home-press entry is on`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnHomePress = true))

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            val expectedRows: List<SearchResult> = launchableResults(inputItem) + listOf(
                SearchResult.WebSearchRow(url = "https://www.google.com/search?q=al"),
                SearchResult.PlayStoreRow(
                    marketUrl = "market://search?q=al",
                    webUrl = "https://play.google.com/store/search?q=al&c=apps"
                )
            )
            assertEquals(expectedRows, expectSearch().results)

            viewModel.onHomeIntent(wasResumed = true)

            expectHome()
        }
    }

    @Test
    fun `Swipe up opens Search when its entry is on`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnSwipeUp = true))

        viewModel.uiState.test {
            expectHome()

            viewModel.onHomeSwipeUp()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals("", actualSearch.query)
        }
    }

    @Test
    fun `Swipe up does not open Search when its entry is off`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnSwipeUp = false))

        viewModel.uiState.test {
            expectHome()

            viewModel.onHomeSwipeUp()
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `Swipe up during a Home edit mode does not open Search`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: LauncherViewModel = createViewModelWithSettings(
            settings = Settings(openSearchOnSwipeUp = true),
            inputHomeEditState = inputHomeEditState
        )

        viewModel.uiState.test {
            expectHome()

            viewModel.onHomeSwipeUp()
            runCurrent()

            assertFalse(viewModel.isSearchOpen())
            expectNoEvents()
        }
    }

    // Creates the ViewModel and lets its eager settings subscription read the saved entries.
    private fun TestScope.createViewModelWithSettings(
        settings: Settings,
        inputHomeEditState: HomeEditState = HomeEditState()
    ): LauncherViewModel = createViewModel(
        mockPreferencesRepository = createPreferencesRepositoryMock(settings = settings),
        inputHomeEditState = inputHomeEditState
    ).also { advanceUntilIdle() }

    private fun TestScope.createSuggestionFixture(settings: Settings = Settings()): SuggestionFixture {
        val inputRecentApp: App = createApp(
            name = "Recent",
            packageName = "com.example.recent",
            firstInstallTime = TEST_NOW_EPOCH_MS - 60_000L
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(
                state = MutableStateFlow(createLoadedLaunchableState(launchables = listOf(inputRecentApp)))
            ),
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = settings)
        )
        return SuggestionFixture(app = inputRecentApp, viewModel = viewModel)
    }

    private fun TestScope.createShortcutSearchFixture(): ShortcutSearchFixture {
        val inputPublisher: App = createApp(name = "Messages", packageName = "com.example.messages")
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = inputPublisher.packageName,
            shortLabel = "New message",
            longLabel = "New message"
        )
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputPublisher))
        val inputShortcuts: MutableStateFlow<List<SearchShortcut>> = MutableStateFlow(
            listOf(SearchShortcut(shortcut = inputShortcut, publisherName = inputPublisher.name, isHidden = false))
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockShortcutSearchSource = createShortcutSearchSourceMock(shortcuts = inputShortcuts)
        )
        return ShortcutSearchFixture(publisher = inputPublisher, shortcut = inputShortcut, viewModel = viewModel)
    }

    private fun TestScope.createViewModel(
        mockLaunchableRepository: ILaunchableRepository = this@LauncherViewModelTest.mockLaunchableRepository,
        mockFavoritesRepository: IFavoritesRepository = this@LauncherViewModelTest.mockFavoritesFixture.mockFavoritesRepository,
        mockFolderRepository: IFolderRepository = this@LauncherViewModelTest.mockFolderRepository,
        inputHomeEditState: HomeEditState = HomeEditState(),
        mockPopupRepository: IPopupRepository = this@LauncherViewModelTest.mockPopupRepository,
        mockProfileSource: IProfileSource = this@LauncherViewModelTest.mockProfileSource,
        mockPreferencesRepository: IPreferencesRepository = this@LauncherViewModelTest.mockPreferencesRepository,
        privateSpaceLock: IPrivateSpaceLock = createPrivateSpaceLock(profileSource = mockProfileSource),
        mockPrivateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatusMock(),
        mockStandaloneShortcutRepository: IStandaloneShortcutRepository = createStandaloneShortcutRepositoryMock(),
        mockSearchHistoryRepository: ISearchHistoryRepository = this@LauncherViewModelTest.mockSearchHistoryRepository,
        mockClockSource: IClockSource = this@LauncherViewModelTest.mockClockSource,
        mockShortcutSearchSource: IShortcutSearchSource = createShortcutSearchSourceMock()
    ): LauncherViewModel = LauncherViewModel(
        listOffsets = ListOffsets(startPx = START_OFFSET_PX, endPx = END_OFFSET_PX),
        launcherItemRepo = mockLaunchableRepository,
        prefsRepo = mockPreferencesRepository,
        favoritesRepo = mockFavoritesRepository,
        folderRepo = mockFolderRepository,
        popupRepo = mockPopupRepository,
        notificationSource = createNotificationSource(mockPopupRepository = mockPopupRepository),
        homeEditState = inputHomeEditState,
        profileSource = mockProfileSource,
        privateSpaceStatus = mockPrivateSpaceStatus,
        privateSpaceLock = privateSpaceLock,
        standaloneShortcutRepository = mockStandaloneShortcutRepository,
        searchHistoryRepository = mockSearchHistoryRepository,
        clockSource = mockClockSource,
        shortcutSearchSource = mockShortcutSearchSource
    )

    private fun createPrivateSpaceGroups(privateSpace: LaunchableGroup.PrivateSpace): List<LaunchableGroup> = listOf(
        LaunchableGroup.Star,
        LaunchableGroup.InitialGroup(label = "A", items = listOf(createRailLaunchable("Alpha"))),
        inputBuiltinGroup,
        privateSpace
    )

    private fun expectedRailItems(privateSpace: GroupMark): List<GroupMark> = listOf(
        GroupMark.Label(LaunchableGroup.Star.label),
        GroupMark.Label("A"),
        GroupMark.Label(inputBuiltinGroup.label),
        privateSpace
    )

    // The settled action, skipping the StateFlow's initial null when it wins the subscription race.
    private suspend fun TurbineTestContext<IntentSender?>.awaitIntentSender(): IntentSender {
        var actualIntentSender: IntentSender? = awaitItem()
        while (actualIntentSender == null) {
            actualIntentSender = awaitItem()
        }
        return actualIntentSender
    }

    private suspend fun TurbineTestContext<LauncherUiState>.awaitAppListMatching(
        predicate: (List<LaunchableGroup.Listed>) -> Boolean
    ): LauncherUiState.AppList.Browsing {
        while (true) {
            val actualState: LauncherUiState = awaitItem()
            if (actualState is LauncherUiState.AppList.Browsing && predicate(actualState.groups)) {
                return actualState
            }
        }
    }

    private suspend fun <T> TurbineTestContext<T>.awaitFirstMatching(predicate: (T) -> Boolean): T {
        while (true) {
            val actualItem: T = awaitItem()
            if (predicate(actualItem)) {
                return actualItem
            }
        }
    }

    private suspend fun TurbineTestContext<LauncherUiState>.awaitAutoLaunch(): SearchResult.LaunchableResult {
        val actualState: LauncherUiState.Search = awaitFirstMatching { state ->
            state is LauncherUiState.Search && state.autoLaunchResult != null
        } as LauncherUiState.Search
        return requireNotNull(actualState.autoLaunchResult)
    }

    private suspend fun TurbineTestContext<LauncherUiState>.awaitFirstSearch(): LauncherUiState.Search =
        awaitFirstMatching { state -> state is LauncherUiState.Search } as LauncherUiState.Search

    private suspend fun TurbineTestContext<List<GroupMark>>.awaitRailItems(expected: List<GroupMark>): List<GroupMark> {
        var actualRailItems: List<GroupMark> = awaitItem()
        while (actualRailItems != expected) {
            actualRailItems = awaitItem()
        }
        return actualRailItems
    }

    private fun TestScope.createNotificationSource(mockPopupRepository: IPopupRepository): INotificationSource =
        createTestNotificationSource(
            context = RuntimeEnvironment.getApplication(),
            scope = backgroundScope,
            mockPopupRepository = mockPopupRepository,
            relay = notificationRelay
        )

    private suspend fun TestScope.createFolderSummaryFixture(members: List<App>): FolderSummaryFixture {
        val folder: Folder = Folder(folderId = FAVORITE_FOLDER_ID)
        val mockPopupRepositoryState: PopupRepositoryState = PopupRepositoryState(
            notificationsEnabled = MutableStateFlow(true),
            itemsByOwner = MutableStateFlow(
                mapOf(folder.key to members.map { member -> PopupItem.Launchable(launchable = member) })
            )
        )
        inputFolders.value = listOf(folder)
        return FolderSummaryFixture(
            folder = folder,
            viewModel = createViewModel(mockPopupRepository = createPopupRepositoryMock(state = mockPopupRepositoryState))
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

    private fun TurbineTestContext<LauncherUiState>.expectSearch(): LauncherUiState.Search = expectState()

    private fun assertAppListStateRetained(before: LauncherUiState.AppList.Browsing, after: LauncherUiState) {
        val actualAfter: LauncherUiState.AppList.Browsing = checkState(after)
        assertSame(before.lazyListState, actualAfter.lazyListState)
        assertEquals(before.groups, actualAfter.groups)
    }

    private fun railIndex(group: LaunchableGroup): Int = inputGroups.indexOf(group)

    private fun launchableResults(vararg launchables: Launchable): List<SearchResult> =
        launchables.map { launchable -> SearchResult.LaunchableResult(launchable) }

    private fun SearchResult.launchableResult(): SearchResult.LaunchableResult {
        assertTrue("Expected a Launchable result but was $this", this is SearchResult.LaunchableResult)
        return this as SearchResult.LaunchableResult
    }

    private fun listedItem(index: Int): Launchable = inputListedGroups[index].items.first()
}

private data class FolderSummaryFixture(val folder: Folder, val viewModel: LauncherViewModel)

private data class SuggestionFixture(val app: App, val viewModel: LauncherViewModel)

private data class ShortcutSearchFixture(val publisher: App, val shortcut: ShortcutLaunchable, val viewModel: LauncherViewModel)
