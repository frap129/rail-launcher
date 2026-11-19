package feature.launcher

import android.content.IntentSender
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.favorites.IFavoritesRepository
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableReadiness
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.launches.ILaunchCountRepository
import core.data.launches.LaunchCountRecord
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationShade
import core.data.notifications.INotificationSource
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.prefs.IPreferencesRepository
import core.data.prefs.ISearchSettings
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.profiles.IPrivateSpaceLock
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.IProfileSource
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.QuietRequestResult
import core.data.room.AppDatabase
import core.data.search.HOME_ROW_TARGET
import core.data.search.HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT
import core.data.search.IContactSearchSource
import core.data.search.ISearchHistoryRepository
import core.data.search.ISearchHistoryRepositoryImpl
import core.data.search.ISearchProjectionSource
import core.data.search.ISearchProjectionSourceImpl
import core.data.search.IShortcutSearchSource
import core.data.search.IWebSuggestionSource
import core.data.search.SearchContact
import core.data.search.SearchHistoryRecord
import core.data.search.SearchResult
import core.data.search.SearchShortcut
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.FavoritesRepositoryFixture
import core.data.testing.MainDispatcherRule
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.PopupRepositoryState
import core.data.testing.ProfileSourceState
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TEST_ALPHA_WEB_SUGGESTION
import core.data.testing.TEST_ALPHA_WEB_SUGGESTION_URL
import core.data.testing.TEST_BETA_WEB_SUGGESTION
import core.data.testing.TEST_BETA_WEB_SUGGESTION_URL
import core.data.testing.TEST_CALCULATOR_QUERY
import core.data.testing.TEST_GOOGLE_PLAY_STORE_SETTINGS
import core.data.testing.TEST_GOOGLE_SETTINGS
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TEST_PLAY_STORE_SETTINGS
import core.data.testing.TEST_SDK
import core.data.testing.TEST_WEB_SUGGESTION
import core.data.testing.TEST_WEB_SUGGESTION_URL
import core.data.testing.TimberLogRecorder
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createBuiltinGroup
import core.data.testing.createClockReading
import core.data.testing.createClockSourceMock
import core.data.testing.createContactSearchSourceMock
import core.data.testing.createFavoritesRepositoryFixture
import core.data.testing.createFolderRepositoryMock
import core.data.testing.createInMemoryDatabase
import core.data.testing.createIntentSender
import core.data.testing.createLaunchCountRecord
import core.data.testing.createLaunchCountRepositoryMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableResult
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createNotificationHandle
import core.data.testing.createNotificationRecord
import core.data.testing.createNotificationShadeMock
import core.data.testing.createPausedWorkApp
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateApp
import core.data.testing.createPrivateSpaceLock
import core.data.testing.createPrivateSpaceStatusMock
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createRailLaunchable
import core.data.testing.createSearchContact
import core.data.testing.createSearchSettings
import core.data.testing.createShortcut
import core.data.testing.createShortcutSearchSourceMock
import core.data.testing.createStandaloneShortcutRepositoryMock
import core.data.testing.createTestNotificationSource
import core.data.testing.createWebSuggestionRow
import core.data.testing.createWebSuggestionSourceMock
import core.data.testing.setFavorites
import core.data.testing.setLoaded
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.ui.GroupMark
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
private const val MESSAGES_PUBLISHER: String = "Messages"

