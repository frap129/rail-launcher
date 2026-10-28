package core.data.folders

import core.data.favorites.FavoriteRecord
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.popups.PopupItemRecord
import core.data.prefs.LaunchableCustomization
import core.data.room.dao.FavoriteDao
import core.data.room.dao.FolderDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.shortcuts.IShortcutSource
import core.data.testing.FIRST_POSITION
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createApp
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createFavoritesRepository
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPopupRepository
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcutSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.failOnce
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetAreaRepositoryImpl
import core.data.widgets.IWidgetLifecycleReconcilerImpl
import core.data.widgets.WidgetKind
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ITEM_PACKAGE_NAME: String = "com.example.app"
private const val OTHER_OWNER_KEY: String = "com.other_owner"
private const val FOLDER_ID: Long = 3L
private const val APP_WIDGET_ID: Int = 42
private val otherOwnerLaunchable: Launchable = createRailLaunchable(name = "Other", deeplink = OTHER_OWNER_KEY)

@OptIn(ExperimentalCoroutinesApi::class)
class IFolderRepositoryImplAtomicityTest : InMemoryDatabaseTest() {
    @Test
    fun `create leaves no Folder when a Pop-up write fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val mockPopupRepository: IPopupRepository = createPopupRepositoryMock().apply {
            coEvery { setItems(any(), any(), any()) } throws IllegalStateException("Pop-up write failed")
        }
        val repository: IFolderRepository = createFolderRepository(mockPopupRepository = mockPopupRepository)

        val actualFailure: Throwable? = runCatching {
            repository.create(name = "Games", initialItems = listOf(inputItem.key))
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(database.getFolderDao().observeAll().first().isEmpty())
        assertTrue(database.getPopupDao().getAllItems().isEmpty())
        assertTrue(customizations().isEmpty())
    }

    @Test
    fun `create rolls back the Folder and its items when the Favorite write fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(
            launchables = listOf(inputItem, otherOwnerLaunchable),
            folders = listOf(Folder(folderId = 1L))
        )
        val mockFavoriteDao: FavoriteDao = failingFavoriteAdd()
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            favoriteDao = mockFavoriteDao,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        val actualFailure: Throwable? = runCatching {
            repository.create(name = "Games", initialItems = listOf(inputItem.key))
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(database.getFolderDao().observeAll().first().isEmpty())
        assertTrue(database.getPopupDao().getAllItems().isEmpty())
        assertTrue(favoriteRecords().isEmpty())
    }

    @Test
    fun `create removes the Folder when the name write fails`() = runTest {
        val mockCustomizationDao: LaunchableCustomizationDao = spyk(database.getLaunchableCustomizationDao())
        coEvery { mockCustomizationDao.setName(any(), any()) } throws IllegalStateException("Name write failed")
        val repository: IFolderRepository = createFolderRepository(customizationDao = mockCustomizationDao)

        val actualFailure: Throwable? = runCatching { repository.create(name = "Games") }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(database.getFolderDao().observeAll().first().isEmpty())
        coVerify { mockCustomizationDao.delete(key = Folder(folderId = 1L).key) }
    }

    @Test
    fun `create retries the Folder delete when its cleanup fails once`() = runTest {
        val mockFolderDao: FolderDao = database.getFolderDao().failOnce(
            failure = IllegalStateException("Folder delete failed"),
            stub = { dao -> dao.delete(any()) },
            delegate = { dao -> dao.delete(firstArg()) }
        )
        val mockFavoriteDao: FavoriteDao = failingFavoriteAdd()
        val repository: IFolderRepository = createFolderRepository(folderDao = mockFolderDao, favoriteDao = mockFavoriteDao)

        val actualFailure: Throwable? = runCatching { repository.create(name = "Games") }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(database.getFolderDao().observeAll().first().isEmpty())
    }

