package feature.settings

import app.cash.turbine.test
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.model.Launchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.testing.FavoritesRepositoryFixture
import core.data.testing.TEST_SDK
import core.data.testing.createFavoritesRepositoryFixture
import core.data.testing.createFolderRepositoryMock
import core.data.testing.createRailLaunchable
import core.data.testing.createTestNotificationSource
import core.ui.popups.PopupTestBase
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val FOLDER_NAME: String = "Work"
private const val OTHER_FOLDER_NAME: String = "Play"
private const val TRAVEL_FOLDER_NAME: String = "Travel"
private const val GAMES_FOLDER_NAME: String = "Games"
private const val NEW_FOLDER_NAME: String = "Trips"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupSettingsViewModelTest : PopupTestBase() {
    private val mockFavoritesFixture: FavoritesRepositoryFixture = createFavoritesRepositoryFixture()

    @Test
    fun `lists every Folder with its item count and location`() = runTest {
        val inputHomeFolder: Folder = Folder(folderId = 1L, name = FOLDER_NAME)
        val inputOtherFolder: Folder = Folder(folderId = 2L, name = OTHER_FOLDER_NAME)
        val inputTravelFolder: Folder = Folder(folderId = 3L, name = TRAVEL_FOLDER_NAME)
        val inputGamesFolder: Folder = Folder(folderId = 4L, name = GAMES_FOLDER_NAME)
        val inputFolders: List<Folder> = listOf(inputHomeFolder, inputOtherFolder, inputTravelFolder, inputGamesFolder)
        val inputItem: Launchable = createRailLaunchable(name = "First")
        val inputOtherItem: Launchable = createRailLaunchable(name = "Second")
        setFolders(inputFolders)
        setPopupLaunchables(ownerKey = inputHomeFolder.key, launchables = listOf(inputItem, inputOtherItem))
        mockFavoritesFixture.state.favorites.value = listOf(inputHomeFolder, inputTravelFolder)
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val actualState: PopupSettingsUiState = expectMostRecentItem()
            assertEquals(inputFolders, actualState.folders.map { row -> row.folder })
            assertEquals(listOf(2, 0, 0, 0), actualState.folders.map { row -> row.itemCount })
            assertEquals(
                listOf(
                    FolderLocation.HomeAndAppList,
                    FolderLocation.AppList,
                    FolderLocation.HomeAndAppList,
                    FolderLocation.AppList
                ),
                actualState.folders.map { row -> row.location }
            )
        }
    }

    @Test
    fun `creates a Folder without a favorite`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.createFolder(name = NEW_FOLDER_NAME)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mockFolderRepository.create(name = NEW_FOLDER_NAME, initialItems = emptyList(), addToFavorites = false)
        }
    }

    @Test
    fun `emits a created Folder for the Snackbar`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel(
            mockFolderRepository = createFolderRepositoryMock(
                createdFolder = Folder(folderId = 1L, name = NEW_FOLDER_NAME)
            )
        )

        viewModel.createdFolders.test {
            viewModel.createFolder(name = NEW_FOLDER_NAME)
            assertEquals(NEW_FOLDER_NAME, awaitItem().name)
        }
    }

    @Test
    fun `On Home adds the selected Folder to the Favorites`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.openActions(inputFolder)
            viewModel.setOnHome(true)
            advanceUntilIdle()
            mockFavoritesFixture.state.favorites.value = listOf(inputFolder)
            assertEquals(FolderLocation.HomeAndAppList, expectMostRecentItem().selectedFolder?.location)
        }
        coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputFolder) }
    }

    @Test
    fun `deleting the open Folder closes its sheet and removes its row`() = runTest {
        setFolders(listOf(inputFolder))
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.openActions(inputFolder)
            assertEquals(inputFolder, expectMostRecentItem().selectedFolder?.folder)
            viewModel.collaborators.folder.requestDeleteFolder(inputFolder)
            viewModel.collaborators.folder.confirmDeleteFolder()
            advanceUntilIdle()
            deleteFolder(inputFolder)
            val actualState: PopupSettingsUiState = expectMostRecentItem()
            assertNull(actualState.selectedFolder)
            assertTrue(actualState.folders.isEmpty())
        }
        coVerify(exactly = 1) { mockFolderRepository.delete(inputFolder) }
    }

    @Test
    fun `opens and closes the new Folder name dialog`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.openNewFolderDialog()
            assertTrue(expectMostRecentItem().isNewFolderDialogOpen)
            viewModel.closeNewFolderDialog()
            assertFalse(expectMostRecentItem().isNewFolderDialogOpen)
        }
    }

    @Test
    fun `adds the selected Folder to the Favorites before the list is collected`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openActions(inputFolder)
        viewModel.setOnHome(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockFavoritesFixture.mockFavoritesRepository.add(inputFolder) }
    }

    @Test
    fun `shows the notification switch and access status`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel(accessCheck = { true })

        viewModel.uiState.test {
            val actualState: PopupSettingsUiState = expectMostRecentItem()
            assertFalse(actualState.notificationsEnabled)
            assertTrue(actualState.notificationAccess)
        }
    }

    @Test
    fun `turning notifications on without access asks to allow it`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel(accessCheck = { false })

        viewModel.events.test {
            viewModel.setNotificationsEnabled(true)
            assertEquals(WidgetSettingsEvent.RequestNotificationAccess, awaitItem())
        }
    }

    @Test
    fun `turning notifications on with access does not ask to allow it`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel(accessCheck = { true })

        viewModel.events.test {
            viewModel.setNotificationsEnabled(true)
            expectNoEvents()
        }
    }

    @Test
    fun `persists whether Pop-ups show notifications`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.setNotificationsEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockPopupRepository.setNotificationsEnabled(true) }
    }

    @Test
    fun `shows notifications as on when the repository has them on`() = runTest {
        setNotificationsEnabled(true)
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().notificationsEnabled)
        }
    }

    private fun TestScope.createViewModel(
        accessCheck: () -> Boolean = { true },
        mockFolderRepository: IFolderRepository = this@PopupSettingsViewModelTest.mockFolderRepository
    ): PopupSettingsViewModel = PopupSettingsViewModel(
        folderRepository = mockFolderRepository,
        popupRepository = mockPopupRepository,
        notificationSource = createTestNotificationSource(
            context = RuntimeEnvironment.getApplication(),
            scope = backgroundScope,
            mockPopupRepository = mockPopupRepository,
            relay = INotificationListenerRelayImpl(),
            isAccessGranted = accessCheck
        ),
        favoritesRepository = mockFavoritesFixture.mockFavoritesRepository,
        collaborators = createCollaborators()
    )
}
