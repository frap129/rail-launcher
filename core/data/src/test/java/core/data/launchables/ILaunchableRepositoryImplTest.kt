package core.data.launchables

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.IAppRepository
import core.data.apps.model.App
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
import core.data.testing.FakePreferencesRepository
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createFavoritesRepository
import core.data.testing.failOnRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_USER_ID: Int = 0
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val ICON_PACK_PACKAGE_NAME: String = "com.example.iconpack"
private const val ICON_PACK_RES_ID: Int = 42
private const val ORIGINAL_NAME: String = "Original"
private const val RENAMED_NAME: String = "Renamed"
private const val INITIAL_RETRY_DELAY_MS: Long = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class ILaunchableRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)

    @Test
    fun `shares one upstream collection between every subscriber`() = runTest {
        val inputApp: App = createApp()
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(listOf(inputApp))
        val mockAppRepository: IAppRepository = createAppRepository(inputAppState = inputAppState)
        val mockIconRepository: IIconRepository = createIconRepository(inputIcon = Icon.ActivityIcon(inputComponentName))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = mockAppRepository,
            mockPreferencesRepository = FakePreferencesRepository(inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)),
            mockIconRepository = mockIconRepository
        )
        database.getFavoriteDao().add(inputApp.key)
        val favoritesRepository: IFavoritesRepositoryImpl = createFavoritesRepository(
            favoriteDao = database.getFavoriteDao(),
            launchableRepository = repository
        )
        backgroundScope.launch { repository.launchableState.collect() }
        backgroundScope.launch { repository.launchables.collect() }
        backgroundScope.launch { favoritesRepository.favorites.collect() }
        val actualState: LaunchableState = repository.launchableState.first { it.isLoaded }
        val actualMenuItems: List<Launchable> = repository.launchables.first { it.isNotEmpty() }
        val actualFavorites: List<Launchable> = favoritesRepository.favorites.first { it.isNotEmpty() }

        assertEquals(1, inputAppState.subscriptionCount.value)
        coVerify(exactly = 1) { mockIconRepository.getIcon(any(), any()) }
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
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(listOf(inputApp))
        val mockFolderDao: FolderDao = mockk()
        val failureRequests: Channel<Unit> = Channel(Channel.UNLIMITED)
        val actualFailureDelivered: CompletableDeferred<Unit> = CompletableDeferred()
        var actualCollections: Int = 0
        every { mockFolderDao.observeAll() } returns database.getFolderDao().observeAll()
            .onStart { actualCollections++ }
            .failOnRequest(failureRequests = failureRequests, onFailure = { actualFailureDelivered.complete(Unit) })
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(inputAppState = inputAppState),
            folderDao = mockFolderDao,
            scope = backgroundScope
        )
        backgroundScope.launch { repository.launchableState.collect() }
        repository.launchableState.first { it.isLoaded }
        assertEquals(1, actualCollections)
        failureRequests.send(Unit)
        failureRequests.send(Unit)
        actualFailureDelivered.await()
        runCurrent()
        inputAppState.value = listOf(inputRenamedApp)
        runCurrent()
        assertEquals(1, actualCollections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(1, actualCollections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, actualCollections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(2 * INITIAL_RETRY_DELAY_MS - 1)
        runCurrent()
        assertEquals(2, actualCollections)
        assertEquals(ORIGINAL_NAME, repository.launchableState.value.singleAppName())
        advanceTimeBy(1)
        runCurrent()
        val actualRecoveredName: String = repository.launchableState
            .first { state -> state.launchables.filterIsInstance<App>().singleOrNull()?.defaultName == RENAMED_NAME }
            .singleAppName()
        assertEquals(RENAMED_NAME, actualRecoveredName)
        assertEquals(3, actualCollections)
    }

    @Test
    fun `resolves Launchable State on the default dispatcher`() = runTest {
        val actualThreadNames: MutableList<String> = mutableListOf()
        val mockIconRepository: IIconRepository = createIconRepository(
            inputIcon = Icon.ActivityIcon(inputComponentName),
            inputThreadNames = actualThreadNames
        )
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(inputApps = listOf(createApp())),
            mockPreferencesRepository = FakePreferencesRepository(inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)),
            mockIconRepository = mockIconRepository
        )

        repository.launchableState.first { it.isLoaded }

        assertTrue(actualThreadNames.single().startsWith("DefaultDispatcher-worker"))
    }

    @Test
    fun `keeps the Star and Builtin groups when there are no Apps`() = runTest {
        val repository: ILaunchableRepository = createRepository(mockAppRepository = createAppRepository())

        val actualLabels: List<String> = repository.launchableState.first { it.isLoaded }.groups.map { group -> group.label }

        assertEquals(listOf("★", "⌂"), actualLabels)
    }

    @Test
    fun `resolves Builtin Launchables alongside Apps`() = runTest {
        val repository: ILaunchableRepository = createRepository(mockAppRepository = createAppRepository())
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
            mockAppRepository = createAppRepository(inputApps = listOf(inputApp)),
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
            mockAppRepository = createAppRepository(inputApps = listOf(inputApp)),
            mockPreferencesRepository = FakePreferencesRepository(
                inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)
            ),
            mockIconRepository = createIconRepository(inputIcon = expectedIcon)
        )

        val actualApp: App = repository.launchableState.first { it.isLoaded }.launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(expectedIcon, actualApp.icon)
        assertEquals(Icon.ActivityIcon(inputComponentName), inputApp.icon)
    }

    @Test
    fun `Folders appear in the group for their resolved names`() = runTest {
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(),
            mockPreferencesRepository = FakePreferencesRepository(
                inputCustomizations = listOf(LaunchableCustomization(key = Folder(folderId = 1L).key, name = "Games"))
            ),
            folderDao = folderDao
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
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(inputApps = listOf(createApp())),
            folderDao = folderDao
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
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputApp.key, position = 0)))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(inputApps = listOf(inputApp)),
            folderDao = folderDao,
            popupDao = popupDao
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
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val popupDao: PopupDao = database.getPopupDao()
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(inputApps = listOf(inputApp)),
            folderDao = folderDao,
            popupDao = popupDao
        )
        val actualBefore: LaunchableState = repository.launchableState.first { state -> state.isLoaded }
        assertTrue(actualBefore.listedGroups.any { group -> group.items.any { item -> item.key == inputApp.key } })

        popupDao.appendItems(ownerKey = inputFolder.key, itemKeys = listOf(inputApp.key))
        val actualHidden: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.none { group -> group.items.any { item -> item.key == inputApp.key } }
        }

        assertTrue(actualHidden.launchables.any { launchable -> launchable.key == inputApp.key })

        popupDao.removeItems(ownerKey = inputFolder.key, itemKeys = listOf(inputApp.key))
        val actualShown: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.any { group -> group.items.any { item -> item.key == inputApp.key } }
        }

        assertTrue(actualShown.launchables.any { launchable -> launchable.key == inputApp.key })
    }

    @Test
    fun `adds and removes a Folder from the App List and Rail live`() = runTest {
        val folderDao: FolderDao = database.getFolderDao()
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(),
            folderDao = folderDao
        )
        val actualBefore: LaunchableState = repository.launchableState.first { state -> state.isLoaded }

        val folderId: Long = folderDao.insert(record = FolderRecord())
        val actualAdded: LaunchableState = repository.launchableState.first { state ->
            state.listedGroups.any { group -> group.items.any { item -> item is Folder && item.folderId == folderId } }
        }

        folderDao.delete(id = folderId)
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
            mockAppRepository = createAppRepository(inputApps = listOf(createApp(name = "⌂ Game")))
        )

        val actualGroups: List<LaunchableGroup.Listed> = repository.launchableState.first { state -> state.isLoaded }.listedGroups
        val actualKeys: List<String> = actualGroups.map { group -> group.appListKey() }

        assertEquals(listOf("⌂", "⌂"), actualGroups.map { group -> group.label })
        assertEquals(listOf("initial:⌂", "builtin:⌂"), actualKeys)
    }

    @Test
    fun `icon packs never replace Folder icons`() = runTest {
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepository(),
            mockPreferencesRepository = FakePreferencesRepository(inputSettings = Settings(iconPack = ICON_PACK_PACKAGE_NAME)),
            folderDao = folderDao
        )

        val actualFolder: Folder = repository.launchableState.first { state -> state.isLoaded }
            .launchables.filterIsInstance<Folder>().single()

        assertEquals(Icon.FolderIcon(shade = 1), actualFolder.icon)
    }

    private fun createAppRepository(inputApps: List<App> = emptyList()): IAppRepository =
        createAppRepository(inputAppState = MutableStateFlow(inputApps))

    private fun createAppRepository(inputAppState: StateFlow<List<App>?>): IAppRepository = mockk<IAppRepository>().apply {
        every { apps } returns inputAppState
    }

    private fun createIconRepository(inputIcon: Icon, inputThreadNames: MutableList<String> = mutableListOf()): IIconRepository =
        mockk<IIconRepository>().apply {
            every { iconPacks } returns MutableStateFlow(setOf(IconPack.SystemIconPack))
            coEvery { getIcon(any(), any()) } coAnswers {
                inputThreadNames += Thread.currentThread().name
                inputIcon
            }
        }

    private fun TestScope.createRepository(
        mockAppRepository: IAppRepository,
        mockPreferencesRepository: IPreferencesRepository = FakePreferencesRepository(),
        mockIconRepository: IIconRepository = createIconRepository(inputIcon = Icon.ActivityIcon(inputComponentName)),
        folderDao: FolderDao = database.getFolderDao(),
        popupDao: PopupDao = database.getPopupDao(),
        scope: CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.Default)
    ): ILaunchableRepository = ILaunchableRepositoryImpl(
        appRepository = mockAppRepository,
        iconRepository = mockIconRepository,
        preferencesRepository = mockPreferencesRepository,
        folderDao = folderDao,
        popupDao = popupDao,
        scope = scope
    )

    private fun createApp(name: String = ORIGINAL_NAME): App = App(
        defaultName = name,
        profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
        componentName = inputComponentName
    )

    private fun LaunchableState.singleAppName(): String = launchables.filterIsInstance<App>().single().name
}
