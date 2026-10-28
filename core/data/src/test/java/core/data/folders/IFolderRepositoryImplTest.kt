package core.data.folders

import core.data.favorites.IFavoritesRepository
import core.data.icons.model.Icon
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.room.dao.FolderDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createFavoritesRepositoryMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createPopupRepository
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createShortcutSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ITEM_KEY: String = "com.example.app_0"
private const val SECOND_ITEM_KEY: String = "com.example.app_1"
private const val FOLDER_NAME: String = "Games"

class IFolderRepositoryImplTest : InMemoryDatabaseTest() {
    @Test
    fun `create derives the shade from the id and stores the name and items`() = databaseTest {
        val customizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        val mockFavoritesRepository: IFavoritesRepository = createFavoritesRepositoryMock()
        val mockPopupRepository: IPopupRepository = createPopupRepositoryMock()
        val repository: IFolderRepository = createRepository(
            customizationDao = customizationDao,
            mockFavoritesRepository = mockFavoritesRepository,
            mockPopupRepository = mockPopupRepository
        )

        val actualFolder: Folder = repository.create(name = FOLDER_NAME, initialItems = listOf(ITEM_KEY, SECOND_ITEM_KEY))

        assertEquals(1L, actualFolder.folderId)
        assertEquals(Icon.FolderIcon(shade = 1), actualFolder.icon)
        assertEquals(FOLDER_NAME, actualFolder.name)
        assertEquals(listOf(LaunchableCustomization(actualFolder.key, FOLDER_NAME)), customizations())
        coVerify(exactly = 1) { mockFavoritesRepository.add(actualFolder) }
        coVerify(exactly = 1) {
            mockPopupRepository.setItems(
                ownerKey = actualFolder.key,
                add = listOf(ITEM_KEY, SECOND_ITEM_KEY),
                remove = emptySet()
            )
        }
    }

    @Test
    fun `create keeps the default name when the name is blank or the default`() = databaseTest {
        val repository: IFolderRepository = createRepository(
            mockFavoritesRepository = createFavoritesRepositoryMock()
        )

        val blankNamedFolder: Folder = repository.create(name = "   ")
        val defaultNamedFolder: Folder = repository.create(name = Folder.DEFAULT_NAME)

        assertEquals(Folder.DEFAULT_NAME, blankNamedFolder.name)
        assertEquals(Folder.DEFAULT_NAME, defaultNamedFolder.name)
        assertTrue(customizations().isEmpty())
    }

    @Test
    fun `create can leave the Folder out of Favorites`() = databaseTest {
        val mockFavoritesRepository: IFavoritesRepository = createFavoritesRepositoryMock()
        val repository: IFolderRepository = createRepository(
            mockFavoritesRepository = mockFavoritesRepository
        )

        repository.create(name = FOLDER_NAME, addToFavorites = false)

        coVerify(exactly = 0) { mockFavoritesRepository.add(any()) }
    }

    @Test
    fun `delete cascades through its Pop-up, Favorites and customization`() = databaseTest {
        val inputFolder: Folder = Folder(folderId = 3L)
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = inputFolder.folderId))
        val customizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        customizationDao.setName(key = inputFolder.key, name = FOLDER_NAME)
        val mockFavoritesRepository: IFavoritesRepository = createFavoritesRepositoryMock()
        val mockPopupRepository: IPopupRepository = createPopupRepositoryMock()
        val repository: IFolderRepository = createRepository(
            folderDao = folderDao,
            customizationDao = customizationDao,
            mockFavoritesRepository = mockFavoritesRepository,
            mockPopupRepository = mockPopupRepository
        )

        repository.delete(inputFolder)

        assertTrue(folderDao.observeAll().first().isEmpty())
        coVerify(exactly = 1) { mockPopupRepository.deleteOwner(ownerKey = inputFolder.key) }
        coVerify(exactly = 1) { mockPopupRepository.deleteItemsByKey(itemKeys = listOf(inputFolder.key)) }
        coVerify(exactly = 1) { mockFavoritesRepository.remove(inputFolder) }
        assertTrue(customizations().isEmpty())
        assertEquals(Icon.FolderIcon(shade = 0), inputFolder.icon)
    }

    @Test
    fun `delete frees the Folder's widget place and App Widget ids`() = databaseTest {
        val inputFolder: Folder = Folder(folderId = 3L)
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            removeOwnerPlacesAnswer = { listOf(AppWidgetId(55)) }
        )
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val mockPopupRepository: IPopupRepositoryImpl = createPopupRepository(
            popupDao = database.getPopupDao(),
            launchableRepository = createLaunchableRepositoryMock(
                MutableStateFlow(createLaunchableState(groups = emptyList(), folders = listOf(inputFolder)))
            ),
            scope = backgroundScope,
            shortcutSource = createShortcutSourceMock(),
            widgetAreaRepository = mockWidgetAreaRepository,
            appWidgetHost = mockAppWidgetHost
        )
        database.getFolderDao().insert(FolderRecord(id = inputFolder.folderId))
        val repository: IFolderRepository = IFolderRepositoryImpl(
            folderDao = database.getFolderDao(),
            favoritesRepository = createFavoritesRepositoryMock(),
            popupRepository = mockPopupRepository,
            preferencesRepository = createPreferencesRepositoryMock(),
            launchableCustomizationDao = database.getLaunchableCustomizationDao()
        )

        val actualFreedIds: List<AppWidgetId> = repository.delete(inputFolder)

        assertEquals(listOf(AppWidgetId(55)), actualFreedIds)
        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputFolder.key) }
        verify(exactly = 1) { mockAppWidgetHost.deleteId(id = AppWidgetId(55)) }
    }

    private fun createRepository(
        folderDao: FolderDao = database.getFolderDao(),
        customizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao(),
        mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
        mockFavoritesRepository: IFavoritesRepository = createFavoritesRepositoryMock(),
        mockPopupRepository: IPopupRepository = createPopupRepositoryMock()
    ): IFolderRepository = IFolderRepositoryImpl(
        folderDao = folderDao,
        favoritesRepository = mockFavoritesRepository,
        popupRepository = mockPopupRepository,
        preferencesRepository = mockPreferencesRepository,
        launchableCustomizationDao = customizationDao
    )
}
