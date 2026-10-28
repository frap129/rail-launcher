package feature.settings

import app.cash.turbine.test
import core.data.icons.model.Icon
import core.data.prefs.IPreferencesRepository
import core.data.prefs.Settings
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileKind
import core.data.testing.MainDispatcherRule
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.data.testing.createIconRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        )

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().isPrivateSpaceAvailable)
        }
    }

    @Test
    fun `leaves the Private Space switch off without a Private profile`() = runTest {
        val viewModel: SettingsViewModel = createViewModel(mockProfileSource = createProfileSourceMock())

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

    private fun createViewModel(
        mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
        mockProfileSource: IProfileSource = createProfileSourceMock()
    ): SettingsViewModel = SettingsViewModel(
        prefsRepo = mockPreferencesRepository,
        iconRepo = createIconRepositoryMock(inputIcon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID)),
        profileSource = mockProfileSource
    )
}