// The matching tests assert only the rows they are about; the row tests give Search a Google
// engine so its web rows show, and the production defaults have no engine.
private val inputSearchRowsOff: Settings = TEST_GOOGLE_SETTINGS.copy(searchWebRow = false)
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

    // A blank starting query asks the Search engine for no Web suggestions, so only a Typed
    // character queries the source when Search opens.
    private val searchEntryCases: List<SearchEntryCase> = listOf(
        SearchEntryCase(name = "the Search entry", suggestionsQueried = 0, open = { viewModel -> viewModel.openSearch() }),
        SearchEntryCase(
            name = "a Typed character",
            suggestionsQueried = 1,
            open = { viewModel -> viewModel.onTypedCharacter(character = "a", isPopupOpen = false) }
        ),
        SearchEntryCase(name = "Swipe up", suggestionsQueried = 0, open = { viewModel -> viewModel.onHomeSwipeUp() }),
        SearchEntryCase(name = "a Home press", suggestionsQueried = 0, open = { viewModel -> viewModel.onHomeIntent(wasResumed = true) })
    )

    private val mockClockSource: IClockSource = createClockSourceMock(
        reading = createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS))
    )

    private val mockSearchHistoryRepository: ISearchHistoryRepository
        get() = searchHistoryRepository.value

    private val mockLaunchCountRepository: ILaunchCountRepository = createLaunchCountRepositoryMock()

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
    private val inputRailItems: List<GroupMark> = listOf(
        GroupMark.Label("★"),
        GroupMark.Label("A"),
        GroupMark.Label("B"),
        GroupMark.Label("⌂")
    )
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
    fun `shows Home before the Launchables are loaded`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = inputGroups, readiness = LaunchableReadiness.Loading)
            mockFavoritesFixture.state.setLoaded(false)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                assertFalse(actualHome.isFavoritesLoaded)
                assertTrue(actualHome.favorites.isEmpty())
            }
        }
    }

    @Test
    fun `shows only the Star on the Rail until the App List is ready`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = inputGroups, readiness = LaunchableReadiness.HomeReady)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.railItems.test {
                assertEquals(
                    listOf(GroupMark.Label("★")),
                    awaitRailItems(listOf(GroupMark.Label("★")))
                )
                inputLaunchableState.value = createLaunchableState(groups = inputGroups)
                awaitRailItems(inputRailItems)
            }
        }
    }

    @Test
    fun `reads Loading for the App List until it is ready`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = inputGroups, readiness = LaunchableReadiness.HomeReady)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(index = railIndex(inputListedGroups[0]))
                runCurrent()
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                inputLaunchableState.value = createLaunchableState(groups = inputGroups)
                val actualAppList: LauncherUiState.AppList.Browsing =
                    awaitFirstMatching { state -> state is LauncherUiState.AppList.Browsing } as LauncherUiState.AppList.Browsing
                assertEquals(inputListedGroups, actualAppList.groups)
            }
        }
    }

    @Test
    fun `carries the resolved Recently installed section beside the listed groups`() {
        runTest {
            val inputRecentApp: App = createApp(name = "Fresh", packageName = "com.example.fresh")
            val inputSection: RecentlyInstalledSection = RecentlyInstalledSection(apps = listOf(inputRecentApp))
            inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputRecentApp))
                .copy(recentlyInstalled = inputSection)
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(index = 0)
                runCurrent()

                val actualAppList: LauncherUiState.AppList.Browsing =
                    awaitFirstMatching { state -> state is LauncherUiState.AppList.Browsing } as LauncherUiState.AppList.Browsing
                assertEquals(listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputRecentApp))), actualAppList.groups)
                assertEquals(inputSection, actualAppList.recentlyInstalled)
            }
        }
    }

    @Test
    fun `carries no Recently installed section while none is resolved`() {
        runTest {
            val inputFreshApp: App = createApp(name = "Fresh", packageName = "com.example.fresh")
            inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputFreshApp))
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.uiState.test {
                expectHome()
                viewModel.onRailReleased(index = 0)
                runCurrent()

                val actualAppList: LauncherUiState.AppList.Browsing =
                    awaitFirstMatching { state -> state is LauncherUiState.AppList.Browsing } as LauncherUiState.AppList.Browsing
                assertEquals(listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(inputFreshApp))), actualAppList.groups)
                assertNull(actualAppList.recentlyInstalled)
            }
        }
    }

    @Test
    fun `shows Favorites from the Last-known App list while the Rail keeps only the Star`() {
        runTest {
            val inputLastKnownApp: App = createApp(name = "Last-known", customName = "Last-known name")
            inputLaunchableState.value = createLaunchableState(
                groups = emptyList(),
                readiness = LaunchableReadiness.LastKnownHomeReady
            )
            mockFavoritesFixture.state.setFavorites(listOf(inputLastKnownApp))
            mockFavoritesFixture.state.setLoaded(true)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                assertTrue(actualHome.isFavoritesLoaded)
                assertEquals(listOf(inputLastKnownApp), actualHome.favorites)

                viewModel.onRailReleased(index = 1)
                runCurrent()

                expectNoEvents()
                assertSame(actualHome, viewModel.uiState.value)
            }
            viewModel.railItems.test {
                assertEquals(listOf(GroupMark.Label("★")), awaitRailItems(listOf(GroupMark.Label("★"))))
            }
        }
    }

    @Test
    fun `reads Loading for Search until the App List is ready`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = inputGroups, readiness = LaunchableReadiness.HomeReady)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.openSearch()
                runCurrent()
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                inputLaunchableState.value = createLaunchableState(groups = inputGroups)
                val actualSearch: LauncherUiState.Search =
                    awaitFirstMatching { state -> state is LauncherUiState.Search } as LauncherUiState.Search
                assertEquals("", actualSearch.query)
            }
        }
    }

    @Test
    fun `keeps Home's state when Search opens before the App List is ready`() {
        runTest {
            inputLaunchableState.value = createLaunchableState(groups = inputGroups, readiness = LaunchableReadiness.HomeReady)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHome: LauncherUiState.Home = expectHome()
                viewModel.openSearch()
                runCurrent()
                assertEquals(LauncherUiState.Loading, expectMostRecentItem())
                viewModel.closeSearch()
                val actualReturnedHome: LauncherUiState.Home = expectHome()
                assertSame(actualHome.lazyListState, actualReturnedHome.lazyListState)
            }
        }
    }

    @Test
    fun `keeps the first loaded Home atomic across names, Folders and the Locked Private Space`() {
        runTest {
            val inputCustomApp: App = createApp(
                name = "Alpha",
                packageName = "com.example.custom",
                customName = "Renamed Alpha"
            )
            val inputFolderMember: App = createApp(name = "Member", packageName = "com.example.member")
            val inputFolder: Folder = Folder(folderId = FAVORITE_FOLDER_ID)
            val inputLockedPrivateApp: App = createPrivateApp(name = "Secret")
            val inputGroups: List<LaunchableGroup> = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "R", items = listOf(inputCustomApp)),
                LaunchableGroup.InitialGroup(label = "F", items = listOf(inputFolder)),
                inputBuiltinGroup
            )
            val inputState: LaunchableState = createLaunchableState(
                groups = inputGroups,
                folders = listOf(inputFolder),
                readiness = LaunchableReadiness.HomeReady
            ).copy(launchables = listOf(inputCustomApp, inputFolderMember, inputFolder) + inputBuiltinGroup.items)
            inputLaunchableState.value = inputState
            mockFavoritesFixture.state.setFavorites(listOf(inputCustomApp, inputFolderMember))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualHomes: MutableList<LauncherUiState.Home> = mutableListOf()
                var actualAppList: LauncherUiState.AppList.Browsing? = null
                var hasRequestedAppList: Boolean = false
                while (actualAppList == null) {
                    if (!hasRequestedAppList && actualHomes.any { home -> home.isFavoritesLoaded }) {
                        hasRequestedAppList = true
                        inputLaunchableState.value = inputState.copy(readiness = LaunchableReadiness.AppListReady)
                        viewModel.onRailReleased(index = 1)
                    }
                    when (val actualState: LauncherUiState = awaitItem()) {
                        is LauncherUiState.Home -> actualHomes += actualState
                        is LauncherUiState.AppList.Browsing -> actualAppList = actualState
                        else -> Unit
                    }
                }
                val actualFirstLoadedHome: LauncherUiState.Home = actualHomes.first { home -> home.isFavoritesLoaded }
                assertEquals(
                    listOf("Renamed Alpha", "Member"),
                    actualFirstLoadedHome.favorites.map { favorite -> favorite.name }
                )
                assertTrue(actualFirstLoadedHome.favorites.none { favorite -> favorite.key == inputLockedPrivateApp.key })
                assertTrue(
                    actualHomes.flatMap { home -> home.favorites }.none { favorite ->
                        favorite.key == inputLockedPrivateApp.key ||
                            (favorite.key == inputCustomApp.key && favorite.name == inputCustomApp.defaultName)
                    }
                )

                val actualResolvedAppList: LauncherUiState.AppList.Browsing = requireNotNull(actualAppList)
                val actualItems: List<Launchable> = actualResolvedAppList.groups.flatMap { group -> group.items }
                assertTrue(actualItems.none { item -> item.key == inputFolderMember.key })
                assertTrue(actualItems.any { item -> item.key == inputFolder.key })
            }
        }
    }

    @Test
    fun `never reports loaded Favorites as empty while they resolve`() {
        runTest {
            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
            mockFavoritesFixture.state.setLoaded(false)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualUnloaded: LauncherUiState.Home = expectHome()
                assertFalse(actualUnloaded.isFavoritesLoaded)
                mockFavoritesFixture.state.setLoaded(true)
                val actualLoaded: LauncherUiState.Home = awaitLoadedHome()
                assertEquals(listOf(inputItem), actualLoaded.favorites)
            }
        }
    }

    @Test
    fun `reports Home Favorites as loaded only once they resolve`() {
        runTest {
            mockFavoritesFixture.state.setLoaded(false)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualUnloaded: LauncherUiState.Home = expectHome()
                assertFalse(actualUnloaded.isFavoritesLoaded)
                assertTrue(actualUnloaded.favorites.isEmpty())
                mockFavoritesFixture.state.setLoaded(true)
                val actualLoaded: LauncherUiState.Home = awaitLoadedHome()
                assertTrue(actualLoaded.favorites.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `keeps Home suggestions still while Home is shown and updates them after leaving Home`() = runTest {
        val inputFirst: App = createApp(name = "First", packageName = "com.example.first")
        val inputSecond: App = createApp(name = "Second", packageName = "com.example.second")
        val fixture: SuggestionHomeFixture = createSuggestionHomeFixture(
            apps = listOf(inputFirst, inputSecond),
            counts = listOf(createSuggestionRecord(app = inputFirst, score = 1.0))
        )

        fixture.viewModel.uiState.test {
            val actualInitial: List<Launchable> = awaitSuggestions()
            fixture.inputCounts.value = listOf(
                createSuggestionRecord(app = inputFirst, score = 1.0),
                createSuggestionRecord(app = inputSecond, score = 2.0)
            )
            runCurrent()
            val actualWhileShown: List<Launchable> = homeSuggestions(fixture.viewModel)
            fixture.viewModel.openSearch()
            runCurrent()
            fixture.viewModel.closeSearch()
            runCurrent()
            val actualAfterLeaving: List<Launchable> = homeSuggestions(fixture.viewModel)

            assertEquals(listOf<Launchable>(inputFirst), actualInitial)
            assertEquals(listOf<Launchable>(inputFirst), actualWhileShown)
            assertEquals(listOf<Launchable>(inputSecond, inputFirst), actualAfterLeaving)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates Home suggestions after leaving Home for the App List`() = runTest {
        val inputFirst: App = createApp(name = "First", packageName = "com.example.first")
        val inputSecond: App = createApp(name = "Second", packageName = "com.example.second")
        val fixture: SuggestionHomeFixture = createSuggestionHomeFixture(
            apps = listOf(inputFirst, inputSecond),
            counts = listOf(createSuggestionRecord(app = inputFirst, score = 1.0))
        )

        fixture.viewModel.uiState.test {
            awaitSuggestions()
            fixture.inputCounts.value = listOf(
                createSuggestionRecord(app = inputFirst, score = 1.0),
                createSuggestionRecord(app = inputSecond, score = 2.0)
            )
            fixture.viewModel.onRailReleased(index = 0)
            runCurrent()
            fixture.viewModel.onBack()
            runCurrent()
            val actualAfterLeaving: List<Launchable> = homeSuggestions(fixture.viewModel)

            assertEquals(listOf<Launchable>(inputSecond, inputFirst), actualAfterLeaving)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps Home suggestions still when the Favorites change while Home is shown`() = runTest {
        val inputSuggested: List<App> = (1..3).map { index ->
            createApp(name = "Suggested $index", packageName = "com.example.suggested$index")
        }
        val inputFavorites: List<App> = (1..HOME_ROW_TARGET - 1).map { index ->
            createApp(name = "Favorite $index", packageName = "com.example.favorite$index")
        }
        val fixture: SuggestionHomeFixture = createSuggestionHomeFixture(
            apps = inputSuggested + inputFavorites,
            counts = inputSuggested.mapIndexed { index, app ->
                createSuggestionRecord(
                    app = app,
                    score =
                    inputSuggested.size - index.toDouble()
                )
            }
        )

        fixture.viewModel.uiState.test {
            awaitSuggestions()
            mockFavoritesFixture.state.setFavorites(inputFavorites)
            runCurrent()

            assertEquals(inputSuggested, homeSuggestions(fixture.viewModel))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops a Home suggestion that is Paused while Home is shown`() = runTest {
        val inputActive: App = createWorkSuggestion(isQuiet = false)
        val inputPaused: App = createWorkSuggestion(isQuiet = true)
        val fixture: SuggestionHomeFixture = createSuggestionHomeFixture(
            apps = listOf(inputActive),
            counts = listOf(createSuggestionRecord(app = inputActive, score = 1.0))
        )

        fixture.viewModel.uiState.test {
            awaitSuggestions()
            inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputPaused))
            runCurrent()

            assertEquals(emptyList<Launchable>(), homeSuggestions(fixture.viewModel))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaves Home suggestions out while editing Favorites`() = runTest {
        val inputApp: App = createApp(name = "Suggested", packageName = "com.example.suggested")
        val fixture: SuggestionHomeFixture = createSuggestionHomeFixture(
            apps = listOf(inputApp),
            counts = listOf(createSuggestionRecord(app = inputApp, score = 1.0))
        )

        fixture.viewModel.uiState.test {
            awaitSuggestions()
            fixture.viewModel.editFavorites()
            val actualEditingHome: LauncherUiState.Home =
                awaitFirstMatching { state -> state is LauncherUiState.Home && state.isEditingFavorites } as LauncherUiState.Home

            assertEquals(emptyList<Launchable>(), actualEditingHome.suggestions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Home suggestions while Suggest often-used Apps is off and shows them again when it is on`() = runTest {
        val inputApp: App = createApp(name = "Suggested", packageName = "com.example.suggested")
        val fixture: SuggestionHomeFixture = createSuggestionHomeFixture(
            apps = listOf(inputApp),
            counts = listOf(createSuggestionRecord(app = inputApp, score = 1.0))
        )

        fixture.viewModel.uiState.test {
            awaitSuggestions()
            fixture.inputSettings.value = fixture.inputSettings.value.copy(suggestOftenUsedApps = false)
            val actualOff: LauncherUiState.Home =
                awaitFirstMatching { state -> state is LauncherUiState.Home && state.suggestions.isEmpty() } as LauncherUiState.Home
            fixture.inputSettings.value = fixture.inputSettings.value.copy(suggestOftenUsedApps = true)
            val actualOn: List<Launchable> = awaitSuggestions()

            assertEquals(emptyList<Launchable>(), actualOff.suggestions)
            assertEquals(listOf<Launchable>(inputApp), actualOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not enter Edit Favorites until the Favorites are loaded`() {
        runTest {
            mockFavoritesFixture.state.setLoaded(false)
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                val actualUnloaded: LauncherUiState.Home = expectHome()
                assertFalse(actualUnloaded.isFavoritesLoaded)
                viewModel.editFavorites()
                runCurrent()
                assertFalse((viewModel.uiState.value as LauncherUiState.Home).isEditingFavorites)
                mockFavoritesFixture.state.setLoaded(true)
                awaitLoadedHome()
                viewModel.editFavorites()
                val actualEditingHome: LauncherUiState.Home =
                    awaitFirstMatching { state -> state is LauncherUiState.Home && state.isEditingFavorites } as LauncherUiState.Home
                assertTrue(actualEditingHome.isEditingFavorites)
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
                viewModel.onRailReleased(index = 1)
                val actualAppList: LauncherUiState.AppList.Browsing = expectAppList()
                assertEquals(listOf(inputBuiltinGroup), actualAppList.groups)
                assertTrue(actualAppList.groups.flatMap { group -> group.items }.any { item -> item.name == "Rail Settings" })
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
                assertEquals(inputRailItems, expectMostRecentItem())
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
    fun `exposes the Rail haptics setting`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(
                    settings = Settings(railHaptics = true)
                )
            )

            viewModel.railHaptics.test {
                assertEquals(true, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `keeps Rail haptics off until the stored setting arrives`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(settingsFlow = emptyFlow())
            )

            assertFalse(viewModel.railHaptics.value)
        }
    }

    @Test
    fun `exposes the Show Rail setting`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(
                    settings = Settings(showRail = true)
                )
            )

            viewModel.showRail.test {
                assertEquals(true, expectMostRecentItem())
            }
        }
    }

    @Test
    fun `starts with the default Show Rail before the stored setting arrives`() {
        runTest {
            val viewModel: LauncherViewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(settingsFlow = emptyFlow())
            )

            assertTrue(viewModel.showRail.value)
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.moveFavorite(fromIndex = 0, toIndex = 1)
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
                viewModel.onBack()
                advanceUntilIdle()
                mockFavoritesFixture.state.setFavorites(listOf(inputSecondItem, inputItem))
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
                assertEquals(createItemMenu(item = inputItem), expectMostRecentItem())
            }
        }
    }

    @Test
    fun `offers Hide from App List for an App and a Standalone shortcut`() = runTest {
        val inputApp: App = createApp(name = "Example", packageName = "com.example.app")
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.site")
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputApp, inputShortcut))

        assertMenuProperty(item = inputApp, expected = true, select = { it.canHide })
        assertMenuProperty(item = inputShortcut, expected = true, select = { it.canHide })
    }

    @Test
    fun `does not offer Hide from App List for a Hidden app`() = runTest {
        val inputHiddenApp: App = createApp(name = "Example", packageName = "com.example.app")
        inputLaunchableState.value = createLaunchableState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "E", items = listOf(inputHiddenApp))),
            hiddenKeys = setOf(inputHiddenApp.key)
        )

        assertMenuProperty(item = inputHiddenApp, expected = false, select = { it.canHide })
    }

    @Test
    fun `does not offer Hide from App List for a Folder`() = runTest {
        val inputFolder: Folder = Folder(folderId = 1L)
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputFolder))

        assertMenuProperty(item = inputFolder, expected = false, select = { it.canHide })
    }

    @Test
    fun `does not offer Hide from App List for a Shortcut Search result`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()
        val expectedResult: ShortcutLaunchable = fixture.shortcut.copy(publisherName = fixture.publisher.name)

        assertMenuProperty(item = expectedResult, expected = false, select = { it.canHide }, viewModel = fixture.viewModel)
    }

    @Test
    fun `does not offer Hide from App List for a Rail deeplink`() = runTest {
        val inputRailLaunchable: Launchable = createRailLaunchable(name = "Rail Settings")
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputRailLaunchable))

        assertMenuProperty(item = inputRailLaunchable, expected = false, select = { it.canHide })
    }

    @Test
    fun `lists a Hidden app in the App List while editing Favorites`() = runTest {
        val inputHiddenApp: App = createApp(name = "Zulu", packageName = "com.example.zulu")
        val inputVisibleApp: App = createApp(name = "Alpha", packageName = "com.example.alpha")
        inputLaunchableState.value = createLaunchableState(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "A", items = listOf(inputVisibleApp)),
                LaunchableGroup.InitialGroup(label = "Z", items = listOf(inputHiddenApp)),
                inputBuiltinGroup
            ),
            hiddenKeys = setOf(inputHiddenApp.key)
        )
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.railItems.test {
            assertEquals(listOf(GroupMark.Label("★"), GroupMark.Label("A"), GroupMark.Label("⌂")), expectMostRecentItem())
            viewModel.editFavorites()
            assertEquals(
                listOf(GroupMark.Label("★"), GroupMark.Label("A"), GroupMark.Label("Z"), GroupMark.Label("⌂")),
                expectMostRecentItem()
            )
        }
        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(index = 2)
            val actualHiddenGroup: LaunchableGroup.Listed = expectAppList().groups.single { group -> group.label == "Z" }
            assertEquals(listOf(inputHiddenApp), actualHiddenGroup.items)
        }
        viewModel.toggleFavorite(inputHiddenApp)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputHiddenApp) }
    }

    @Test
    fun `hides a Launchable from the App List and closes its menu`() = runTest {
        val inputApp: App = createApp(name = "Example", packageName = "com.example.app")
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputApp))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.menuState.test {
            viewModel.openItemMenu(inputApp)
            assertTrue(expectMostRecentItem() is LauncherMenuState.Menu)

            viewModel.hideFromAppList(inputApp)

            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
        }
        advanceUntilIdle()
        verify(exactly = 1) { mockPreferencesRepository.setHidden(item = inputApp, hidden = true) }
    }

    @Test
    fun `offers uninstall for a removable App when its Profile allows it`() = runTest {
        val inputApp: App = createApp(name = "Example", packageName = "com.example.app")
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputApp))

        assertMenuProperty(item = inputApp, expected = true, select = { it.canUninstall })
    }

    @Test
    fun `hides uninstall for a system App`() = runTest {
        val inputApp: App = createApp(name = "Settings", packageName = "com.android.settings", isRemovable = false)
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputApp))

        assertMenuProperty(item = inputApp, expected = false, select = { it.canUninstall })
    }

    @Test
    fun `offers Install in Private Space for an App while the Private Space is Unlocked`() = runTest {
        assertInstallInPrivateSpaceProfile(
            item = createApp(name = "Example", packageName = "com.example.app"),
            status = PrivateSpaceStatus(profile = inputPrivateProfile),
            expected = inputPrivateProfile
        )
    }

    @Test
    fun `hides Install in Private Space while the Private Space is Locked`() = runTest {
        assertInstallInPrivateSpaceProfile(
            item = createApp(name = "Example", packageName = "com.example.app"),
            status = PrivateSpaceStatus(profile = inputPrivateProfile, isLocked = true),
            expected = null
        )
    }

    @Test
    fun `hides Install in Private Space for a private App`() = runTest {
        assertInstallInPrivateSpaceProfile(
            item = createPrivateApp(),
            status = PrivateSpaceStatus(profile = inputPrivateProfile),
            expected = null
        )
    }

    @Test
    fun `hides Install in Private Space for a System App, updated or not`() = runTest {
        listOf(true, false).forEach { isRemovable ->
            assertInstallInPrivateSpaceProfile(
                item = createApp(name = "Clock", packageName = "com.android.deskclock", isRemovable = isRemovable, isSystemApp = true),
                status = PrivateSpaceStatus(profile = inputPrivateProfile),
                expected = null
            )
        }
    }

    /** Asserts the Profile whose Store page [item]'s menu offers while the Private Space has [status]. */
    private suspend fun TestScope.assertInstallInPrivateSpaceProfile(item: App, status: PrivateSpaceStatus, expected: ProfileInfo?) {
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(item))

        assertMenuProperty(
            item = item,
            expected = expected,
            select = { menu -> menu.installInPrivateSpaceProfile },
            viewModel = createViewModel(mockPrivateSpaceStatus = createPrivateSpaceStatusMock(status = MutableStateFlow(status)))
        )
    }

    @Test
    fun `hides uninstall for an App whose Profile restricts it`() = runTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputApp: App = createApp(name = "Example", packageName = "com.example.app", profileInfo = inputProfile)
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            uninstallRestrictedUsers += inputProfile.user
        }
        val viewModel: LauncherViewModel = createViewModel(
            mockProfileSource = createProfileSourceMock(state = inputProfileState)
        )

        assertMenuProperty(item = inputApp, expected = false, select = { it.canUninstall }, viewModel = viewModel)
    }

    @Test
    fun `hides uninstall for a Shortcut`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()

        assertMenuProperty(item = fixture.shortcut, expected = false, select = { it.canUninstall }, viewModel = fixture.viewModel)
    }

    @Test
    fun `hides uninstall for a Standalone shortcut or Folder`() = runTest {
        val inputShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.site")
        val inputFolder: Folder = Folder(folderId = 1L)
        inputLaunchableState.value = createLoadedLaunchableState(launchables = listOf(inputShortcut, inputFolder))

        assertMenuProperty(item = inputShortcut, expected = false, select = { it.canUninstall })
        assertMenuProperty(item = inputFolder, expected = false, select = { it.canUninstall })
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
                assertEquals(createItemMenu(item = inputItem), expectMostRecentItem())
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
            mockFavoritesFixture.state.setFavorites(listOf(inputSecondItem, inputItem))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(listOf(inputSecondItem, inputItem), expectHome().favorites)
            }
        }
    }

    @Test
    fun `removes a Launchable from the Favorites`() {
        runTest {
            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
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
                    assertEquals(createItemMenu(item = inputItem), expectMostRecentItem())
                }
            }
        }
    }

    @Test
    fun `enters Edit Favorites mode from the menu`() {
        runTest {
            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem))
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
                mockFavoritesFixture.state.favoritesState.value.favorites
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
                mockFavoritesFixture.state.setFavorites(listOf(inputItem))
                mockFavoritesFixture.state.isFavorite.value = true
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                mockFavoritesFixture.state.setFavorites(emptyList())
                advanceUntilIdle()
                assertTrue(viewModel.favoritesState.value.favorites.isEmpty())
            }
            coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.remove(inputItem) }
        }
    }

    @Test
    fun `toggles a Favorite from Home in Edit Favorites mode`() {
        runTest {
            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
            mockFavoritesFixture.state.isFavorite.value = true
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(inputItem)
                advanceUntilIdle()
                mockFavoritesFixture.state.setFavorites(emptyList())
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

            val actualCollectionJob: Job = viewModel.favoritesState.launchIn(this)
            runCurrent()
            actualCollectionJob.cancel()
            advanceUntilIdle()

            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
            advanceUntilIdle()

            assertTrue(viewModel.favoritesState.value.favorites.isEmpty())
        }
    }

    @Test
    fun `reorders a Favorite added during Edit Favorites`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            val inputRailItem: Launchable = listedItem(inputListedGroups.lastIndex)
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                assertTrue(expectHome().isEditingFavorites)
                viewModel.toggleFavorite(inputRailItem)
                advanceUntilIdle()
                mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem, inputRailItem))
                advanceUntilIdle()
                assertEquals(listOf(inputItem, inputSecondItem, inputRailItem), viewModel.favoritesState.value.favorites)
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                advanceUntilIdle()
                assertEquals(listOf(inputRailItem, inputItem, inputSecondItem), viewModel.favoritesState.value.favorites)
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem))
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
                    viewModel.favoritesState.first { state -> state.favorites.firstOrNull() == inputSecondItem }.favorites
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem))
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem, inputRailItem))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                expectHome()
                viewModel.editFavorites()
                viewModel.moveFavorite(fromIndex = 2, toIndex = 0)
                expectHome()
                viewModel.finishEditingFavorites()
                advanceUntilIdle()
                mockFavoritesFixture.state.setFavorites(listOf(inputRailItem, inputItem, inputSecondItem))
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
    fun `tapping a suspended App launches it without a prompt`() = runTest {
        val inputApp: App = createApp(name = "Gmail", packageName = "com.example.gmail", isSuspended = true)
        val viewModel: LauncherViewModel = createViewModel()

        assertTrue(viewModel.requestLaunch(inputApp))
        assertNull(viewModel.pausedPrompt.value)
    }

    @Test
    fun `long-pressing a suspended App still opens its menu`() = runTest {
        val inputApp: App = createApp(name = "Gmail", packageName = "com.example.gmail", isSuspended = true)
        val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(createLoadedLaunchableState(listOf(inputApp)))
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(inputState)
        )

        viewModel.menuState.test {
            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())
            viewModel.openItemMenu(inputApp)
            assertEquals(createItemMenu(item = inputApp, canUninstall = true), expectMostRecentItem())
        }
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
            assertEquals(createItemMenu(item = inputPausedApp, canUninstall = true), expectMostRecentItem())
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
            mockFavoritesFixture.state.setFavorites(listOf(inputItem, inputSecondItem))
            val viewModel: LauncherViewModel = createViewModel()
            viewModel.uiState.test {
                assertEquals(listOf(inputItem, inputSecondItem), expectHome().favorites)
                mockFavoritesFixture.state.setFavorites(listOf(inputSecondItem))
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
                createLaunchableState(groups = emptyList(), readiness = LaunchableReadiness.Loading)
            )
            val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputState)
            mockFavoritesFixture.state.setFavorites(listOf(inputItem))
            mockFavoritesFixture.state.setLoaded(false)
            val viewModel: LauncherViewModel = createViewModel(mockLaunchableRepository = mockLaunchableRepository)
            viewModel.uiState.test {
                val actualUnloadedHome: LauncherUiState.Home = expectHome()
                assertFalse(actualUnloadedHome.isFavoritesLoaded)
                inputState.value = createLaunchableState(groups = inputGroups)
                mockFavoritesFixture.state.setLoaded(true)
                val actualLoadedHome: LauncherUiState.Home = awaitLoadedHome()
                assertEquals(listOf(inputItem), actualLoadedHome.favorites)
            }
        }
    }

    @Test
    fun `keeps the repository Favorite order when the Launchables reload`() {
        runTest {
            val inputSecondItem: Launchable = listedItem(1)
            mockFavoritesFixture.state.setFavorites(listOf(inputSecondItem, inputItem))
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
        mockFavoritesFixture.state.setFavorites(listOf(folder))
        mockFavoritesFixture.state.isFavorite.value = true
        val viewModel: LauncherViewModel = createViewModel(mockLaunchableRepository = mockLaunchableRepository)
        viewModel.uiState.test {
            assertEquals(listOf(folder), expectHome().favorites)
            viewModel.editFavorites()
            assertTrue(expectHome().isEditingFavorites)
            viewModel.toggleFavorite(folder)
            advanceUntilIdle()
            mockFavoritesFixture.state.setFavorites(emptyList())
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
    fun `collects no Search source on Home or in the App List`() = runTest {
        val fixture: SearchSourceFixture = createSearchSourceFixture()

        fixture.viewModel.uiState.test {
            expectHome()
            runCurrent()
            fixture.assertSearchSources(shownName = "Home", expectedSubscriptions = 0, expectedSuggestionCalls = 0)

            fixture.viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
            runCurrent()
            fixture.assertSearchSources(shownName = "the App List", expectedSubscriptions = 0, expectedSuggestionCalls = 0)
        }
    }

    @Test
    fun `starts the Search sources from every Search entry and stops them after the timeout once Search closes`() = runTest {
        val fixture: SearchSourceFixture = createSearchSourceFixture(settings = Settings(openSearchOnHomePress = true))
        var expectedSuggestionCalls: Int = 0

        fixture.viewModel.uiState.test {
            expectHome()
            for (entry in searchEntryCases) {
                entry.open(fixture.viewModel)
                runCurrent()
                expectedSuggestionCalls += entry.suggestionsQueried
                fixture.assertSearchSources(
                    shownName = entry.name,
                    expectedSubscriptions = 1,
                    expectedSuggestionCalls = expectedSuggestionCalls
                )

                fixture.viewModel.closeSearch()
                advanceTimeBy(WHILE_SUBSCRIBED_TIMEOUT_MS - 1)
                runCurrent()
                fixture.assertSearchSources(
                    shownName = entry.name,
                    expectedSubscriptions = 1,
                    expectedSuggestionCalls = expectedSuggestionCalls
                )

                advanceTimeBy(2)
                runCurrent()
                fixture.assertSearchSources(
                    shownName = entry.name,
                    expectedSubscriptions = 0,
                    expectedSuggestionCalls = expectedSuggestionCalls
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `starts the sources unloaded when Search reopens after the timeout`() = runTest {
        val inputStaleShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.messages",
            shortLabel = "New message",
            longLabel = "New message"
        )
        val inputFreshShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.messages",
            shortcutId = SECOND_SHORTCUT_ID,
            shortLabel = "New call",
            longLabel = "New call"
        )
        val fixture: SearchSourceFixture = createSearchSourceFixture(settings = Settings(searchAutoLaunch = true))

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "new")
            fixture.emitSuggestionSources()
            fixture.inputContacts.emit(emptyList())
            fixture.inputShortcuts.emit(
                listOf(SearchShortcut(shortcut = inputStaleShortcut, publisherName = MESSAGES_PUBLISHER, isHidden = false))
            )
            assertEquals(createLaunchableResult(inputStaleShortcut.copy(publisherName = MESSAGES_PUBLISHER)), awaitAutoLaunch())

            fixture.viewModel.closeSearch()
            advanceTimeBy(WHILE_SUBSCRIBED_TIMEOUT_MS + 1)
            runCurrent()

            fixture.viewModel.openSearch()
            fixture.viewModel.setSearchQuery("new")
            runCurrent()
            val actualReopened: LauncherUiState.Search = expectSearch()
            assertTrue(actualReopened.results.isEmpty())
            assertNull(actualReopened.topResult)
            assertNull(actualReopened.autoLaunchResult)

            fixture.emitSuggestionSources()
            fixture.inputContacts.emit(emptyList())
            fixture.inputShortcuts.emit(
                listOf(SearchShortcut(shortcut = inputFreshShortcut, publisherName = MESSAGES_PUBLISHER, isHidden = false))
            )

            val actualFreshResult: SearchResult.LaunchableResult = awaitAutoLaunch()
            assertEquals(createLaunchableResult(inputFreshShortcut.copy(publisherName = MESSAGES_PUBLISHER)), actualFreshResult)
            assertTrue(fixture.viewModel.shouldAutoLaunch(actualFreshResult))
            assertFalse(fixture.viewModel.shouldAutoLaunch(actualFreshResult))
        }
    }

    @Test
    fun `shows Search suggestions before the Shortcuts and contacts load`() = runTest {
        val inputRecentApp: App = createApp(
            name = "Recent",
            packageName = "com.example.recent",
            firstInstallTime = TEST_NOW_EPOCH_MS - 60_000L
        )
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputRecentApp))
        val fixture: SearchSourceFixture = createSearchSourceFixture()

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()
            fixture.emitSuggestionSources()

            val actualSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.isNotEmpty()
            } as LauncherUiState.Search
            assertTrue(actualSearch.isShowingSuggestions)
            assertEquals(launchableResults(inputRecentApp), actualSearch.results)
        }
    }

    @Test
    fun `waits for every source before showing a late Top result`() = runTest {
        val inputApp: App = createApp(name = "New message", packageName = "com.example.messages")
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputApp))
        val inputTopShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.tasks",
            shortLabel = "New",
            longLabel = "New"
        )
        val fixture: SearchSourceFixture = createSearchSourceFixture(settings = Settings(searchAutoLaunch = true))

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "new")
            fixture.emitSuggestionSources()
            fixture.inputContacts.emit(emptyList())
            runCurrent()

            val actualWaiting: LauncherUiState.Search = expectSearch()
            assertTrue(actualWaiting.results.isEmpty())
            assertNull(actualWaiting.topResult)
            assertNull(actualWaiting.autoLaunchResult)

            fixture.inputShortcuts.emit(listOf(SearchShortcut(shortcut = inputTopShortcut, publisherName = "Tasks", isHidden = false)))

            val actualSearch: LauncherUiState.Search = awaitSearchWithResults()
            assertEquals(createLaunchableResult(inputTopShortcut.copy(publisherName = "Tasks")), actualSearch.topResult)
            assertEquals(2, actualSearch.results.size)
            assertNull(actualSearch.autoLaunchResult)
        }
    }

    @Test
    fun `opens the item menu of a Shortcut Search result while Search is closed`() = runTest {
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

        viewModel.menuState.test {
            assertEquals(LauncherMenuState.Closed, expectMostRecentItem())

            viewModel.openItemMenu(inputShortcut)

            val actualMenu: LauncherMenuState.Menu = expectMostRecentItem() as LauncherMenuState.Menu
            assertEquals(inputShortcut, actualMenu.item)
            assertFalse(actualMenu.isStandaloneShortcut)
        }
    }

    @Test
    fun `opens the item menu of a Standalone shortcut while Search is closed`() = runTest {
        val inputShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.messages",
            shortLabel = "New message",
            longLabel = "New message"
        )
        inputLaunchableState.value = createLoadedLaunchableState(listOf(inputShortcut))
        val inputShortcuts: MutableSharedFlow<List<SearchShortcut>> = MutableSharedFlow()
        val viewModel: LauncherViewModel = createViewModel(
            mockShortcutSearchSource = createShortcutSearchSourceMock(shortcuts = inputShortcuts)
        )

        viewModel.menuState.test {
            viewModel.openItemMenu(inputShortcut)

            val actualMenu: LauncherMenuState.Menu = expectMostRecentItem() as LauncherMenuState.Menu
            assertEquals(inputShortcut, actualMenu.item)
            assertTrue(actualMenu.isStandaloneShortcut)
            assertEquals(0, inputShortcuts.subscriptionCount.value)
        }
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
    fun `shows the stored Search options and follows a change to them`() = runTest {
        val inputSettings: MutableStateFlow<Settings> = MutableStateFlow(
            Settings(
                openSearchOnSwipeUp = false,
                searchSuggestions = false,
                searchWebSuggestions = true,
                searchContacts = true,
                searchCalculatorRow = false,
                searchEngine = SearchEngine.Kagi
            )
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settingsFlow = inputSettings),
            mockContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(true))
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            val actualOptions: SearchOptions = expectSearch().options
            assertFalse(actualOptions.openSearchOnSwipeUp)
            assertFalse(actualOptions.searchSuggestions)
            assertTrue(actualOptions.searchWebSuggestions)
            assertTrue(actualOptions.searchContacts)
            assertFalse(actualOptions.searchCalculatorRow)
            assertEquals(SearchEngine.Kagi, actualOptions.searchEngine)

            inputSettings.value = inputSettings.value.copy(searchCalculatorRow = true)

            assertTrue(expectSearch().options.searchCalculatorRow)
        }
    }

    @Test
    fun `removes and restores the Calculator row when its Search option changes`() = runTest {
        val inputSettings: MutableStateFlow<Settings> = MutableStateFlow(Settings(searchCalculatorRow = true))
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(settingsFlow = inputSettings)
        every { mockPreferencesRepository.setSearchCalculatorRow(any()) } answers {
            inputSettings.value = inputSettings.value.copy(searchCalculatorRow = firstArg())
        }
        val viewModel: LauncherViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery(TEST_CALCULATOR_QUERY)
            assertTrue(expectSearch().results.any { result -> result is SearchResult.CalculatorRow })

            viewModel.setSearchCalculatorRow(false)

            val actualSearchWithoutCalculator: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search &&
                    state.results.none { result -> result is SearchResult.CalculatorRow }
            } as LauncherUiState.Search
            assertTrue(actualSearchWithoutCalculator.results.none { result -> result is SearchResult.CalculatorRow })

            viewModel.setSearchCalculatorRow(true)

            val actualSearchWithCalculator: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search &&
                    state.results.any { result -> result is SearchResult.CalculatorRow }
            } as LauncherUiState.Search
            assertTrue(actualSearchWithCalculator.results.any { result -> result is SearchResult.CalculatorRow })
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
    fun `typing a hardware character appends to the query typed in the field`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onTypedCharacter(character = "g", isPopupOpen = false)
            assertEquals("g", expectSearch().query)

            viewModel.setSearchQuery(query = "gm")
            assertEquals("gm", expectSearch().query)

            viewModel.onTypedCharacter(character = "a", isPopupOpen = false)

            assertEquals("gma", expectSearch().query)
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
    fun `Go opens nothing while the shown results belong to an earlier query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel: LauncherViewModel = createViewModel()

            viewModel.uiState.test {
                openSearchFor(viewModel = viewModel, query = "al")
                val actualSearch: LauncherUiState.Search = expectSearch()
                assertEquals(createLaunchableResult(inputItem), actualSearch.topResult)

                // The query field is a keystroke ahead: navigation has "alp" while the shown
                // results, and so uiState, still belong to "al".
                viewModel.setSearchQuery("alp")
                assertNull(viewModel.onSearchGo("alp"))
            }
        } finally {
            Dispatchers.setMain(mainDispatcherRule.dispatcher)
        }
    }

    @Test
    fun `Go opens the Top result and resets Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
            openSearchFor(viewModel = viewModel, query = "al")
            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals(launchableResults(inputItem), actualSearch.results)
            assertEquals(createLaunchableResult(inputItem), actualSearch.topResult)

            val actualTopResult: SearchResult? = viewModel.onSearchGo("al")
            assertEquals(createLaunchableResult(inputItem), actualTopResult)
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
            assertEquals(expectedResult, viewModel.onSearchGo("12*7"))
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
            val expectedTopResult: SearchResult = createLaunchableResult(inputApp)
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
            assertEquals(expectedResult, viewModel.onSearchGo("example.com"))
        }
    }

    @Test
    fun `Go returns the web search row when nothing else matches`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = TEST_GOOGLE_SETTINGS
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("rail launcher")

            val expectedResult: SearchResult = SearchResult.WebSearchRow(url = "https://www.google.com/search?q=rail%20launcher")
            assertEquals(expectedResult, expectSearch().topResult)
            assertEquals(expectedResult, viewModel.onSearchGo("rail launcher"))
        }
    }

    @Test
    fun `Go opens nothing when no Search engine and only the Play Store row matches`() = runTest {
        inputLaunchableState.value = createLoadedLaunchableState(emptyList())
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = TEST_PLAY_STORE_SETTINGS)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("unmatched")

            val expectedResult: SearchResult = SearchResult.PlayStoreRow(
                marketUrl = "market://search?q=unmatched",
                webUrl = "https://play.google.com/store/search?q=unmatched&c=apps"
            )
            val actualSearch: LauncherUiState.Search = expectSearch()
            assertEquals(listOf(expectedResult), actualSearch.results)
            assertNull(actualSearch.topResult)
            assertNull(viewModel.onSearchGo("unmatched"))
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
            assertEquals(createLaunchableResult(inputItem), actualResult)
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

            assertEquals(createLaunchableResult(inputItem), awaitAutoLaunch())
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
    fun `appends Contact rows after the Launchable results and before the web rows`() = runTest {
        val inputContact: SearchContact = createSearchContact()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = TEST_GOOGLE_PLAY_STORE_SETTINGS.copy(searchContacts = true)
            ),
            mockContactSearchSource = createContactSearchSourceMock(
                contacts = MutableStateFlow(listOf(inputContact)),
                hasPermission = MutableStateFlow(true)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            val actualSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.any { result -> result is SearchResult.ContactRow }
            } as LauncherUiState.Search

            assertEquals(
                launchableResults(inputItem) + listOf(
                    SearchResult.ContactRow(contact = inputContact),
                    SearchResult.WebSearchRow(url = "https://www.google.com/search?q=al"),
                    SearchResult.PlayStoreRow(
                        marketUrl = "market://search?q=al",
                        webUrl = "https://play.google.com/store/search?q=al&c=apps"
                    )
                ),
                actualSearch.results
            )
        }
    }

    @Test
    fun `shows no Contact rows when the source offers none`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(searchContacts = true)),
            mockContactSearchSource = createContactSearchSourceMock(
                contacts = MutableStateFlow(emptyList()),
                hasPermission = MutableStateFlow(false)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("alice")
            advanceUntilIdle()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertTrue(actualSearch.results.none { result -> result is SearchResult.ContactRow })
        }
    }

    @Test
    fun `typed results wait for the first Contact emission`() = runTest {
        val inputContacts: MutableSharedFlow<List<SearchContact>> = MutableSharedFlow()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSearchRowsOff.copy(searchContacts = true)),
            mockContactSearchSource = createContactSearchSourceMock(
                contacts = inputContacts,
                hasPermission = MutableStateFlow(true)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            advanceUntilIdle()
            assertTrue(expectSearch().results.isEmpty())

            inputContacts.emit(emptyList())

            val actualSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.isNotEmpty()
            } as LauncherUiState.Search
            assertEquals(launchableResults(inputItem), actualSearch.results)
        }
    }

    @Test
    fun `does not make a Contact row the Top result`() = runTest {
        val inputContact: SearchContact = createSearchContact()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = TEST_GOOGLE_SETTINGS.copy(searchContacts = true)
            ),
            mockContactSearchSource = createContactSearchSourceMock(
                contacts = MutableStateFlow(listOf(inputContact)),
                hasPermission = MutableStateFlow(true)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("alice")
            val actualSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.any { result -> result is SearchResult.ContactRow }
            } as LauncherUiState.Search

            assertEquals(
                SearchResult.WebSearchRow(url = "https://www.google.com/search?q=alice"),
                actualSearch.topResult
            )
        }
    }

    @Test
    fun `holds Auto-launch back while a Contact row is showing`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(searchAutoLaunch = true, searchContacts = true)
            ),
            mockContactSearchSource = createContactSearchSourceMock(
                contacts = MutableStateFlow(listOf(createSearchContact())),
                hasPermission = MutableStateFlow(true)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            val actualSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.any { result -> result is SearchResult.ContactRow }
            } as LauncherUiState.Search
            assertNull(actualSearch.autoLaunchResult)
        }
    }

    @Test
    fun `auto-launches when no Contact matches`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(searchAutoLaunch = true, searchContacts = true)
            ),
            mockContactSearchSource = createContactSearchSourceMock(
                contacts = MutableStateFlow(emptyList()),
                hasPermission = MutableStateFlow(true)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")

            assertEquals(createLaunchableResult(inputItem), awaitAutoLaunch())
        }
    }

    @Test
    fun `shows no Web search or Web suggestion rows without a Search engine`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(searchPlayStoreRow = false)),
            mockWebSuggestionSource = createWebSuggestionSourceMock(
                suggestions = MutableStateFlow(listOf(TEST_ALPHA_WEB_SUGGESTION))
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            advanceUntilIdle()

            assertEquals(launchableResults(inputItem), expectSearch().results)
        }
    }

    @Test
    fun `shows local results before Web suggestions arrive`() = runTest {
        val inputSuggestions: MutableSharedFlow<List<String>> = MutableSharedFlow()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = inputSearchRowsOff.copy(searchWebSuggestions = true)
            ),
            mockWebSuggestionSource = createWebSuggestionSourceMock(suggestions = inputSuggestions)
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)

            inputSuggestions.emit(listOf(TEST_ALPHA_WEB_SUGGESTION))

            val actualSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.any { result -> result is SearchResult.WebSuggestionRow }
            } as LauncherUiState.Search
            assertEquals(
                launchableResults(inputItem) + createWebSuggestionRow(
                    suggestion = TEST_ALPHA_WEB_SUGGESTION,
                    url = TEST_ALPHA_WEB_SUGGESTION_URL
                ),
                actualSearch.results
            )
        }
    }

    @Test
    fun `appends Web suggestion rows below every local row`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = TEST_GOOGLE_PLAY_STORE_SETTINGS.copy(searchWebSuggestions = true)
            ),
            mockWebSuggestionSource = createWebSuggestionSourceMock(
                suggestions = MutableStateFlow(listOf(TEST_ALPHA_WEB_SUGGESTION))
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            advanceUntilIdle()
            val actualSearch: LauncherUiState.Search = expectSearch()

            assertEquals(
                launchableResults(inputItem) + listOf(
                    SearchResult.WebSearchRow(url = "https://www.google.com/search?q=al"),
                    SearchResult.PlayStoreRow(
                        marketUrl = "market://search?q=al",
                        webUrl = "https://play.google.com/store/search?q=al&c=apps"
                    ),
                    createWebSuggestionRow(
                        suggestion = TEST_ALPHA_WEB_SUGGESTION,
                        url = TEST_ALPHA_WEB_SUGGESTION_URL
                    )
                ),
                actualSearch.results
            )
            assertEquals(createLaunchableResult(inputItem), actualSearch.topResult)
        }
    }

    @Test
    fun `drops a Web suggestion response for an older query`() = runTest {
        val inputOldSuggestions: MutableSharedFlow<List<String>> = MutableSharedFlow()
        val inputNewSuggestions: MutableSharedFlow<List<String>> = MutableSharedFlow()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = inputSearchRowsOff.copy(searchWebSuggestions = true)
            ),
            mockWebSuggestionSource = createWebSuggestionSourceMock(
                suggestionsByQuery = mapOf("al" to inputOldSuggestions, "be" to inputNewSuggestions)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            assertEquals(launchableResults(inputItem), expectSearch().results)
            viewModel.setSearchQuery("be")
            assertEquals(launchableResults(listedItem(1)), expectSearch().results)

            inputOldSuggestions.emit(listOf(TEST_ALPHA_WEB_SUGGESTION))
            runCurrent()
            expectNoEvents()

            inputNewSuggestions.emit(listOf(TEST_BETA_WEB_SUGGESTION))
            val actualSuggestedSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.any { result -> result is SearchResult.WebSuggestionRow }
            } as LauncherUiState.Search
            assertEquals(
                launchableResults(listedItem(1)) + createWebSuggestionRow(
                    suggestion = TEST_BETA_WEB_SUGGESTION,
                    url = TEST_BETA_WEB_SUGGESTION_URL
                ),
                actualSuggestedSearch.results
            )
        }
    }

    @Test
    fun `never shows an older query's Web suggestions under a new query`() = runTest {
        val inputOldSuggestions: MutableSharedFlow<List<String>> = MutableSharedFlow()
        val inputNewSuggestions: MutableSharedFlow<List<String>> = MutableSharedFlow()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = inputSearchRowsOff.copy(searchWebSuggestions = true)
            ),
            mockWebSuggestionSource = createWebSuggestionSourceMock(
                suggestionsByQuery = mapOf("ab" to inputOldSuggestions, "abc" to inputNewSuggestions)
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("ab")
            expectSearch()
            inputOldSuggestions.emit(listOf(TEST_ALPHA_WEB_SUGGESTION))
            val actualOldSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.results.any { result -> result is SearchResult.WebSuggestionRow }
            } as LauncherUiState.Search
            assertEquals(
                listOf(
                    createWebSuggestionRow(
                        suggestion = TEST_ALPHA_WEB_SUGGESTION,
                        url = TEST_ALPHA_WEB_SUGGESTION_URL
                    )
                ),
                actualOldSearch.results
            )

            viewModel.setSearchQuery("abc")

            val actualNewSearch: LauncherUiState.Search = awaitFirstMatching { state ->
                state is LauncherUiState.Search && state.query == "abc"
            } as LauncherUiState.Search
            assertTrue(actualNewSearch.results.none { result -> result is SearchResult.WebSuggestionRow })
        }
    }

    @Test
    fun `does not make a Web suggestion row the Top result`() = runTest {
        val inputSettings: Settings = TEST_GOOGLE_SETTINGS.copy(
            searchWebRow = false,
            searchWebSuggestions = true
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSettings),
            mockWebSuggestionSource = createWebSuggestionSourceMock(
                suggestions = MutableStateFlow(listOf(TEST_WEB_SUGGESTION))
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("zz")
            advanceUntilIdle()
            val actualSearch: LauncherUiState.Search = expectSearch()

            assertEquals(
                listOf(createWebSuggestionRow(suggestion = TEST_WEB_SUGGESTION, url = TEST_WEB_SUGGESTION_URL)),
                actualSearch.results
            )
            assertNull(actualSearch.topResult)
            assertNull(viewModel.onSearchGo("zz"))
        }
    }

    @Test
    fun `auto-launches despite Web suggestion rows`() = runTest {
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = inputSearchRowsOff.copy(searchWebSuggestions = true, searchAutoLaunch = true)
            ),
            mockWebSuggestionSource = createWebSuggestionSourceMock(
                suggestions = MutableStateFlow(listOf(TEST_ALPHA_WEB_SUGGESTION))
            )
        )

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            advanceUntilIdle()

            assertEquals(createLaunchableResult(inputItem), expectSearch().autoLaunchResult)
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
            assertEquals(createLaunchableResult(inputItem), actualResult)
            assertTrue(viewModel.shouldAutoLaunch(actualResult))
        }
    }

    @Test
    fun `does not auto-launch the same result again after a recreation`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(searchAutoLaunch = true))
        val expectedResult: SearchResult.LaunchableResult = createLaunchableResult(inputItem)

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
        val expectedResult: SearchResult.LaunchableResult = createLaunchableResult(inputItem)

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
    fun `a launch from Home records a Launch count`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
        }
        viewModel.onLaunched(inputItem)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockLaunchCountRepository.recordLaunch(inputItem.key) }
    }

    @Test
    fun `a launch from the App List records a Launch count`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[0]))
            expectAppList()
        }
        viewModel.onLaunched(inputItem)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockLaunchCountRepository.recordLaunch(inputItem.key) }
    }

    @Test
    fun `a launch from Search records a Launch count`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("al")
            expectSearch()
        }
        viewModel.onLaunched(inputItem)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockLaunchCountRepository.recordLaunch(inputItem.key) }
    }

    @Test
    fun `a launch of a Hidden app from Search records a Launch count`() = runTest {
        val inputHiddenApp: App = createApp(name = "Zulu", packageName = "com.example.zulu")
        inputLaunchableState.value = createLoadedLaunchableState(
            launchables = listOf(inputHiddenApp),
            hiddenKeys = setOf(inputHiddenApp.key)
        )
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("zul")
            assertEquals(launchableResults(inputHiddenApp, isHidden = true), expectSearch().results)
        }
        viewModel.onLaunched(inputHiddenApp)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockLaunchCountRepository.recordLaunch(inputHiddenApp.key) }
    }

    @Test
    fun `opening a Folder Pop-up records a Launch count for the Folder`() = runTest {
        val inputFolder: Folder = Folder(folderId = FAVORITE_FOLDER_ID)
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.onFolderPopupOpened(inputFolder)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockLaunchCountRepository.recordLaunch(inputFolder.key) }
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
            assertNull(fixture.viewModel.onSearchGo(""))
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
    fun `leaves a Hidden app out of the Search suggestions`() = runTest {
        val fixture: SuggestionFixture = createSuggestionFixture(isHidden = true)

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch()

            val actualSearch: LauncherUiState.Search = expectSearch()
            assertTrue(actualSearch.isShowingSuggestions)
            assertTrue(actualSearch.results.isEmpty())
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

            val actualTopResult: SearchResult.LaunchableResult = requireNotNull(viewModel.onSearchGo("gmail")).launchableResult()
            assertEquals(createLaunchableResult(inputPausedApp), actualTopResult)
            assertFalse(viewModel.requestLaunch(actualTopResult.launchable))
            val actualRemainingSearch: LauncherUiState.Search = checkState(viewModel.uiState.value)
            assertEquals("gmail", actualRemainingSearch.query)
        }
    }

    @Test
    fun `Go offers a disabled Shortcut as the Top result`() = runTest {
        val inputDisabledShortcut: ShortcutLaunchable = createShortcut(packageName = "com.example.site", isEnabled = false)
        inputLaunchableState.value = createLaunchableState(groups = inputGroups, folders = listOf(inputDisabledShortcut))
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.openSearch()
            viewModel.setSearchQuery("shortcut")
            assertEquals(launchableResults(inputDisabledShortcut), expectSearch().results)

            val actualTopResult: SearchResult.LaunchableResult = requireNotNull(viewModel.onSearchGo("shortcut")).launchableResult()
            assertEquals(createLaunchableResult(inputDisabledShortcut), actualTopResult)
            assertTrue(actualTopResult.launchable.isDisabled)
        }
    }

    @Test
    fun `the first Search state with results contains a Shortcut result`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "ne")

            val actualSearch: LauncherUiState.Search = awaitSearchWithResults()
            val expectedResult: ShortcutLaunchable = fixture.shortcut.copy(publisherName = fixture.publisher.name)
            assertEquals(launchableResults(expectedResult), actualSearch.results)
            assertEquals(createLaunchableResult(expectedResult), actualSearch.topResult)
        }
    }

    @Test
    fun `Go runs a Shortcut Top result when no Launchable matches`() = runTest {
        val fixture: ShortcutSearchFixture = createShortcutSearchFixture()
        val expectedResult: ShortcutLaunchable = fixture.shortcut.copy(publisherName = fixture.publisher.name)

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = "new")

            val actualSearch: LauncherUiState.Search = awaitSearchWithResults()
            assertEquals(launchableResults(expectedResult), actualSearch.results)
            assertEquals(createLaunchableResult(expectedResult), actualSearch.topResult)

            val actualTopResult: SearchResult? = fixture.viewModel.onSearchGo("new")
            assertEquals(createLaunchableResult(expectedResult), actualTopResult)
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
    fun `marks a Hidden app result as Hidden when Show hidden apps is on`() = runTest {
        val fixture: HiddenSearchFixture = createHiddenSearchFixture(
            launchable = createApp(name = "Zulu", packageName = "com.example.zulu"),
            query = "zul"
        )

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = fixture.query)

            val actualSearch: LauncherUiState.Search = awaitSearchWithResults()
            assertEquals(launchableResults(fixture.launchable, isHidden = true), actualSearch.results)
        }
    }

    @Test
    fun `leaves a Hidden app out of the Search results when Show hidden apps is off`() = runTest {
        val inputVisibleApp: App = createApp(name = "Zulu Two", packageName = "com.example.zulu-two")
        val fixture: HiddenSearchFixture = createHiddenSearchFixture(
            launchable = createApp(name = "Zulu", packageName = "com.example.zulu"),
            query = "zul",
            settings = Settings(searchShowHiddenApps = false),
            visibleLaunchable = inputVisibleApp
        )

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = fixture.query)

            assertEquals(launchableResults(inputVisibleApp), awaitSearchWithResults().results)
        }
    }

    @Test
    fun `marks a Hidden Standalone shortcut result as Hidden when Show hidden apps is on`() = runTest {
        val fixture: HiddenSearchFixture = createHiddenSearchFixture(
            launchable = createShortcut(
                packageName = "com.example.messages",
                shortLabel = "New message",
                longLabel = "New message"
            ),
            query = "new"
        )

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = fixture.query)

            val actualSearch: LauncherUiState.Search = awaitSearchWithResults()
            assertEquals(launchableResults(fixture.launchable, isHidden = true), actualSearch.results)
        }
    }

    @Test
    fun `leaves a Hidden Standalone shortcut out of the Search results when Show hidden apps is off`() = runTest {
        val inputVisibleShortcut: ShortcutLaunchable = createShortcut(
            packageName = "com.example.messages",
            shortcutId = SECOND_SHORTCUT_ID,
            shortLabel = "New call",
            longLabel = "New call"
        )
        val fixture: HiddenSearchFixture = createHiddenSearchFixture(
            launchable = createShortcut(
                packageName = "com.example.messages",
                shortLabel = "New message",
                longLabel = "New message"
            ),
            query = "new",
            settings = Settings(searchShowHiddenApps = false),
            visibleLaunchable = inputVisibleShortcut
        )

        fixture.viewModel.uiState.test {
            expectHome()
            fixture.viewModel.openSearch(startingQuery = fixture.query)

            assertEquals(launchableResults(inputVisibleShortcut), awaitSearchWithResults().results)
        }
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

            val actualSearch: LauncherUiState.Search = awaitSearchWithResults()
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
            assertEquals(launchableResults(expectedResult), awaitSearchWithResults().results)
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
    fun `Home intent from another app keeps Home without opening Search`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnHomePress = true))

        viewModel.uiState.test {
            val actualHome: LauncherUiState.Home = expectHome()

            viewModel.onHomeIntent(wasResumed = false)

            runCurrent()
            expectNoEvents()
            assertSame(actualHome.lazyListState, (viewModel.uiState.value as LauncherUiState.Home).lazyListState)
        }
    }

    @Test
    fun `Home intent from another app keeps the App List where it was left`() = runTest {
        val viewModel: LauncherViewModel = createViewModel()

        viewModel.uiState.test {
            expectHome()
            viewModel.onRailReleased(railIndex(inputListedGroups[1]))
            val actualAppList: LauncherUiState.AppList.Browsing = expectAppList()

            viewModel.onHomeIntent(wasResumed = false)

            runCurrent()
            expectNoEvents()
            assertAppListStateRetained(before = actualAppList, after = viewModel.uiState.value)
        }
    }

    @Test
    fun `Home closes an open Search when the Home-press entry is on`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(
            TEST_GOOGLE_PLAY_STORE_SETTINGS.copy(openSearchOnHomePress = true)
        )

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

    @Test
    fun `Home exposes the Swipe up entry`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(openSearchOnSwipeUp = false))

        viewModel.uiState.test {
            assertFalse(expectHome().isSwipeUpEnabled)
        }
    }

    @Test
    fun `Home exposes the Swipe down entry`() = runTest {
        val viewModel: LauncherViewModel = createViewModelWithSettings(Settings(expandNotificationsOnSwipeDown = false))

        viewModel.uiState.test {
            assertFalse(expectHome().isSwipeDownEnabled)
        }
    }

    @Test
    fun `Swipe down expands the notification shade when its entry is on`() = runTest {
        val mockNotificationShade: INotificationShade = createNotificationShadeMock()
        val viewModel: LauncherViewModel = createViewModelWithSettings(
            settings = Settings(expandNotificationsOnSwipeDown = true),
            mockNotificationShade = mockNotificationShade
        )

        viewModel.onHomeSwipeDown()

        verify(exactly = 1) { mockNotificationShade.expand() }
    }

    @Test
    fun `Swipe down does not expand the notification shade when its entry is off`() = runTest {
        val mockNotificationShade: INotificationShade = createNotificationShadeMock()
        val viewModel: LauncherViewModel = createViewModelWithSettings(
            settings = Settings(expandNotificationsOnSwipeDown = false),
            mockNotificationShade = mockNotificationShade
        )

        viewModel.onHomeSwipeDown()

        verify(exactly = 0) { mockNotificationShade.expand() }
    }

    @Test
    fun `Swipe down during a Home edit mode does not expand the notification shade`() = runTest {
        val mockNotificationShade: INotificationShade = createNotificationShadeMock()
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: LauncherViewModel = createViewModelWithSettings(
            settings = Settings(expandNotificationsOnSwipeDown = true),
            inputHomeEditState = inputHomeEditState,
            mockNotificationShade = mockNotificationShade
        )

        viewModel.onHomeSwipeDown()

        verify(exactly = 0) { mockNotificationShade.expand() }
    }

    // Creates the ViewModel and lets its eager settings subscription read the saved entries.
    private fun TestScope.createViewModelWithSettings(
        settings: Settings,
        inputHomeEditState: HomeEditState = HomeEditState(),
        mockNotificationShade: INotificationShade = createNotificationShadeMock()
    ): LauncherViewModel = createViewModel(
        mockPreferencesRepository = createPreferencesRepositoryMock(settings = settings),
        inputHomeEditState = inputHomeEditState,
        mockNotificationShade = mockNotificationShade
    ).also { advanceUntilIdle() }

    private fun TestScope.createHiddenSearchFixture(
        launchable: Launchable,
        query: String,
        settings: Settings = Settings(),
        visibleLaunchable: Launchable? = null
    ): HiddenSearchFixture {
        inputLaunchableState.value = createLoadedLaunchableState(
            launchables = listOfNotNull(launchable, visibleLaunchable),
            hiddenKeys = setOf(launchable.key)
        )
        return HiddenSearchFixture(
            launchable = launchable,
            query = query,
            viewModel = createViewModelWithSettings(settings = settings)
        )
    }

    private fun TestScope.createSuggestionFixture(settings: Settings = Settings(), isHidden: Boolean = false): SuggestionFixture {
        val inputRecentApp: App = createApp(
            name = "Recent",
            packageName = "com.example.recent",
            firstInstallTime = TEST_NOW_EPOCH_MS - 60_000L
        )
        val viewModel: LauncherViewModel = createViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(
                state = MutableStateFlow(
                    createLoadedLaunchableState(
                        launchables = listOf(inputRecentApp),
                        hiddenKeys = if (isHidden) setOf(inputRecentApp.key) else emptySet()
                    )
                )
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

    private fun TestScope.createSearchSourceFixture(settings: Settings = Settings()): SearchSourceFixture {
        val inputHistory: MutableSharedFlow<List<SearchHistoryRecord>> = MutableSharedFlow()
        val inputClockReadings: MutableSharedFlow<ClockReading> = MutableSharedFlow()
        val inputShortcuts: MutableSharedFlow<List<SearchShortcut>> = MutableSharedFlow()
        val inputContacts: MutableSharedFlow<List<SearchContact>> = MutableSharedFlow()
        val mockSearchHistoryRepository: ISearchHistoryRepository = mockk()
        every { mockSearchHistoryRepository.history } returns inputHistory
        val mockWebSuggestionSource: IWebSuggestionSource = createWebSuggestionSourceMock()
        val viewModel: LauncherViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = settings),
            mockSearchHistoryRepository = mockSearchHistoryRepository,
            mockClockSource = createClockSourceMock(readings = inputClockReadings),
            mockShortcutSearchSource = createShortcutSearchSourceMock(shortcuts = inputShortcuts),
            mockContactSearchSource = createContactSearchSourceMock(contacts = inputContacts),
            mockWebSuggestionSource = mockWebSuggestionSource
        )
        return SearchSourceFixture(
            inputHistory = inputHistory,
            inputClockReadings = inputClockReadings,
            inputShortcuts = inputShortcuts,
            inputContacts = inputContacts,
            mockWebSuggestionSource = mockWebSuggestionSource,
            viewModel = viewModel
        )
    }

    private suspend fun SearchSourceFixture.emitSuggestionSources(
        history: List<SearchHistoryRecord> = emptyList(),
        reading: ClockReading = createClockReading(instant = Instant.ofEpochMilli(TEST_NOW_EPOCH_MS))
    ) {
        inputHistory.emit(history)
        inputClockReadings.emit(reading)
    }

    private fun SearchSourceFixture.assertSearchSources(shownName: String, expectedSubscriptions: Int, expectedSuggestionCalls: Int) {
        assertEquals("$shownName Search history subscribers", expectedSubscriptions, inputHistory.subscriptionCount.value)
        assertEquals("$shownName clock reading subscribers", expectedSubscriptions, inputClockReadings.subscriptionCount.value)
        assertEquals("$shownName Shortcut subscribers", expectedSubscriptions, inputShortcuts.subscriptionCount.value)
        assertEquals("$shownName contact subscribers", expectedSubscriptions, inputContacts.subscriptionCount.value)
        verify(exactly = expectedSuggestionCalls) { mockWebSuggestionSource.suggestions(any(), any()) }
    }

    private suspend fun <T> TestScope.assertMenuProperty(
        item: Launchable,
        expected: T,
        select: (LauncherMenuState.Menu) -> T,
        viewModel: LauncherViewModel = createViewModel()
    ) {
        viewModel.menuState.test {
            viewModel.openItemMenu(item)
            val actualMenu: LauncherMenuState.Menu = expectMostRecentItem() as LauncherMenuState.Menu
            assertEquals(expected, select(actualMenu))
        }
    }

    private fun TestScope.createSuggestionHomeFixture(apps: List<App>, counts: List<LaunchCountRecord>): SuggestionHomeFixture {
        inputLaunchableState.value = createLoadedLaunchableState(launchables = apps)
        val inputCounts: MutableStateFlow<List<LaunchCountRecord>> = MutableStateFlow(counts)
        val inputSettings: MutableStateFlow<Settings> = MutableStateFlow(Settings())
        return SuggestionHomeFixture(
            inputCounts = inputCounts,
            inputSettings = inputSettings,
            viewModel = createViewModel(
                mockPreferencesRepository = createPreferencesRepositoryMock(settingsFlow = inputSettings),
                mockLaunchCountRepository = createLaunchCountRepositoryMock(counts = inputCounts)
            )
        )
    }

    private fun createSuggestionRecord(app: App, score: Double): LaunchCountRecord = createLaunchCountRecord(
        key = app.key,
        launchCount = HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT,
        recentUseScore = score
    )

    private fun createWorkSuggestion(isQuiet: Boolean): App = createApp(
        name = "Mail",
        packageName = "com.example.mail",
        profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work, isQuiet = isQuiet)
    )

    private fun homeSuggestions(viewModel: LauncherViewModel): List<Launchable> =
        checkState<LauncherUiState.Home>(viewModel.uiState.value).suggestions

    private suspend fun TurbineTestContext<LauncherUiState>.awaitSuggestions(): List<Launchable> = (
        awaitFirstMatching { state ->
            state is LauncherUiState.Home && state.suggestions.isNotEmpty()
        } as LauncherUiState.Home
        ).suggestions

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
        mockLaunchCountRepository: ILaunchCountRepository = this@LauncherViewModelTest.mockLaunchCountRepository,
        mockClockSource: IClockSource = this@LauncherViewModelTest.mockClockSource,
        mockShortcutSearchSource: IShortcutSearchSource = createShortcutSearchSourceMock(),
        mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock(),
        mockSearchSettings: ISearchSettings = createSearchSettings(
            mockPreferencesRepository = mockPreferencesRepository,
            mockContactSearchSource = mockContactSearchSource
        ),
        mockWebSuggestionSource: IWebSuggestionSource = createWebSuggestionSourceMock(),
        mockNotificationShade: INotificationShade = createNotificationShadeMock(),
        mockSearchProjectionSource: ISearchProjectionSource = ISearchProjectionSourceImpl(
            searchHistoryRepository = mockSearchHistoryRepository,
            clockSource = mockClockSource,
            shortcutSearchSource = mockShortcutSearchSource,
            contactSearchSource = mockContactSearchSource,
            searchSettings = mockSearchSettings,
            webSuggestionSource = mockWebSuggestionSource
        )
    ): LauncherViewModel = LauncherViewModel(
        listOffsets = ListOffsets(startPx = START_OFFSET_PX, endPx = END_OFFSET_PX),
        launcherItemRepo = mockLaunchableRepository,
        prefsRepo = mockPreferencesRepository,
        favoritesRepo = mockFavoritesRepository,
        folderRepo = mockFolderRepository,
        popupRepo = mockPopupRepository,
        notificationSource = createNotificationSource(mockPopupRepository = mockPopupRepository),
        notificationShade = mockNotificationShade,
        homeEditState = inputHomeEditState,
        profileSource = mockProfileSource,
        privateSpaceStatus = mockPrivateSpaceStatus,
        privateSpaceLock = privateSpaceLock,
        standaloneShortcutRepository = mockStandaloneShortcutRepository,
        searchHistoryRepository = mockSearchHistoryRepository,
        launchCountRepository = mockLaunchCountRepository,
        searchProjectionSource = mockSearchProjectionSource,
        shortcutSearchSource = mockShortcutSearchSource,
        searchSettings = mockSearchSettings,
        clockSource = mockClockSource
    )

    private fun createPrivateSpaceGroups(privateSpace: LaunchableGroup.PrivateSpace): List<LaunchableGroup> = listOf(
        LaunchableGroup.Star,
        LaunchableGroup.InitialGroup(label = "A", items = listOf(createRailLaunchable("Alpha"))),
        inputBuiltinGroup,
        privateSpace
    )

    private fun expectedRailItems(privateSpace: GroupMark): List<GroupMark> = listOf(
        GroupMark.Label("★"),
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

    private suspend fun TurbineTestContext<LauncherUiState>.awaitAutoLaunch(): SearchResult.LaunchableResult {
        val actualState: LauncherUiState.Search = awaitFirstMatching { state ->
            state is LauncherUiState.Search && state.autoLaunchResult != null
        } as LauncherUiState.Search
        return requireNotNull(actualState.autoLaunchResult)
    }

    private suspend fun TurbineTestContext<LauncherUiState>.awaitSearchWithResults(): LauncherUiState.Search =
        awaitFirstMatching { state -> state is LauncherUiState.Search && state.results.isNotEmpty() } as LauncherUiState.Search

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

    private suspend fun TurbineTestContext<LauncherUiState>.awaitLoadedHome(): LauncherUiState.Home =
        awaitFirstMatching { state -> state is LauncherUiState.Home && state.isFavoritesLoaded } as LauncherUiState.Home

    private fun TurbineTestContext<LauncherUiState>.expectAppList(): LauncherUiState.AppList.Browsing = expectState()

    private fun TurbineTestContext<LauncherUiState>.expectSearch(): LauncherUiState.Search = expectState()

    private fun TestScope.openSearchFor(viewModel: LauncherViewModel, query: String) {
        viewModel.openSearch()
        viewModel.setSearchQuery(query)
        runCurrent()
    }

    private fun assertAppListStateRetained(before: LauncherUiState.AppList.Browsing, after: LauncherUiState) {
        val actualAfter: LauncherUiState.AppList.Browsing = checkState(after)
        assertSame(before.lazyListState, actualAfter.lazyListState)
        assertEquals(before.groups, actualAfter.groups)
    }

    private fun railIndex(group: LaunchableGroup): Int = inputGroups.indexOf(group)

    private fun launchableResults(vararg launchables: Launchable, isHidden: Boolean = false): List<SearchResult> =
        launchables.map { launchable -> createLaunchableResult(launchable = launchable, isHidden = isHidden) }

    private fun SearchResult.launchableResult(): SearchResult.LaunchableResult {
        assertTrue("Expected a Launchable result but was $this", this is SearchResult.LaunchableResult)
        return this as SearchResult.LaunchableResult
    }

    private fun listedItem(index: Int): Launchable = inputListedGroups[index].items.first()
}

