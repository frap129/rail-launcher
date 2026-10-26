package feature.settings

import app.cash.turbine.test
import core.data.favorites.FavoriteRecord
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSourceImpl
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeFolderRepository
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupRepository
import core.data.testing.FakePreferencesRepository
import core.data.testing.FakeShortcutSource
import core.data.testing.FolderCreateRequest
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_SDK
import core.data.testing.createEagerFavoritesRepository
import core.data.testing.createRailLaunchable
import core.popups.IPopupCollaborators
import core.popups.IPopupCollaboratorsImpl
import core.popups.IPopupEditorImpl
import core.popups.IPopupFolderActionsImpl
import core.popups.IPopupPickerImpl
import core.popups.IPopupSession
import core.popups.IPopupSessionImpl
import core.ui.layout.HomeEditState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val FOLDER_NAME: String = "Work"
private const val OTHER_FOLDER_NAME: String = "Play"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val inputFolder: Folder = Folder(folderId = 1L, name = FOLDER_NAME)

    @Test
    fun `lists every Folder with its item count and location`() = runTest {
        val inputHomeFolder: Folder = inputFolder
        val inputNotShownFolder: Folder = Folder(folderId = 2L, name = OTHER_FOLDER_NAME)
        val inputBothFolder: Folder = Folder(folderId = 3L, name = "Travel", showInAppList = true)
        val inputAppListFolder: Folder = Folder(folderId = 4L, name = "Games", showInAppList = true)
        val inputItem: Launchable = createRailLaunchable(name = "First")
        val inputOtherItem: Launchable = createRailLaunchable(name = "Second")
        val inputFolders: List<Folder> = listOf(inputHomeFolder, inputNotShownFolder, inputBothFolder, inputAppListFolder)
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setItems(ownerKey = inputHomeFolder.key, items = listOf(inputItem, inputOtherItem))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(
                FavoriteRecord(key = inputHomeFolder.key, position = 0),
                FavoriteRecord(key = inputBothFolder.key, position = 1)
            )
        )
        val viewModel: PopupSettingsViewModel = createViewModel(
            folders = inputFolders,
            mockPopupRepository = mockPopupRepository,
            mockFavoriteDao = mockFavoriteDao
        )

        viewModel.uiState.test {
            val actualState: PopupSettingsUiState = expectMostRecentItem()
            assertEquals(inputFolders, actualState.folders.map { row -> row.folder })
            assertEquals(listOf(2, 0, 0, 0), actualState.folders.map { row -> row.itemCount })
            assertEquals(
                listOf(FolderLocation.Home, FolderLocation.NotShown, FolderLocation.HomeAndAppList, FolderLocation.AppList),
                actualState.folders.map { row -> row.location }
            )
        }
    }

    @Test
    fun `creates a Folder without a favorite`() = runTest {
        val mockFolderRepository: FakeFolderRepository = FakeFolderRepository()
        val viewModel: PopupSettingsViewModel = createViewModel(mockFolderRepository = mockFolderRepository)

        viewModel.uiState.test {
            viewModel.createFolder(name = FOLDER_NAME)
            advanceUntilIdle()
            assertEquals(listOf(FOLDER_NAME), expectMostRecentItem().folders.map { row -> row.folder.name })
        }
        assertEquals(
            listOf(FolderCreateRequest(name = FOLDER_NAME, initialItems = emptyList(), addToFavorites = false)),
            mockFolderRepository.createRequests
        )
    }

    @Test
    fun `emits a created Folder for the Snackbar`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.createdFolders.test {
            viewModel.createFolder(name = FOLDER_NAME)
            assertEquals(FOLDER_NAME, awaitItem().name)
        }
    }

    @Test
    fun `On Home adds the selected Folder to the Favorites`() = runTest {
        val viewModel: PopupSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.openActions(inputFolder)
            viewModel.setOnHome(true)
            advanceUntilIdle()
            assertEquals(FolderLocation.Home, expectMostRecentItem().selectedFolder?.location)
        }
    }

    @Test
    fun `deleting the open Folder closes its sheet and removes its row`() = runTest {
        val mockFolderRepository: FakeFolderRepository = FakeFolderRepository(initialFolders = listOf(inputFolder))
        val viewModel: PopupSettingsViewModel = createViewModel(mockFolderRepository = mockFolderRepository)

        viewModel.uiState.test {
            viewModel.openActions(inputFolder)
            assertEquals(inputFolder, expectMostRecentItem().selectedFolder?.folder)
            mockFolderRepository.delete(inputFolder)
            advanceUntilIdle()
            val actualState: PopupSettingsUiState = expectMostRecentItem()
            assertNull(actualState.selectedFolder)
            assertTrue(actualState.folders.isEmpty())
        }
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
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        val viewModel: PopupSettingsViewModel = createViewModel(mockFavoriteDao = mockFavoriteDao)
        advanceUntilIdle()

        viewModel.openActions(inputFolder)
        viewModel.setOnHome(true)
        advanceUntilIdle()

        assertEquals(listOf(inputFolder.key), mockFavoriteDao.records.map { record -> record.key })
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
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        val viewModel: PopupSettingsViewModel = createViewModel(mockPopupRepository = mockPopupRepository)

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().notificationsEnabled)
            viewModel.setNotificationsEnabled(true)
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().notificationsEnabled)
        }
        assertTrue(mockPopupRepository.notificationsEnabled.first())
    }

    private fun TestScope.createViewModel(
        folders: List<Folder> = listOf(inputFolder),
        mockFolderRepository: FakeFolderRepository = FakeFolderRepository(initialFolders = folders),
        mockPopupRepository: FakePopupRepository = FakePopupRepository(),
        mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(),
        accessCheck: () -> Boolean = { true }
    ): PopupSettingsViewModel {
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), folders = folders)
        return PopupSettingsViewModel(
            folderRepository = mockFolderRepository,
            popupRepository = mockPopupRepository,
            notificationSource = INotificationSourceImpl(
                context = RuntimeEnvironment.getApplication(),
                scope = backgroundScope,
                popupRepository = mockPopupRepository,
                relay = INotificationListenerRelayImpl(),
                isAccessGranted = { accessCheck() }
            ),
            favoritesRepository = createEagerFavoritesRepository(
                favoriteDao = mockFavoriteDao,
                launchableRepository = mockLaunchableRepository
            ),
            collaborators = createCollaborators(
                mockFolderRepository = mockFolderRepository,
                mockLaunchableRepository = mockLaunchableRepository,
                mockPopupRepository = mockPopupRepository
            )
        )
    }

    private fun TestScope.createCollaborators(
        mockFolderRepository: FakeFolderRepository,
        mockLaunchableRepository: FakeLaunchableRepository,
        mockPopupRepository: FakePopupRepository
    ): IPopupCollaborators {
        val session: IPopupSession = IPopupSessionImpl()
        val inputHomeEditState: HomeEditState = HomeEditState()
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        return IPopupCollaboratorsImpl(
            session = session,
            editor = IPopupEditorImpl(
                parentScope = backgroundScope,
                launchableRepository = mockLaunchableRepository,
                shortcutSource = mockShortcutSource,
                popupRepository = mockPopupRepository,
                homeEditState = inputHomeEditState,
                session = session
            ),
            picker = IPopupPickerImpl(
                parentScope = backgroundScope,
                launchableRepository = mockLaunchableRepository,
                shortcutSource = mockShortcutSource,
                popupRepository = mockPopupRepository,
                homeEditState = inputHomeEditState,
                session = session
            ),
            folder = IPopupFolderActionsImpl(
                parentScope = backgroundScope,
                launchableRepository = mockLaunchableRepository,
                popupRepository = mockPopupRepository,
                folderRepository = mockFolderRepository,
                preferencesRepository = FakePreferencesRepository(),
                homeEditState = inputHomeEditState,
                session = session
            )
        )
    }
}
