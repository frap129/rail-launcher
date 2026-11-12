package feature.settings

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.prefs.IPreferencesRepository
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileKind
import core.data.search.IContactSearchSource
import core.data.testing.MainDispatcherRule
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.data.testing.createContactSearchSourceMock
import core.data.testing.createIconRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createPrivateSpaceStatusMock
import core.data.testing.createProfileInfo
import core.data.testing.createSearchSettings
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val viewModel: SettingsViewModel = createViewModel(
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
        val viewModel: SettingsViewModel = createViewModel()

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().isPrivateSpaceAvailable)
        }
    }

    @Test
    fun `exposes the stored Private Space switch`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
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
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setHidePrivateSpaceWhenLocked(true)

            verify(exactly = 1) { mockPreferencesRepository.setHidePrivateSpaceWhenLocked(true) }
        }
    }

    @Test
    fun `exposes the stored Scrub edges`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(scrubEdges = ScrubEdges.START)
            )
        )

        viewModel.uiState.test {
            assertEquals(ScrubEdges.START, expectMostRecentItem().scrubEdges)
        }
    }

    @Test
    fun `persists the Scrub edges`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setScrubEdges(ScrubEdges.END)

            verify(exactly = 1) { mockPreferencesRepository.setScrubEdges(ScrubEdges.END) }
        }
    }

    @Test
    fun `exposes the stored Rail side`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(railSide = RailSide.START)
            )
        )

        viewModel.uiState.test {
            assertEquals(RailSide.START, expectMostRecentItem().railSide)
        }
    }

    @Test
    fun `persists the Rail side`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setRailSide(RailSide.START)

            verify(exactly = 1) { mockPreferencesRepository.setRailSide(RailSide.START) }
        }
    }

    @Test
    fun `exposes the stored Search swipe-up entry`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
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
        val viewModel: SettingsViewModel = createViewModel(
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
        val viewModel: SettingsViewModel = createViewModel(
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
        val viewModel: SettingsViewModel = createViewModel(
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
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setOpenSearchOnHomePress(true)

            verify(exactly = 1) { mockPreferencesRepository.setOpenSearchOnHomePress(true) }
        }
    }

    @Test
    fun `exposes the stored Auto-launch and Bottom-anchored results`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
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
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setSearchAutoLaunch(true)
            viewModel.setSearchBottomAnchoredResults(true)

            verify(exactly = 1) { mockPreferencesRepository.setSearchAutoLaunch(true) }
            verify(exactly = 1) { mockPreferencesRepository.setSearchBottomAnchoredResults(true) }
        }
    }

    @Test
    fun `exposes no Search engine by default`() = runTest {
        val viewModel: SettingsViewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchEngine.None, expectMostRecentItem().searchEngine)
        }
    }

    @Test
    fun `starts with the Search defaults before the stored settings load`() = runTest {
        val inputDefaults: Settings = Settings()
        val viewModel: SettingsViewModel = createViewModel()

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
        val viewModel: SettingsViewModel = createViewModel(
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
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setSearchWebRow(false)
            viewModel.setSearchPlayStoreRow(false)

            verify(exactly = 1) { mockPreferencesRepository.setSearchWebRow(false) }
            verify(exactly = 1) { mockPreferencesRepository.setSearchPlayStoreRow(false) }
        }
    }

    @Test
    fun `exposes the stored Contact results switch when the permission is granted`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(searchContacts = true)),
            mockContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(true))
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().searchContacts)
        }
    }

    @Test
    fun `hides the Contact results switch without the Contacts permission`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(
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
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)

            viewModel.setSearchEngine(SearchEngine.Brave)

            verify(exactly = 1) { mockPreferencesRepository.setSearchEngine(SearchEngine.Brave) }
        }
    }

    @Test
    fun `persists the custom Search engine template`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)
            val inputTemplate: String = TEST_CUSTOM_SEARCH_ENGINE_TEMPLATE

            viewModel.setCustomSearchEngineTemplate(inputTemplate)

            verify(exactly = 1) { mockPreferencesRepository.setCustomSearchEngineTemplate(inputTemplate) }
        }
    }

    @Test
    fun `resets every Custom icon`() {
        runTest {
            val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
            val viewModel: SettingsViewModel = createViewModel(mockPreferencesRepository = mockPreferencesRepository)
            viewModel.resetCustomIcons()
            verify(exactly = 1) { mockPreferencesRepository.resetCustomIcons() }
        }
    }

    private fun TestScope.createViewModel(
        mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
        mockPrivateSpaceStatus: IPrivateSpaceStatus = createPrivateSpaceStatusMock(),
        mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock()
    ): SettingsViewModel = SettingsViewModel(
        prefsRepo = mockPreferencesRepository,
        iconRepo = createIconRepositoryMock(inputIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID)),
        privateSpaceStatus = mockPrivateSpaceStatus,
        searchSettings = createSearchSettings(
            mockPreferencesRepository = mockPreferencesRepository,
            mockContactSearchSource = mockContactSearchSource
        )
    )
}