private data class FolderSummaryFixture(val folder: Folder, val viewModel: LauncherViewModel)

private data class HiddenSearchFixture(val launchable: Launchable, val query: String, val viewModel: LauncherViewModel)

private data class SuggestionHomeFixture(
    val inputCounts: MutableStateFlow<List<LaunchCountRecord>>,
    val inputSettings: MutableStateFlow<Settings>,
    val viewModel: LauncherViewModel
)

private data class SuggestionFixture(val app: App, val viewModel: LauncherViewModel)

private data class ShortcutSearchFixture(val publisher: App, val shortcut: ShortcutLaunchable, val viewModel: LauncherViewModel)

private data class SearchSourceFixture(
    val inputHistory: MutableSharedFlow<List<SearchHistoryRecord>>,
    val inputClockReadings: MutableSharedFlow<ClockReading>,
    val inputShortcuts: MutableSharedFlow<List<SearchShortcut>>,
    val inputContacts: MutableSharedFlow<List<SearchContact>>,
    val mockWebSuggestionSource: IWebSuggestionSource,
    val viewModel: LauncherViewModel
)

/** One way into Search, and how many Web suggestion requests its starting query makes. */
private class SearchEntryCase(val name: String, val suggestionsQueried: Int, val open: (LauncherViewModel) -> Unit)
