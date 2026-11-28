package feature.settings

import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.LaunchableReadiness
import core.data.launchables.model.Launchable
import core.data.prefs.AppListOrder
import core.data.prefs.IPreferencesRepository
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileKind
import core.data.search.IStoppedSuggestionRepository
import core.data.testing.MainDispatcherRule
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createApp
import core.data.testing.createContactSearchSourceMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceStatusMock
import core.data.testing.createProfileInfo
import core.data.testing.createStoppedSuggestionRepositoryMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    @Test
    fun `offers the Private Space switch when a Private profile exists`() = runTest {
        val inputPrivateProfile = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPrivateSpaceStatus = createPrivateSpaceStatusMock(
                status = MutableStateFlow(PrivateSpaceStatus(profile = inputPrivateProfile))
            )
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().isPrivateSpaceAvailable)
        }
    }

    @Test
    fun `leaves the Private Space switch off without a Private profile`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().isPrivateSpaceAvailable)
        }
    }

    @Test
    fun `exposes the stored Private Space switch`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(hidePrivateSpaceWhenLocked = true)
            )
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().hidePrivateSpaceWhenLocked)
        }
    }

    @Test
    fun `persists the Private Space switch`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setHidePrivateSpaceWhenLocked(true)

            verify(exactly = 1) { mockPreferencesRepository.setHidePrivateSpaceWhenLocked(true) }
        }
    }

    @Test
    fun `lists the Hidden apps the Hidden apps screen shows`() = runTest {
        val inputHiddenApp: App = createApp(name = "Maps")
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(inputHiddenApp),
                hiddenKeys = setOf(inputHiddenApp.key)
            )
        )

        viewModel.hiddenApps.test {
            assertEquals(listOf(inputHiddenApp), expectMostRecentItem())
        }
    }

    @Test
    fun `shows no Hidden apps when nothing is Hidden`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        viewModel.hiddenApps.test {
            assertEquals(emptyList<Launchable>(), expectMostRecentItem())
        }
    }

    @Test
    fun `shows no Hidden apps until the Launchable State loads`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockLaunchableRepository = createLaunchableRepositoryMock(readiness = LaunchableReadiness.Loading)
        )

        viewModel.hiddenApps.test {
            assertNull(expectMostRecentItem())
        }
    }

    @Test
    fun `unhides a Hidden app`() {
        runTest {
            val inputHiddenApp: App = createApp(name = "Maps")
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(
                mockPreferencesRepository = mockPreferencesRepository,
                mockLaunchableRepository = createLaunchableRepositoryMock(
                    launchables = listOf(inputHiddenApp),
                    hiddenKeys = setOf(inputHiddenApp.key)
                )
            )

            viewModel.unhide(inputHiddenApp)

            verify(exactly = 1) { mockPreferencesRepository.setHidden(inputHiddenApp, false) }
        }
    }

    @Test
    fun `exposes the stored Scrub edges`() = runTest {
        assertExposesSetting(
            expected = ScrubEdges.START,
            inputSettings = Settings(scrubEdges = ScrubEdges.START),
            read = { scrubEdges }
        )
    }

    @Test
    fun `persists the Scrub edges`() = runTest {
        assertPersists(
            persist = { setScrubEdges(ScrubEdges.END) },
            verifyCall = { setScrubEdges(ScrubEdges.END) }
        )
    }

    @Test
    fun `exposes the stored Rail side`() = runTest {
        assertExposesSetting(
            expected = RailSide.START,
            inputSettings = Settings(railSide = RailSide.START),
            read = { railSide }
        )
    }

    @Test
    fun `persists the Rail side`() = runTest {
        assertPersists(
            persist = { setRailSide(RailSide.START) },
            verifyCall = { setRailSide(RailSide.START) }
        )
    }

    @Test
    fun `defaults the App List order to Recently used`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        val actualState: SettingsUiState = viewModel.uiState.value

        assertEquals(AppListOrder.RECENTLY_USED, actualState.appListOrder)
    }

    @Test
    fun `exposes the stored App List order`() = runTest {
        assertExposesSetting(
            expected = AppListOrder.ALPHABETICAL,
            inputSettings = Settings(appListOrder = AppListOrder.ALPHABETICAL),
            read = { appListOrder }
        )
    }

    @Test
    fun `persists the App List order`() = runTest {
        assertPersists(
            persist = { setAppListOrder(AppListOrder.MOST_USED) },
            verifyCall = { setAppListOrder(AppListOrder.MOST_USED) }
        )
    }

    @Test
    fun `defaults Rank Folders by use to on`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        assertTrue(viewModel.uiState.value.rankFoldersByUse)
    }

    @Test
    fun `exposes the stored Rank Folders by use`() = runTest {
        assertExposesSetting(
            expected = false,
            inputSettings = Settings(rankFoldersByUse = false),
            read = { rankFoldersByUse }
        )
    }

    @Test
    fun `persists Rank Folders by use`() = runTest {
        assertPersists(
            persist = { setRankFoldersByUse(false) },
            verifyCall = { setRankFoldersByUse(false) }
        )
    }

    @Test
    fun `defaults Show Recently installed to off`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        assertFalse(viewModel.uiState.value.showRecentlyInstalled)
    }

    @Test
    fun `exposes the stored Show Recently installed switch`() = runTest {
        assertExposesSetting(
            expected = true,
            inputSettings = Settings(showRecentlyInstalled = true),
            read = { showRecentlyInstalled }
        )
    }

    @Test
    fun `persists Show Recently installed`() = runTest {
        assertPersists(
            persist = { setShowRecentlyInstalled(true) },
            verifyCall = { setShowRecentlyInstalled(true) }
        )
    }

    @Test
    fun `defaults Rail haptics to on`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        assertTrue(viewModel.uiState.value.railHaptics)
    }

    @Test
    fun `exposes the stored Rail haptics switch`() = runTest {
        assertExposesSetting(
            expected = false,
            inputSettings = Settings(railHaptics = false),
            read = { railHaptics }
        )
    }

    @Test
    fun `persists Rail haptics`() = runTest {
        assertPersists(
            persist = { setRailHaptics(false) },
            verifyCall = { setRailHaptics(false) }
        )
    }

    @Test
    fun `defaults Show Rail to on`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        assertTrue(viewModel.uiState.value.showRail)
    }

    @Test
    fun `exposes the stored Show Rail switch`() = runTest {
        assertExposesSetting(
            expected = false,
            inputSettings = Settings(showRail = false),
            read = { showRail }
        )
    }

    @Test
    fun `persists Show Rail`() = runTest {
        assertPersists(
            persist = { setShowRail(false) },
            verifyCall = { setShowRail(false) }
        )
    }

    @Test
    fun `defaults Suggest often-used Apps to on`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        assertTrue(viewModel.uiState.value.suggestOftenUsedApps)
    }

    @Test
    fun `exposes the stored Suggest often-used Apps switch`() = runTest {
        assertExposesSetting(
            expected = false,
            inputSettings = Settings(suggestOftenUsedApps = false),
            read = { suggestOftenUsedApps }
        )
    }

    @Test
    fun `persists Suggest often-used Apps`() = runTest {
        assertPersists(
            persist = { setSuggestOftenUsedApps(false) },
            verifyCall = { setSuggestOftenUsedApps(false) }
        )
    }

    @Test
    fun `leaves Rotate Home unset by default`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()
        assertNull(viewModel.uiState.value.rotateHome)
    }

    @Test
    fun `exposes the stored Rotate Home choice`() = runTest {
        assertExposesSetting(
            expected = true,
            inputSettings = Settings(rotateHome = true),
            read = { rotateHome }
        )
    }

    @Test
    fun `persists Rotate Home`() = runTest {
        assertPersists(
            persist = { setRotateHome(false) },
            verifyCall = { setRotateHome(false) }
        )
    }

    @Test
    fun `leaves the default launcher prompt enabled by default`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()
        assertTrue(viewModel.uiState.value.defaultLauncherPromptEnabled)
    }

    @Test
    fun `exposes the stored default launcher prompt choice`() = runTest {
        assertExposesSetting(
            expected = false,
            inputSettings = Settings(defaultLauncherPromptEnabled = false),
            read = { defaultLauncherPromptEnabled }
        )
    }

    @Test
    fun `persists the default launcher prompt choice`() = runTest {
        assertPersists(
            persist = { setDefaultLauncherPromptEnabled(false) },
            verifyCall = { setDefaultLauncherPromptEnabled(false) }
        )
    }

    @Test
    fun `exposes the stored Swipe down entry`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(expandNotificationsOnSwipeDown = false)
            )
        )

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().expandNotificationsOnSwipeDown)
        }
    }

    @Test
    fun `persists the Swipe down entry`() = runTest {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

        viewModel.setExpandNotificationsOnSwipeDown(false)

        verify(exactly = 1) { mockPreferencesRepository.setExpandNotificationsOnSwipeDown(false) }
    }

    @Test
    fun `exposes the stored Search swipe-up entry`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(openSearchOnSwipeUp = false)
            )
        )

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().openSearchOnSwipeUp)
        }
    }

    @Test
    fun `exposes the stored Search suggestions switch`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(searchSuggestions = false)
            )
        )

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().searchSuggestions)
        }
    }

    @Test
    fun `exposes the stored Live web suggestions switch`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(searchWebSuggestions = true)
            )
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().searchWebSuggestions)
        }
    }

    @Test
    fun `exposes the stored Search Home-press entry`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(openSearchOnHomePress = true)
            )
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().openSearchOnHomePress)
        }
    }

    @Test
    fun `persists the Search Home-press entry`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setOpenSearchOnHomePress(true)

            verify(exactly = 1) { mockPreferencesRepository.setOpenSearchOnHomePress(true) }
        }
    }

    @Test
    fun `exposes the stored Auto-launch and Bottom-anchored results`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(searchAutoLaunch = true, searchBottomAnchoredResults = true)
            )
        )

        viewModel.uiState.test {
            val actualState: SettingsUiState = expectMostRecentItem()
            assertTrue(actualState.searchAutoLaunch)
            assertTrue(actualState.searchBottomAnchoredResults)
        }
    }

    @Test
    fun `persists Auto-launch and Bottom-anchored results`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setSearchAutoLaunch(true)
            viewModel.setSearchBottomAnchoredResults(true)

            verify(exactly = 1) { mockPreferencesRepository.setSearchAutoLaunch(true) }
            verify(exactly = 1) { mockPreferencesRepository.setSearchBottomAnchoredResults(true) }
        }
    }

    @Test
    fun `exposes the stored Show hidden apps switch`() = runTest {
        assertExposesSetting(
            expected = false,
            inputSettings = Settings(searchShowHiddenApps = false),
            read = { searchShowHiddenApps }
        )
    }

    @Test
    fun `persists the Show hidden apps switch`() = runTest {
        assertPersists(
            persist = { setSearchShowHiddenApps(false) },
            verifyCall = { setSearchShowHiddenApps(false) }
        )
    }

    @Test
    fun `exposes no Search engine by default`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel()

        viewModel.uiState.test {
            assertEquals(SearchEngine.None, expectMostRecentItem().searchEngine)
        }
    }

    @Test
    fun `starts with the Search defaults before the stored settings load`() = runTest {
        val inputDefaults: Settings = Settings()
        val viewModel: SettingsViewModel = createSettingsViewModel()

        val actualState: SettingsUiState = viewModel.uiState.value

        assertEquals(inputDefaults.openSearchOnSwipeUp, actualState.openSearchOnSwipeUp)
        assertEquals(inputDefaults.openSearchOnHomePress, actualState.openSearchOnHomePress)
        assertEquals(inputDefaults.searchSuggestions, actualState.searchSuggestions)
        assertEquals(inputDefaults.searchAutoLaunch, actualState.searchAutoLaunch)
        assertEquals(inputDefaults.searchBottomAnchoredResults, actualState.searchBottomAnchoredResults)
        assertEquals(inputDefaults.searchCalculatorRow, actualState.searchCalculatorRow)
        assertEquals(inputDefaults.searchWebRow, actualState.searchWebRow)
        assertEquals(inputDefaults.searchPlayStoreRow, actualState.searchPlayStoreRow)
        assertEquals(inputDefaults.searchWebSuggestions, actualState.searchWebSuggestions)
        assertEquals(inputDefaults.searchShowHiddenApps, actualState.searchShowHiddenApps)
        assertEquals(inputDefaults.searchContacts, actualState.searchContacts)
        assertEquals(inputDefaults.searchEngine, actualState.searchEngine)
        assertEquals(inputDefaults.searchEngineTemplate, actualState.searchEngineTemplate)
    }

    @Test
    fun `exposes the stored Search rows and engine`() = runTest {
        val inputSettings: Settings = Settings(
            searchCalculatorRow = false,
            searchWebRow = false,
            searchPlayStoreRow = false,
            searchEngine = SearchEngine.Kagi,
            searchEngineTemplate = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
        )
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSettings)
        )

        viewModel.uiState.test {
            val actualState: SettingsUiState = expectMostRecentItem()
            assertFalse(actualState.searchCalculatorRow)
            assertFalse(actualState.searchWebRow)
            assertFalse(actualState.searchPlayStoreRow)
            assertEquals(SearchEngine.Kagi, actualState.searchEngine)
            assertEquals(TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE, actualState.searchEngineTemplate)
        }
    }

    @Test
    fun `persists the Web search and Play Store rows`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setSearchWebRow(false)
            viewModel.setSearchPlayStoreRow(false)

            verify(exactly = 1) { mockPreferencesRepository.setSearchWebRow(false) }
            verify(exactly = 1) { mockPreferencesRepository.setSearchPlayStoreRow(false) }
        }
    }

    @Test
    fun `exposes the stored Contact results switch when the permission is granted`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(searchContacts = true)),
            mockContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(true))
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().searchContacts)
        }
    }

    @Test
    fun `hides the Contact results switch without the Contacts permission`() = runTest {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(searchContacts = true)),
            mockContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(false))
        )

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().searchContacts)
        }
    }

    @Test
    fun `persists the Search engine`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setSearchEngine(SearchEngine.Brave)

            verify(exactly = 1) { mockPreferencesRepository.setSearchEngine(SearchEngine.Brave) }
        }
    }

    @Test
    fun `persists the custom Search engine template`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)
            val inputTemplate: String = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE

            viewModel.setCustomSearchEngineTemplate(inputTemplate)

            verify(exactly = 1) { mockPreferencesRepository.setCustomSearchEngineTemplate(inputTemplate) }
        }
    }

    @Test
    fun `resets every Custom icon`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)
            viewModel.resetCustomIcons()
            verify(exactly = 1) { mockPreferencesRepository.resetCustomIcons() }
        }
    }

    @Test
    fun `reports no stopped suggestions until one is stopped`() = runTest {
        val inputStoppedKeys: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockStoppedSuggestionRepository = createStoppedSuggestionRepositoryMock(keys = inputStoppedKeys)
        )

        viewModel.hasStoppedSuggestions.test {
            assertFalse(expectMostRecentItem())
            inputStoppedKeys.value = setOf("com.example/.Main_UserHandle{0}")
            assertTrue(expectMostRecentItem())
        }
    }

    @Test
    fun `resets every stopped suggestion`() = runTest {
        val mockStoppedSuggestionRepository: IStoppedSuggestionRepository = resetStoppedSuggestions()

        coVerify(exactly = 1) { mockStoppedSuggestionRepository.reset() }
    }

    @Test
    fun `logs a failed reset of the stopped suggestions without crashing`() = runTest {
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.record {
            resetStoppedSuggestions { mockStoppedSuggestionRepository ->
                coEvery { mockStoppedSuggestionRepository.reset() } throws IllegalStateException("Could not reset")
            }
        }

        logRecorder.assertLogged("Failed to reset the stopped suggestions")
        logRecorder.assertLogged("Could not reset")
    }

    private fun resetStoppedSuggestions(stub: (IStoppedSuggestionRepository) -> Unit = {}): IStoppedSuggestionRepository {
        val mockStoppedSuggestionRepository: IStoppedSuggestionRepository = createStoppedSuggestionRepositoryMock()
        stub(mockStoppedSuggestionRepository)
        val viewModel: SettingsViewModel = createSettingsViewModel(mockStoppedSuggestionRepository = mockStoppedSuggestionRepository)

        viewModel.resetStoppedSuggestions()

        return mockStoppedSuggestionRepository
    }

    private suspend fun <T> TestScope.assertExposesSetting(expected: T, inputSettings: Settings, read: SettingsUiState.() -> T) {
        val viewModel: SettingsViewModel = createSettingsViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = inputSettings)
        )

        viewModel.uiState.test {
            assertEquals(expected, expectMostRecentItem().read())
        }
    }

    private fun TestScope.assertPersists(persist: SettingsViewModel.() -> Unit, verifyCall: IPreferencesRepository.() -> Unit) {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val viewModel: SettingsViewModel = createSettingsViewModel(mockPreferencesRepository = mockPreferencesRepository)

        viewModel.persist()

        verify(exactly = 1) { mockPreferencesRepository.verifyCall() }
    }
}
