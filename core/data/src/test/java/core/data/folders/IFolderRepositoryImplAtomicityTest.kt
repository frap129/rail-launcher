package core.data.folders

import core.data.favorites.FavoriteRecord
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.IPopupRepository
import core.data.popups.IPopupRepositoryImpl
import core.data.popups.PopupItemRecord
import core.data.prefs.LaunchableCustomization
import core.data.room.AppDatabase
import core.data.room.dao.FolderDao
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeFolderDao
import core.data.testing.FakeLaunchableCustomizationDao
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupRepository
import core.data.testing.FakePreferencesRepository
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.TEST_SDK
import core.data.testing.appWidgetPlace
import core.data.testing.createApp
import core.data.testing.createFavoriteDao
import core.data.testing.createFavoritesRepository
import core.data.testing.createInMemoryDatabase
import core.data.testing.createPopupRepository
import core.data.widgets.AppWidgetId
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetAreaRepositoryImpl
import core.data.widgets.IWidgetLifecycleReconcilerImpl
import core.data.widgets.WidgetKind
import core.data.widgets.WidgetPlaceRecord
import core.data.widgets.WidgetRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ITEM_PACKAGE_NAME: String = "com.example.app"
private const val OTHER_OWNER_KEY: String = "com.other_owner"
private const val FOLDER_ID: Long = 3L
private const val APP_WIDGET_ID: Int = 42

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class IFolderRepositoryImplAtomicityTest {
    private lateinit var mockDatabase: AppDatabase

    @Before
    fun createDatabase() {
        mockDatabase = createInMemoryDatabase()
    }

    @After
    fun closeDatabase() {
        mockDatabase.close()
    }

    @Test
    fun `create leaves no Folder when a Pop-up write fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.failNextSetItems.failWith(IllegalStateException("Pop-up write failed"))
        val mockCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao()
        val repository: IFolderRepository = createFolderRepository(
            mockCustomizationDao = mockCustomizationDao,
            mockPopupRepository = mockPopupRepository
        )

        val actualFailure: Throwable? = runCatching {
            repository.create(name = "Games", initialItems = listOf(inputItem.key))
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(mockDatabase.getFolderDao().observeAll().first().isEmpty())
        assertTrue(mockDatabase.getPopupDao().getAllItems().isEmpty())
        assertTrue(mockCustomizationDao.renames.isEmpty())
    }

    @Test
    fun `create rolls back the Folder and its items when the Favorite write fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val mockLaunchableRepository: FakeLaunchableRepository = createLaunchableRepository(launchables = listOf(inputItem))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        mockFavoriteDao.failNextAdd.failWith(IllegalStateException("Favorite write failed"))
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockFavoriteDao = mockFavoriteDao,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        val actualFailure: Throwable? = runCatching {
            repository.create(name = "Games", initialItems = listOf(inputItem.key))
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(mockDatabase.getFolderDao().observeAll().first().isEmpty())
        assertTrue(mockDatabase.getPopupDao().getAllItems().isEmpty())
        assertTrue(mockFavoriteDao.records.isEmpty())
    }

    @Test
    fun `create removes the Folder when the name write fails`() = runTest {
        val mockCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao()
        mockCustomizationDao.failNextSetName.failWith(IllegalStateException("Name write failed"))
        val repository: IFolderRepository = createFolderRepository(mockCustomizationDao = mockCustomizationDao)

        val actualFailure: Throwable? = runCatching { repository.create(name = "Games") }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(mockDatabase.getFolderDao().observeAll().first().isEmpty())
        assertEquals(listOf(Folder(folderId = 1L).key), mockCustomizationDao.deletedKeys)
    }

    @Test
    fun `create retries the Folder delete when its cleanup fails once`() = runTest {
        val mockFolderDao: FakeFolderDao = FakeFolderDao()
        mockFolderDao.failNextDelete.failWith(IllegalStateException("Folder delete failed"))
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        mockFavoriteDao.failNextAdd.failWith(IllegalStateException("Favorite write failed"))
        val repository: IFolderRepository = createFolderRepository(mockFolderDao = mockFolderDao, mockFavoriteDao = mockFavoriteDao)

        val actualFailure: Throwable? = runCatching { repository.create(name = "Games") }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(mockFolderDao.observeAll().first().isEmpty())
    }

    @Test
    fun `create stores the Folder, its items and its Favorite together`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val expectedFolder: Folder = Folder(folderId = 1L, name = "Games")
        val mockLaunchableRepository: FakeLaunchableRepository = createLaunchableRepository(
            launchables = listOf(inputItem),
            folders = listOf(expectedFolder)
        )
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao()
        val mockCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao()
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockFavoriteDao = mockFavoriteDao,
            mockCustomizationDao = mockCustomizationDao,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        val actualFolder: Folder = repository.create(name = "Games", initialItems = listOf(inputItem.key))

        assertEquals(expectedFolder, actualFolder)
        assertEquals(listOf(FolderRecord(id = expectedFolder.folderId)), mockDatabase.getFolderDao().observeAll().first())
        assertEquals(
            listOf(PopupItemRecord(ownerKey = expectedFolder.key, itemKey = inputItem.key, position = 0)),
            mockDatabase.getPopupDao().getAllItems()
        )
        assertEquals(listOf(FavoriteRecord(key = expectedFolder.key, position = 0)), mockFavoriteDao.records)
        assertEquals(listOf(expectedFolder.key), mockCustomizationDao.renames.map { rename -> rename.key })
        assertEquals(listOf("Games"), mockCustomizationDao.renames.map { rename -> rename.name })
    }

    @Test
    fun `delete keeps the Folder when a Pop-up item write fails`() = runTest {
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        mockDatabase.getFolderDao().insert(FolderRecord(id = inputFolder.folderId))
        val inputFavorite: FavoriteRecord = FavoriteRecord(key = inputFolder.key, position = 0)
        val mockFavoriteDao: FakeFavoriteDao = createFavoriteDao(favorite = inputFolder)
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.failNextDeleteItemsByKey.failWith(IllegalStateException("Pop-up write failed"))
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = createLaunchableRepository(launchables = emptyList(), folders = listOf(inputFolder)),
            mockFavoriteDao = mockFavoriteDao,
            mockPopupRepository = mockPopupRepository
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), mockDatabase.getFolderDao().observeAll().first())
        assertEquals(listOf(inputFavorite), mockFavoriteDao.records)
    }

    @Test
    fun `delete keeps the Folder and its Pop-up rows when the owner delete fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val inputOwnedItem: PopupItemRecord = PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0)
        val inputReferencingItem: PopupItemRecord = PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = inputFolder.key, position = 0)
        seedFolder(folder = inputFolder, items = listOf(inputOwnedItem, inputReferencingItem))
        val mockFavoriteDao: FakeFavoriteDao = createFavoriteDao(favorite = inputFolder)
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.failNextDeleteOwner.failWith(IllegalStateException("Pop-up write failed"))
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = createLaunchableRepository(launchables = listOf(inputItem), folders = listOf(inputFolder)),
            mockFavoriteDao = mockFavoriteDao,
            mockPopupRepository = mockPopupRepository
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), mockDatabase.getFolderDao().observeAll().first())
        assertEquals(setOf(inputOwnedItem, inputReferencingItem), mockDatabase.getPopupDao().getAllItems().toSet())
    }

    @Test
    fun `delete rolls back the Folder, its Pop-up rows and name when releasing Widgets fails`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val inputOwnedItem: PopupItemRecord = PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0)
        val inputReferencingItem: PopupItemRecord = PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = inputFolder.key, position = 0)
        seedFolder(folder = inputFolder, items = listOf(inputOwnedItem, inputReferencingItem))
        val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
        mockWidgetAreaRepository.failNextRemoveOwnerPlaces.failWith(IllegalStateException("Widget release failed"))
        val mockCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao(
            inputCustomizations = listOf(LaunchableCustomization(key = inputFolder.key, name = "Games"))
        )
        val mockLaunchableRepository: FakeLaunchableRepository = createLaunchableRepository(
            launchables = listOf(inputItem),
            folders = listOf(inputFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockFavoriteDao = createFavoriteDao(favorite = inputFolder),
            mockCustomizationDao = mockCustomizationDao,
            mockPopupRepository = createDatabasePopupRepository(
                launchableRepository = mockLaunchableRepository,
                mockWidgetAreaRepository = mockWidgetAreaRepository
            )
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), mockDatabase.getFolderDao().observeAll().first())
        assertEquals(setOf(inputOwnedItem, inputReferencingItem), mockDatabase.getPopupDao().getAllItems().toSet())
        assertTrue(mockCustomizationDao.deletedKeys.isEmpty())
        assertEquals(1, mockCustomizationDao.observeAll().first().size)
    }

    @Test
    fun `delete releases no Widget ids when the transaction fails after the owner delete`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val mockFolderDao: FakeFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = inputFolder.folderId)))
        mockFolderDao.failNextDelete.failWith(IllegalStateException("Folder delete failed"))
        val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L, appWidgetId = APP_WIDGET_ID, ownerKey = inputFolder.key))
        )
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val mockLaunchableRepository: FakeLaunchableRepository = createLaunchableRepository(
            launchables = listOf(inputItem),
            folders = listOf(inputFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            mockFolderDao = mockFolderDao,
            launchableRepository = mockLaunchableRepository,
            mockFavoriteDao = createFavoriteDao(favorite = inputFolder),
            mockPopupRepository = createPopupRepository(
                popupDao = mockDatabase.getPopupDao(),
                launchableRepository = mockLaunchableRepository,
                scope = backgroundScope,
                widgetAreaRepository = mockWidgetAreaRepository,
                appWidgetHost = mockAppWidgetHost
            )
        )

        val actualFailure: Throwable? = runCatching { repository.delete(inputFolder) }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertTrue(mockAppWidgetHost.deletedIds.isEmpty())
        assertEquals(listOf(FolderRecord(id = inputFolder.folderId)), mockFolderDao.observeAll().first())
    }

    @Test
    fun `delete removes the Folder, its Pop-up rows, Place, name and Favorite`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        val inputOwnedItem: PopupItemRecord = PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0)
        val inputReferencingItem: PopupItemRecord = PopupItemRecord(ownerKey = OTHER_OWNER_KEY, itemKey = inputFolder.key, position = 0)
        seedFolder(folder = inputFolder, items = listOf(inputOwnedItem, inputReferencingItem))
        val mockFavoriteDao: FakeFavoriteDao = createFavoriteDao(favorite = inputFolder)
        val mockCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao(
            inputCustomizations = listOf(LaunchableCustomization(key = inputFolder.key, name = "Games"))
        )
        val mockLaunchableRepository: FakeLaunchableRepository = createLaunchableRepository(
            launchables = listOf(inputItem),
            folders = listOf(inputFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockFavoriteDao = mockFavoriteDao,
            mockCustomizationDao = mockCustomizationDao,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        val actualFreedIds: List<AppWidgetId> = repository.delete(inputFolder)

        assertEquals(listOf(AppWidgetId(APP_WIDGET_ID)), actualFreedIds)
        assertTrue(mockDatabase.getFolderDao().observeAll().first().isEmpty())
        assertTrue(mockDatabase.getPopupDao().getAllItems().isEmpty())
        assertTrue(mockDatabase.getWidgetAreaDao().observePlaces().first().isEmpty())
        assertTrue(mockFavoriteDao.records.isEmpty())
        assertEquals(listOf(inputFolder.key), mockCustomizationDao.deletedKeys)
    }

    @Test
    fun `delete purges the Favorite when its removal fails after the Folder is gone`() = runTest {
        val inputItem: Launchable = createApp(name = "App", packageName = ITEM_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        seedFolder(
            folder = inputFolder,
            items = listOf(PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputItem.key, position = 0))
        )
        val mockFavoriteDao: FakeFavoriteDao = createFavoriteDao(favorite = inputFolder)
        mockFavoriteDao.failNextRemove.failWith(IllegalStateException("Favorite removal failed"))
        val mockLaunchableRepository: FakeLaunchableRepository = createLaunchableRepository(
            launchables = listOf(inputItem),
            folders = listOf(inputFolder)
        )
        val repository: IFolderRepository = createFolderRepository(
            launchableRepository = mockLaunchableRepository,
            mockFavoriteDao = mockFavoriteDao,
            mockPopupRepository = createDatabasePopupRepository(launchableRepository = mockLaunchableRepository)
        )

        repository.delete(inputFolder)
        mockLaunchableRepository.setFolders(folders = emptyList())
        runCurrent()

        assertTrue(mockDatabase.getFolderDao().observeAll().first().isEmpty())
        assertTrue(mockFavoriteDao.records.isEmpty())
    }

    private suspend fun seedFolder(folder: Folder, items: List<PopupItemRecord>) {
        mockDatabase.getFolderDao().insert(FolderRecord(id = folder.folderId))
        mockDatabase.getPopupDao().insertItems(items)
        val placeId: Long = mockDatabase.getWidgetAreaDao().insertPlace(WidgetPlaceRecord(position = 0, ownerKey = folder.key))
        mockDatabase.getWidgetAreaDao().insertWidget(
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

    private fun TestScope.createFolderRepository(
        mockFolderDao: FolderDao = mockDatabase.getFolderDao(),
        launchableRepository: FakeLaunchableRepository = createLaunchableRepository(launchables = emptyList()),
        mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(),
        mockCustomizationDao: FakeLaunchableCustomizationDao = FakeLaunchableCustomizationDao(),
        mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        mockPopupRepository: IPopupRepository = FakePopupRepository()
    ): IFolderRepository = IFolderRepositoryImpl(
        folderDao = mockFolderDao,
        favoritesRepository = createFavoritesRepository(mockFavoriteDao, launchableRepository),
        popupRepository = mockPopupRepository,
        preferencesRepository = mockPreferencesRepository,
        launchableCustomizationDao = mockCustomizationDao
    )

    private fun TestScope.createDatabasePopupRepository(
        launchableRepository: FakeLaunchableRepository,
        mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepository()
    ): IPopupRepositoryImpl = createPopupRepository(
        popupDao = mockDatabase.getPopupDao(),
        launchableRepository = launchableRepository,
        scope = backgroundScope,
        widgetAreaRepository = mockWidgetAreaRepository
    )

    private fun createWidgetAreaRepository(): IWidgetAreaRepository {
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        return IWidgetAreaRepositoryImpl(
            widgetAreaDao = mockDatabase.getWidgetAreaDao(),
            appWidgetHost = mockAppWidgetHost,
            lifecycleReconciler = IWidgetLifecycleReconcilerImpl(dao = mockDatabase.getWidgetAreaDao(), host = mockAppWidgetHost)
        )
    }

    private fun createLaunchableRepository(
        launchables: List<Launchable>,
        folders: List<Launchable> = emptyList()
    ): FakeLaunchableRepository = FakeLaunchableRepository(
        groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = launchables)),
        folders = folders
    )
}