    @Test
    fun `create stores the Folder, its items and its Favorite together`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val expectedFolder: Folder = Folder(folderId = 1L, name = "Games")
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(
            launchables = listOf(inputItem, otherOwnerLaunchable),
            folders = listOf(expectedFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        val actualFolder: Folder = repository.create(name = "Games", initialItems = listOf(inputItem.key))

        assertEquals(expectedFolder, actualFolder)
        assertEquals(listOf(FolderRecord(id = expectedFolder.folderId)), database.getFolderDao().observeAll().first())
        assertEquals(
            listOf(PopupItemRecord(ownerKey = expectedFolder.key, itemKey = inputItem.key, position = 0)),
            database.getPopupDao().getAllItems()
        )
        assertEquals(listOf(FavoriteRecord(key = expectedFolder.key, position = FIRST_POSITION)), favoriteRecords())
        assertEquals(listOf(LaunchableCustomization(key = expectedFolder.key, name = "Games")), customizations())
    }

    @Test
    fun `delete keeps the Folder when a Pop-up item write fails`() = runTest {
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        database.getFolderDao().insert(FolderRecord(id = inputFolder.folderId))
        val inputFavorite: FavoriteRecord = FavoriteRecord(key = inputFolder.key, position = FIRST_POSITION)
        seedFavorite(key = inputFolder.key, position = FIRST_POSITION)
        val mockPopupRepository: IPopupRepository = createPopupRepositoryMock().apply {
            coEvery { deleteItemsByKey(any()) } throws IllegalStateException("Pop-up write failed")
        }
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(otherOwnerLaunchable),
                folders = listOf(inputFolder)
            ),
            mockPopupRepository = mockPopupRepository
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), database.getFolderDao().observeAll().first())
        assertEquals(listOf(inputFavorite), favoriteRecords())
    }

    @Test
    fun `delete keeps the Folder and its Pop-up rows when the owner delete fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val inputOwnedItem: PopupItemRecord = PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0)
        val inputReferencingItem: PopupItemRecord = PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = inputFolder.key, position = 0)
        seedFolder(folder = inputFolder, items = listOf(inputOwnedItem, inputReferencingItem))
        seedFavorite(key = inputFolder.key, position = FIRST_POSITION)
        val mockPopupRepository: IPopupRepository = createPopupRepositoryMock().apply {
            coEvery { deleteOwner(any()) } throws IllegalStateException("Pop-up write failed")
        }
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(inputItem, otherOwnerLaunchable),
                folders = listOf(inputFolder)
            ),
            mockPopupRepository = mockPopupRepository
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), database.getFolderDao().observeAll().first())
        assertEquals(setOf(inputOwnedItem, inputReferencingItem), database.getPopupDao().getAllItems().toSet())
    }

    @Test
    fun `delete rolls back the Folder, its Pop-up rows and name when releasing Widgets fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val inputOwnedItem: PopupItemRecord = PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0)
        val inputReferencingItem: PopupItemRecord = PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = inputFolder.key, position = 0)
        seedFolder(folder = inputFolder, items = listOf(inputOwnedItem, inputReferencingItem))
        seedFavorite(key = inputFolder.key, position = FIRST_POSITION)
        seedCustomization(key = inputFolder.key, name = "Games")
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            removeOwnerPlacesAnswer = { throw IllegalStateException("Widget release failed") }
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(
            launchables = listOf(inputItem, otherOwnerLaunchable),
            folders = listOf(inputFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockPopupRepository = createDatabasePopupRepository(
                launchableRepository = mockLaunchableRepository,
                mockWidgetAreaRepository = mockWidgetAreaRepository
            )
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), database.getFolderDao().observeAll().first())
        assertEquals(setOf(inputOwnedItem, inputReferencingItem), database.getPopupDao().getAllItems().toSet())
        assertEquals(listOf(LaunchableCustomization(key = inputFolder.key, name = "Games")), customizations())
    }

    @Test
    fun `delete releases no Widget ids when the transaction fails after the owner delete`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        database.getFolderDao().insert(FolderRecord(id = inputFolder.folderId))
        val mockFolderDao: FolderDao = database.getFolderDao().failOnce(
            failure = IllegalStateException("Folder delete failed"),
            stub = { dao -> dao.delete(any()) },
            delegate = { dao -> dao.delete(firstArg()) }
        )
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            removeOwnerPlacesAnswer = { listOf(AppWidgetId(APP_WIDGET_ID)) }
        )
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(
            launchables = listOf(inputItem, otherOwnerLaunchable),
            folders = listOf(inputFolder)
        )
        seedFavorite(key = inputFolder.key, position = FIRST_POSITION)
        val repository: IFolderRepository = createFolderRepository(
            folderDao = mockFolderDao,
            launchableRepository = mockLaunchableRepository,
            mockPopupRepository = createDatabasePopupRepository(
                launchableRepository = mockLaunchableRepository,
                mockWidgetAreaRepository = mockWidgetAreaRepository,
                mockAppWidgetHost = mockAppWidgetHost
            )
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        verify(exactly = 0) { mockAppWidgetHost.deleteId(any()) }
        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputFolder.key) }
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), database.getFolderDao().observeAll().first())
    }

    @Test
    fun `delete removes the Folder, its Pop-up rows, Place, name and Favorite`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val inputOwnedItem: PopupItemRecord = PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0)
        val inputReferencingItem: PopupItemRecord = PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = inputFolder.key, position = 0)
        seedFolder(folder = inputFolder, items = listOf(inputOwnedItem, inputReferencingItem))
        seedFavorite(key = inputFolder.key, position = FIRST_POSITION)
        seedCustomization(key = inputFolder.key, name = "Games")
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(
            launchables = listOf(inputItem, otherOwnerLaunchable),
            folders = listOf(inputFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockPopupRepository = createDatabasePopupRepository(
                launchableRepository = mockLaunchableRepository,
                mockWidgetAreaRepository = createWidgetAreaRepository()
            )
        )

        val actualFreedIds: List<AppWidgetId> = repository.delete(inputFolder)

        assertEquals(listOf(AppWidgetId(APP_WIDGET_ID)), actualFreedIds)
        assertTrue(database.getFolderDao().observeAll().first().isEmpty())
        assertTrue(database.getPopupDao().getAllItems().isEmpty())
        assertTrue(database.getWidgetAreaDao().observePlaces().first().isEmpty())
        assertTrue(favoriteRecords().isEmpty())
        assertTrue(customizations().isEmpty())
    }

    @Test
    fun `delete purges the Favorite when its removal fails after the Folder is gone`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        seedFolder(
            folder = inputFolder,
            items = listOf(PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0))
        )
        val mockFavoriteDao: FavoriteDao = database.getFavoriteDao().failOnce(
            failure = IllegalStateException("Favorite removal failed"),
            stub = { dao -> dao.remove(any()) },
            delegate = { dao -> dao.remove(firstArg()) }
        )
        seedFavorite(key = inputFolder.key, position = FIRST_POSITION)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(
                launchables = listOf(inputItem, otherOwnerLaunchable),
                folders = listOf(inputFolder)
            )
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            favoriteDao = mockFavoriteDao,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        repository.delete(inputFolder)
        launchableState.value = createLaunchableState(groups = emptyList())
        runCurrent()

        assertTrue(database.getFolderDao().observeAll().first().isEmpty())
        assertTrue(database.getFavoriteDao().observeAll().first { records -> records.isEmpty() }.isEmpty())
    }

    private suspend fun seedFolder(folder: Folder, items: List<PopupItemRecord>) {
        database.getFolderDao().insert(FolderRecord(id = folder.folderId))
        database.getPopupDao().insertItems(items)
        val placeId: Long = database.getWidgetAreaDao().insertPlace(WidgetPlaceRecord(position = 0, ownerKey = folder.key))
        database.getWidgetAreaDao().insertWidget(
            WidgetRecord(
                placeId = placeId,
                pagePosition = 0,
                kind = WidgetKind.APP_WIDGET,
                appWidgetId = APP_WIDGET_ID,
                provider = "com.example/ExampleWidget",
                profileSerial = 0L
            )
        )
    }

    private suspend fun seedCustomization(key: String, name: String) {
        database.getLaunchableCustomizationDao().setName(key = key, name = name)
    }

    private fun failingFavoriteAdd(): FavoriteDao {
        val mockFavoriteDao: FavoriteDao = spyk(database.getFavoriteDao())
        coEvery { mockFavoriteDao.add(any()) } throws IllegalStateException("Favorite write failed")
        return mockFavoriteDao
    }

    private fun TestScope.createFolderRepository(
        folderDao: FolderDao = database.getFolderDao(),
        launchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(otherOwnerLaunchable)),
        favoriteDao: FavoriteDao = database.getFavoriteDao(),
        customizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao(),
        mockPopupRepository: IPopupRepository = createPopupRepositoryMock()
    ): IFolderRepository = IFolderRepositoryImpl(
        folderDao = folderDao,
        favoritesRepository = createFavoritesRepository(favoriteDao, launchableRepository),
        popupRepository = mockPopupRepository,
        preferencesRepository = createPreferencesRepositoryMock(),
        launchableCustomizationDao = customizationDao
    )

    private fun TestScope.createDatabasePopupRepository(
        launchableRepository: ILaunchableRepository,
        mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(),
        mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock(),
        mockShortcutSource: IShortcutSource = createShortcutSourceMock()
    ): IPopupRepositoryImpl = createPopupRepository(
        popupDao = database.getPopupDao(),
        launchableRepository = launchableRepository,
        scope = backgroundScope,
        shortcutSource = mockShortcutSource,
        widgetAreaRepository = mockWidgetAreaRepository,
        appWidgetHost = mockAppWidgetHost
    )

    private fun createWidgetAreaRepository(): IWidgetAreaRepository {
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
        return IWidgetAreaRepositoryImpl(
            widgetAreaDao = database.getWidgetAreaDao(),
            appWidgetHost = mockAppWidgetHost,
            lifecycleReconciler = IWidgetLifecycleReconcilerImpl(dao = database.getWidgetAreaDao(), host = mockAppWidgetHost)
        )
    }
}
