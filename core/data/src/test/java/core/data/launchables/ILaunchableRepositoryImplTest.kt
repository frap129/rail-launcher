package core.data.launchables

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.IAppRepository
import core.data.apps.model.App
import core.data.favorites.FavoriteRecord
import core.data.favorites.IFavoritesRepositoryImpl
import core.data.folders.Folder
import core.data.folders.FolderRecord
import core.data.icons.IIconRepository
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.appListKey
import core.data.popups.PopupItemRecord
import core.data.prefs.IPreferencesRepository
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.room.dao.FolderDao
import core.data.room.dao.PopupDao
import core.data.testing.FIRST_POSITION
import core.data.testing.FakeFavoriteDao
import core.data.testing.FakeFolderDao
import core.data.testing.FakePopupDao
import core.data.testing.FakePreferencesRepository
import core.data.testing.TEST_SDK
import core.data.testing.createFavoritesRepository
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_USER_ID: Int = 0
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_RES_ID: Int = 42
private const val ORIGINAL_NAME: String = "Original"
private const val RENAMED_NAME: String = "Renamed"
private const val INITIAL_RETRY_DELAY_MS: Long = 1_000L

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class ILaunchableRepositoryImplTest {
    private val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)

    @Test
    fun `shares one upstream collection between every subscriber`() = runTest {
        val inputApp: App = createApp()
        val mockAppRepository: FakeAppRepository = FakeAppRepository(inputApps = listOf(inputApp))
        val mockIconRepository: FakeIconRepository = FakeIconRepository(inputIcon = Icon.ActivityIcon(inputComponentName))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = mockAppRepository,
            mockPreferencesRepository = FakePreferencesRepository(inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)),
            mockIconRepository = mockIconRepository
        )
        val mockFavoriteDao: FakeFavoriteDao = FakeFavoriteDao(
            initialRecords = listOf(FavoriteRecord(key = inputApp.key, position = FIRST_POSITION))
        )
        val favoritesRepository: IFavoritesRepositoryImpl = createFavoritesRepository(
            favoriteDao = mockFavoriteDao,
            launchableRepository = repository
        )
        val actualStateDeferred: Deferred<LaunchableState> = async { repository.launchableState.first { it.isLoaded } }
        // The item menu reads the same launchables flow.
        val actualMenuItemsDeferred: Deferred<List<Launchable>> = async { repository.launchables.first { it.isNotEmpty() } }
        val actualFavoritesDeferred: Deferred<List<Launchable>> = async { favoritesRepository.favorites.first { it.isNotEmpty() } }
        val actualState: LaunchableState = actualStateDeferred.await()
        val actualMenuItems: List<Launchable> = actualMenuItemsDeferred.await()
        val actualFavorites: List<Launchable> = actualFavoritesDeferred.await()

        assertEquals(1, mockAppRepository.collections)
        assertEquals(1, mockIconRepository.iconRequests)
        assertTrue(actualState.isLoaded)
        assertEquals(listOf("O", "⌂"), actualState.listedGroups.map { group -> group.label })
        assertTrue(actualMenuItems.isNotEmpty())
        assertTrue(actualFavorites.isNotEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `restarts a failed upstream with exponential backoff and recovers`() = runTest {
        val inputApp: App = createApp()
        val inputRenamedApp: App = createApp(name = RENAMED_NAME)
        val mockAppRepository: FakeAppRepository = FakeAppRepository(inputApps = listOf(inputApp))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = mockAppRepository,
            scope = backgroundScope
        )
        backgroundScope.launch { repository.launchableState.collect() }
        runCurrent()
        assertTrue(repository.launchableState.value.isLoaded)
        mockAppRepository.failNextCollections(count = 2)
        mockAppRepository.setApps(listOf(inputRenamedApp))
        runCurrent()
        assertEquals(1, mockAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(1, mockAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, mockAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(2 * INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(2, mockAppRepository.collections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, mockAppRepository.collections)
        assertEquals(RENAMED_NAME, repository.launchableState.value.singleAppName())
    }

    @Test
    fun `resolves Launchable State on the default dispatcher`() = runTest {
        val mockAppRepository: FakeAppRepository = FakeAppRepository(inputApps = emptyList())
        val repository: ILaunchableRepository = createRepository(mockAppRepository = mockAppRepository)

        repository.launchableState.first { it.isLoaded }

        val actualThreadName: String = mockAppRepository.collectionThreadNames.single()
        assertTrue(actualThreadName.startsWith("DefaultDispatcher-worker"))
    }

    @Test
    fun `keeps the Star and Builtin groups when there are no Apps`() = runTest {
        val repository: ILaunchableRepository = createRepository(mockAppRepository = FakeAppRepository(inputApps = emptyList()))

        val actualLabels: List<String> = repository.launchableState.first { it.isLoaded }.groups.map { group -> group.label }

        assertEquals(listOf("★", "⌂"), actualLabels)
    }

    @Test
    fun `resolves Builtin Launchables alongside Apps`() = runTest {
        val repository: ILaunchableRepository = createRepository(mockAppRepository = FakeAppRepository(inputApps = emptyList()))
        val actualState: LaunchableState = repository.launchableState.first { it.isLoaded }
        val actualBuiltinItems: List<Launchable> = actualState.groups
            .filterIsInstance<LaunchableGroup.Builtin>()
            .flatMap { group -> group.items }

        assertTrue(actualBuiltinItems.isNotEmpty())
        assertTrue(actualState.launchables.containsAll(actualBuiltinItems))
    }

    @Test
    fun `resolves a renamed App into a new instance and leaves the original unchanged`() = runTest {
        val inputApp: App = createApp()
        val mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository(
            inputCustomizations = listOf(LaunchableCustomization(key = inputApp.key, name = RENAMED_NAME))
        )
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = listOf(inputApp)),
            mockPreferencesRepository = mockPreferencesRepository
        )

        val actualApp: App = repository.launchableState.first { it.isLoaded }.launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(RENAMED_NAME, actualApp.name)
        assertEquals(ORIGINAL_NAME, inputApp.name)
        assertEquals(Icon.ActivityIcon(inputComponentName), inputApp.icon)
    }

    @Test
    fun `resolves an icon pack into a new App instance and leaves the original unchanged`() = runTest {
        val inputApp: App = createApp()
        val expectedIcon: Icon = Icon.PackIcon(
            componentName = inputComponentName,
            packPackageName = ICON_PACK_PACKAGE_NAME,
            resId = ICON_PACK_RES_ID
        )
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = listOf(inputApp)),
            mockPreferencesRepository = FakePreferencesRepository(
                inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)
            ),
            mockIconRepository = FakeIconRepository(inputIcon = expectedIcon)
        )

        val actualApp: App = repository.launchableState.first { it.isLoaded }.launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(expectedIcon, actualApp.icon)
        assertEquals(Icon.ActivityIcon(inputComponentName), inputApp.icon)
    }

    private fun TestScope.createRepository(
        mockAppRepository: IAppRepository,
        mockPreferencesRepository: IPreferencesRepository = FakePreferencesRepository(),
        mockIconRepository: IIconRepository = FakeIconRepository(
            inputIcon = Icon.ActivityIcon(inputComponentName)
        ),
        mockFolderDao: FolderDao = FakeFolderDao(),
        mockPopupDao: PopupDao = FakePopupDao(),
        scope: CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.Default)
    ): ILaunchableRepository = ILaunchableRepositoryImpl(
        appRepository = mockAppRepository,
        iconRepository = mockIconRepository,
        preferencesRepository = mockPreferencesRepository,
        folderDao = mockFolderDao,
        popupDao = mockPopupDao,
        scope = scope
    )

    private fun createApp(name: String = ORIGINAL_NAME): App = App(
        defaultName = name,
        profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
        componentName = inputComponentName
    )

    private fun LaunchableState.singleAppName(): String = launchables.filterIsInstance<App>().single().name

    @Test
    fun `Folders appear in the group for their resolved names`() = runTest {
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = emptyList()),
            mockPreferencesRepository = FakePreferencesRepository(
                inputCustomizations = listOf(LaunchableCustomization(key = Folder(folderId = 1L).key, name = "Games"))
            ),
            mockFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = 1L)))
        )

        val actualState: LaunchableState = repository.launchableState.first { state -> state.isLoaded }
        val actualFolder: Folder = actualState.launchables.filterIsInstance<Folder>().single()
        val actualGroup: LaunchableGroup.InitialGroup = actualState.listedGroups
            .filterIsInstance<LaunchableGroup.InitialGroup>()
            .single { group -> group.label == "G" }

        assertEquals("Games", actualFolder.name)
        assertEquals(listOf("★", "G", "⌂"), actualState.groups.map { group -> group.label })
        assertEquals(listOf(actualFolder), actualGroup.items)
    }

    @Test
    fun `groups Folders alongside Apps by their first character`() = runTest {
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = listOf(createApp())),
            mockFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = 1L)))
        )

        val actualState: LaunchableState = repository.launchableState.first { state -> state.isLoaded }
        val actualFolder: Folder = actualState.launchables.filterIsInstance<Folder>().single()
        val actualFolderGroup: LaunchableGroup.InitialGroup = actualState.listedGroups
            .filterIsInstance<LaunchableGroup.InitialGroup>()
            .single { group -> group.label == "F" }

        assertEquals(listOf("★", "F", "O", "⌂"), actualState.groups.map { group -> group.label })
        assertEquals(listOf(actualFolder), actualFolderGroup.items)
    }

    @Test
    fun `hides Folder members from the App List but keeps them launchable`() = runTest {
        val inputApp: App = createApp()
        val inputFolder: Folder = Folder(folderId = 1L)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = listOf(inputApp)),
            mockFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = 1L))),
            mockPopupDao = FakePopupDao(
                initialItems = listOf(PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputApp.key, position = 0))
            )
        )

        val actualState: LaunchableState = repository.launchableState.first { state -> state.isLoaded }

        assertEquals(listOf("★", "F", "⌂"), actualState.groups.map { group -> group.label })
        assertTrue(actualState.launchables.any { launchable -> launchable.key == inputApp.key })
        assertTrue(actualState.pickerGroups.any { group -> group.items.any { item -> item.key == inputApp.key } })
    }

    @Test
    fun `updates the App List when Folder membership changes`() = runTest {
        val inputApp: App = createApp()
        val inputFolder: Folder = Folder(folderId = 1L)
        val mockPopupDao: FakePopupDao = FakePopupDao()
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = listOf(inputApp)),
            mockFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = 1L))),
            mockPopupDao = mockPopupDao
        )
        val actualBefore: LaunchableState = repository.launchableState.first { state -> state.isLoaded }
        assertTrue(actualBefore.listedGroups.any { group -> group.items.any { item -> item.key == inputApp.key } })

        mockPopupDao.appendItems(ownerKey = inputFolder.key, itemKeys = listOf(inputApp.key))
        val actualHidden: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.none { group -> group.items.any { item -> item.key == inputApp.key } }
        }

        assertTrue(actualHidden.launchables.any { launchable -> launchable.key == inputApp.key })

        mockPopupDao.removeItems(ownerKey = inputFolder.key, itemKeys = listOf(inputApp.key))
        val actualShown: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.any { group -> group.items.any { item -> item.key == inputApp.key } }
        }

        assertTrue(actualShown.launchables.any { launchable -> launchable.key == inputApp.key })
    }

    @Test
    fun `adds and removes a Folder from the App List and Rail live`() = runTest {
        val mockFolderDao: FakeFolderDao = FakeFolderDao()
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = emptyList()),
            mockFolderDao = mockFolderDao
        )
        val actualBefore: LaunchableState = repository.launchableState.first { state -> state.isLoaded }

        val folderId: Long = mockFolderDao.insert(record = FolderRecord())
        val actualAdded: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.any { group -> group.items.any { item -> item is Folder && item.folderId == folderId } }
        }

        mockFolderDao.delete(id = folderId)
        val actualRemoved: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.none { group -> group.items.any { item -> item is Folder } }
        }

        assertEquals(listOf("★", "⌂"), actualBefore.groups.map { group -> group.label })
        assertEquals(listOf("★", "F", "⌂"), actualAdded.groups.map { group -> group.label })
        assertEquals(listOf("★", "⌂"), actualRemoved.groups.map { group -> group.label })
    }

    @Test
    fun `groups an app named with the Builtin label apart from Builtin`() = runTest {
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = listOf(createApp(name = "⌂ Game")))
        )

        val actualGroups: List<LaunchableGroup.Listed> = repository.launchableState.first { state -> state.isLoaded }.listedGroups
        val actualKeys: List<String> = actualGroups.map { group -> group.appListKey() }

        assertEquals(listOf("⌂", "⌂"), actualGroups.map { group -> group.label })
        assertEquals(listOf("initial:⌂", "builtin:⌂"), actualKeys)
    }

    @Test
    fun `icon packs never replace Folder icons`() = runTest {
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = FakeAppRepository(inputApps = emptyList()),
            mockPreferencesRepository = FakePreferencesRepository(inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)),
            mockFolderDao = FakeFolderDao(initialRecords = listOf(FolderRecord(id = 1L)))
        )

        val actualFolder: Folder = repository.launchableState.first { state -> state.isLoaded }
            .launchables.filterIsInstance<Folder>().single()

        assertEquals(Icon.FolderIcon(shade = 1), actualFolder.icon)
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class FakeAppRepository(inputApps: List<App>) : IAppRepository {
    private val state: MutableStateFlow<List<App>?> = MutableStateFlow(inputApps)
    private val collectionCounter: AtomicInteger = AtomicInteger(0)
    private val remainingFailures: AtomicInteger = AtomicInteger(0)
    private val recordedThreadNames: MutableList<String> = Collections.synchronizedList(mutableListOf())
    override val apps: StateFlow<List<App>?> = object : StateFlow<List<App>?> by state {
        override suspend fun collect(collector: FlowCollector<List<App>?>): Nothing {
            collectionCounter.incrementAndGet()
            recordedThreadNames.add(Thread.currentThread().name)
            return state.collect { apps ->
                if (remainingFailures.getAndUpdate { count -> if (count > 0) count - 1 else 0 } > 0) {
                    throw IllegalStateException("Apps upstream failure")
                }
                collector.emit(apps)
            }
        }
    }

    val collections: Int get() = collectionCounter.get()
    val collectionThreadNames: List<String> get() = recordedThreadNames.toList()

    fun failNextCollections(count: Int) {
        remainingFailures.set(count)
    }

    fun setApps(apps: List<App>) {
        state.value = apps
    }
}

private class FakeIconRepository(private val inputIcon: Icon) : IIconRepository {
    private val iconRequestCounter: AtomicInteger = AtomicInteger(0)
    override val iconPacks: StateFlow<Set<IconPack>> = MutableStateFlow(setOf(IconPack.SystemIconPack))
    val iconRequests: Int get() = iconRequestCounter.get()

    override suspend fun getIcon(packPackageName: String, componentName: ComponentName): Icon {
        iconRequestCounter.incrementAndGet()
        return inputIcon
    }
}
