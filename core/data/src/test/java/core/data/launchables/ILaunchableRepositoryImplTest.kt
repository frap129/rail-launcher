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
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.appListKey
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.popups.PopupItemRecord
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderTapSetting
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
import core.data.prefs.LaunchableCustomization
import core.data.prefs.Settings
import core.data.profiles.IProfileSource
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.toProfileToken
import core.data.room.dao.FolderDao
import core.data.room.dao.LaunchableCustomizationDao
import core.data.room.dao.PopupDao
import core.data.testing.FIRST_POSITION
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.MISSING_PRIVATE_USER_ID
import core.data.testing.MISSING_WORK_USER_ID
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.PRIVATE_PACKAGE_NAME
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.ProfileSourceState
import core.data.testing.SECOND_POSITION
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.WORK_USER_ID
import core.data.testing.createApp
import core.data.testing.createAppRepositoryMock
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createFavoritesRepository
import core.data.testing.createIconRepositoryMock
import core.data.testing.createPopupRepository
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createProfileInfo
import core.data.testing.createProfileSourceMock
import core.data.testing.createShortcutSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.failOnRequest
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.IWidgetSettingsRepositoryImpl
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val APP_ACTIVITY_NAME: String = "com.example.app.MainActivity"
private const val ICON_PACK_RES_ID: Int = 42
private const val ORIGINAL_NAME: String = "Original"
private const val RENAMED_NAME: String = "Renamed"
private const val INITIAL_RETRY_DELAY_MS: Long = 1_000L
private const val FOLDER_ID: Long = 1L

@OptIn(ExperimentalCoroutinesApi::class)
class ILaunchableRepositoryImplTest : InMemoryDatabaseTest() {
    private val inputComponentName: ComponentName = ComponentName(APP_PACKAGE_NAME, APP_ACTIVITY_NAME)

    @Test
    fun `keeps a Locked Profile's name customization and removes it once the Profile is gone`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(
            name = "Secret",
            packageName = PRIVATE_PACKAGE_NAME,
            profileInfo = inputPrivateProfile
        )
        val inputPersonalApp: App = createApp(name = "Personal", packageName = "com.example.personal")
        val inputCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
        inputCustomizationDao.setName(key = inputPrivateApp.key, name = "Secret name")
        inputCustomizationDao.setName(key = inputPersonalApp.key, name = "Personal name")
        val inputProfileState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(inputPrivateProfile.copy(isQuiet = true))
        )
        val inputPreferencesRepository: IPreferencesRepository =
            createPreferencesRepository(launchableCustomizationDao = inputCustomizationDao)
        createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputPersonalApp)),
            mockPreferencesRepository = inputPreferencesRepository,
            mockProfileSource = createProfileSourceMock(state = inputProfileState)
        )
        finishPendingWork()

        assertEquals(2, inputCustomizationDao.observeAll().first().size)

        inputProfileState.profiles.value = emptyList()
        finishPendingWork()

        assertEquals(
            listOf(LaunchableCustomization(key = inputPersonalApp.key, name = "Personal name")),
            inputCustomizationDao.observeAll().first()
        )
    }

    @Test
    fun `shares one upstream collection between every subscriber`() = databaseTest {
        val inputApp: App = createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(listOf(inputApp))
        val mockAppRepository: IAppRepository = createAppRepositoryMock(inputAppState = inputAppState)
        val mockIconRepository: IIconRepository = createIconRepositoryMock(inputIcon = Icon.ActivityIcon(inputComponentName))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = mockAppRepository,
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)),
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
        finishPendingWork()
        val actualState: LaunchableState = repository.launchableState.first()
        val actualMenuItems: List<Launchable> = repository.launchables.first()
        val actualFavorites: List<Launchable> = favoritesRepository.favorites.first()

        assertEquals(1, inputAppState.subscriptionCount.value)
        coVerify(exactly = 1) { mockIconRepository.getIcon(any(), any(), any()) }
        assertTrue(actualState.isLoaded)
        assertEquals(listOf("O", "⌂"), actualState.listedGroups.map { group -> group.label })
        assertTrue(actualMenuItems.isNotEmpty())
        assertTrue(actualFavorites.isNotEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `restarts a failed upstream with exponential backoff and recovers`() = databaseTest {
        val inputApp: App = createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)
        val inputRenamedApp: App = createApp(name = RENAMED_NAME, packageName = APP_PACKAGE_NAME)
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(listOf(inputApp))
        val mockFolderDao: FolderDao = mockk()
        val failureRequests: Channel<Unit> = Channel(Channel.UNLIMITED)
        val actualFailureDelivered: CompletableDeferred<Unit> = CompletableDeferred()
        var actualCollections: Int = 0
        every { mockFolderDao.observeAll() } returns database.getFolderDao().observeAll()
            .onStart { actualCollections++ }
            .failOnRequest(failureRequests = failureRequests, onFailure = { actualFailureDelivered.complete(Unit) })
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputAppState = inputAppState),
            folderDao = mockFolderDao,
            scope = backgroundScope
        )
        backgroundScope.launch { repository.launchableState.collect() }
        loadedState(repository)
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
        val actualRecoveredName: String = repository.launchableState.value.singleAppName()
        assertEquals(RENAMED_NAME, actualRecoveredName)
        assertEquals(3, actualCollections)
    }

    @Test
    fun `resolves Launchable State only when the injected dispatcher runs`() = databaseTest {
        val inputDispatcher: TestDispatcher = StandardTestDispatcher(testScheduler)
        val inputAppState: MutableStateFlow<List<App>?> =
            MutableStateFlow(listOf(createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)))
        val mockIconRepository: IIconRepository = createIconRepositoryMock(inputIcon = Icon.ActivityIcon(inputComponentName))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputAppState = inputAppState),
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)),
            mockIconRepository = mockIconRepository,
            scope = CoroutineScope(inputDispatcher)
        )
        val collection: Job = launch { repository.launchableState.collect { } }
        try {
            assertFalse(repository.launchableState.value.isLoaded)
            coVerify(exactly = 0) { mockIconRepository.getIcon(any(), any(), any()) }

            advanceUntilIdle()
            assertEquals(1, inputAppState.subscriptionCount.value)
            assertTrue(repository.launchableState.value.isLoaded)
            coVerify(exactly = 1) { mockIconRepository.getIcon(any(), any(), any()) }
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun `keeps the Star and Builtin groups when there are no Apps`() = databaseTest {
        val repository: ILaunchableRepository = createRepository(mockAppRepository = createAppRepositoryMock())

        val actualLabels: List<String> = loadedState(repository).groups.map { group -> group.label }

        assertEquals(listOf("★", "⌂"), actualLabels)
    }

    @Test
    fun `resolves Builtin Launchables alongside Apps`() = databaseTest {
        val repository: ILaunchableRepository = createRepository(mockAppRepository = createAppRepositoryMock())
        val actualState: LaunchableState = loadedState(repository)
        val actualBuiltinItems: List<Launchable> = actualState.groups
            .filterIsInstance<LaunchableGroup.Builtin>()
            .flatMap { group -> group.items }

        assertTrue(actualBuiltinItems.isNotEmpty())
        assertTrue(actualState.launchables.containsAll(actualBuiltinItems))
    }

    @Test
    fun `resolves a renamed App into a new instance and leaves the original unchanged`() = databaseTest {
        val inputApp: App = createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(
            customizations = listOf(LaunchableCustomization(key = inputApp.key, name = RENAMED_NAME))
        )
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputApp)),
            mockPreferencesRepository = mockPreferencesRepository
        )

        val actualApp: App = loadedState(repository).launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(RENAMED_NAME, actualApp.name)
        assertEquals(ORIGINAL_NAME, inputApp.name)
        assertEquals(Icon.ActivityIcon(inputComponentName), inputApp.icon)
    }

    @Test
    fun `badges a renamed work App's displayed name for accessibility`() = databaseTest {
        val inputProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputApp: App = App(
            defaultName = ORIGINAL_NAME,
            profileInfo = inputProfile,
            componentName = inputComponentName,
            badgedLabel = "Work $ORIGINAL_NAME"
        )
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputProfile)).apply {
            badgedLabel = { label, _ -> "Work $label" }
        }
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputApp)),
            mockPreferencesRepository = createPreferencesRepositoryMock(
                customizations = listOf(LaunchableCustomization(key = inputApp.key, name = RENAMED_NAME))
            ),
            mockProfileSource = createProfileSourceMock(state = inputProfileState)
        )

        val actualApp: App = loadedState(repository).launchables.filterIsInstance<App>().single()

        assertEquals(RENAMED_NAME, actualApp.name)
        assertEquals("Work $RENAMED_NAME", actualApp.accessibilityName)
    }

    @Test
    fun `resolves an icon pack into a new App instance and leaves the original unchanged`() = databaseTest {
        val inputApp: App = createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)
        val expectedIcon: Icon = Icon.PackIcon(
            componentName = inputComponentName,
            packPackageName = TEST_ICON_PACK_PACKAGE,
            resId = ICON_PACK_RES_ID
        )
        val mockIconRepository: IIconRepository = createIconRepositoryMock(inputIcon = expectedIcon)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputApp)),
            mockPreferencesRepository = createPreferencesRepositoryMock(
                settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)
            ),
            mockIconRepository = mockIconRepository
        )

        val actualApp: App = loadedState(repository).launchables.filterIsInstance<App>().single()

        assertNotSame(inputApp, actualApp)
        assertEquals(expectedIcon, actualApp.icon)
        assertEquals(Icon.ActivityIcon(inputComponentName), inputApp.icon)
        coVerify(exactly = 1) {
            mockIconRepository.getIcon(
                packPackageName = TEST_ICON_PACK_PACKAGE,
                componentName = inputComponentName,
                profile = inputApp.profileInfo
            )
        }
    }

    @Test
    fun `Folders appear in the group for their resolved names`() = databaseTest {
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(),
            mockPreferencesRepository = createPreferencesRepositoryMock(
                customizations = listOf(LaunchableCustomization(key = Folder(folderId = 1L).key, name = "Games"))
            ),
            folderDao = folderDao
        )

        val actualState: LaunchableState = loadedState(repository)
        val actualFolder: Folder = actualState.launchables.filterIsInstance<Folder>().single()
        val actualGroup: LaunchableGroup.InitialGroup = actualState.listedGroups
            .filterIsInstance<LaunchableGroup.InitialGroup>()
            .single { group -> group.label == "G" }

        assertEquals("Games", actualFolder.name)
        assertEquals(listOf("★", "G", "⌂"), actualState.groups.map { group -> group.label })
        assertEquals(listOf(actualFolder), actualGroup.items)
    }

    @Test
    fun `groups Folders alongside Apps by their first character`() = databaseTest {
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(
                inputApps = listOf(createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME))
            ),
            folderDao = folderDao
        )

        val actualState: LaunchableState = loadedState(repository)
        val actualFolder: Folder = actualState.launchables.filterIsInstance<Folder>().single()
        val actualFolderGroup: LaunchableGroup.InitialGroup = actualState.listedGroups
            .filterIsInstance<LaunchableGroup.InitialGroup>()
            .single { group -> group.label == "F" }

        assertEquals(listOf("★", "F", "O", "⌂"), actualState.groups.map { group -> group.label })
        assertEquals(listOf(actualFolder), actualFolderGroup.items)
    }

    @Test
    fun `hides Folder members from the App List but keeps them launchable`() = databaseTest {
        val inputApp: App = createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = 1L)
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputApp.key, position = 0)))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputApp)),
            folderDao = folderDao,
            popupDao = popupDao
        )

        val actualState: LaunchableState = loadedState(repository)

        assertEquals(listOf("★", "F", "⌂"), actualState.groups.map { group -> group.label })
        assertTrue(actualState.launchables.any { launchable -> launchable.key == inputApp.key })
        assertTrue(actualState.pickerGroups.any { group -> group.items.any { item -> item.key == inputApp.key } })
    }

    @Test
    fun `updates the App List when Folder membership changes`() = databaseTest {
        val inputApp: App = createApp(name = ORIGINAL_NAME, packageName = APP_PACKAGE_NAME)
        val inputFolder: Folder = Folder(folderId = 1L)
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val popupDao: PopupDao = database.getPopupDao()
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputApp)),
            folderDao = folderDao,
            popupDao = popupDao
        )
        val actualBefore: LaunchableState = loadedState(repository)
        assertTrue(actualBefore.listedGroups.any { group -> group.items.any { item -> item.key == inputApp.key } })

        popupDao.appendItems(ownerKey = inputFolder.key, itemKeys = listOf(inputApp.key))
        val actualHidden: LaunchableState = loadedState(repository)

        assertTrue(actualHidden.launchables.any { launchable -> launchable.key == inputApp.key })

        popupDao.removeItems(ownerKey = inputFolder.key, itemKeys = listOf(inputApp.key))
        val actualShown: LaunchableState = loadedState(repository)

        assertTrue(actualShown.launchables.any { launchable -> launchable.key == inputApp.key })
    }

    @Test
    fun `adds and removes a Folder from the App List and Rail live`() = databaseTest {
        val folderDao: FolderDao = database.getFolderDao()
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(),
            folderDao = folderDao
        )
        val actualBefore: LaunchableState = loadedState(repository)

        val folderId: Long = folderDao.insert(record = FolderRecord())
        val actualAdded: LaunchableState = loadedState(repository)

        folderDao.delete(id = folderId)
        val actualRemoved: LaunchableState = loadedState(repository)

        assertEquals(listOf("★", "⌂"), actualBefore.groups.map { group -> group.label })
        assertEquals(listOf("★", "F", "⌂"), actualAdded.groups.map { group -> group.label })
        assertEquals(listOf("★", "⌂"), actualRemoved.groups.map { group -> group.label })
    }

    @Test
    fun `groups an app named with the Builtin label apart from Builtin`() = databaseTest {
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(createApp(name = "⌂ Game", packageName = APP_PACKAGE_NAME)))
        )

        val actualGroups: List<LaunchableGroup.Listed> = loadedState(repository).listedGroups
        val actualKeys: List<String> = actualGroups.map { group -> group.appListKey() }

        assertEquals(listOf("⌂", "⌂"), actualGroups.map { group -> group.label })
        assertEquals(listOf("initial:⌂", "builtin:⌂"), actualKeys)
    }

    @Test
    fun `icon packs never replace Folder icons`() = databaseTest {
        val folderDao: FolderDao = database.getFolderDao()
        folderDao.insert(FolderRecord(id = 1L))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(),
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(iconPack = TEST_ICON_PACK_PACKAGE)),
            folderDao = folderDao
        )

        val actualFolder: Folder = loadedState(repository)
            .launchables.filterIsInstance<Folder>().single()

        assertEquals(Icon.FolderIcon(shade = 1), actualFolder.icon)
    }

    @Test
    fun `shows a Locked Private Space container last without private Apps`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputPrivateApp)),
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        )

        val actualState: LaunchableState = loadedState(repository)
        val actualGroup: LaunchableGroup.PrivateSpace = actualState.groups
            .filterIsInstance<LaunchableGroup.PrivateSpace>()
            .single()

        assertTrue(actualGroup.isLocked)
        assertTrue(actualGroup.items.isEmpty())
        assertEquals(listOf("★", "⌂", LaunchableGroup.PrivateSpace.LABEL), actualState.groups.map { group -> group.label })
    }

    @Test
    fun `hides a Locked Private Space while Rail hides it`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(),
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(hidePrivateSpaceWhenLocked = true)),
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        )

        val actualGroups: List<LaunchableGroup> = loadedState(repository).groups

        assertEquals(listOf("★", "⌂"), actualGroups.map { group -> group.label })
        assertTrue(actualGroups.none { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `hides a Locked Private Space the system hides and shows it again when the flag clears`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(),
            mockProfileSource = createProfileSourceMock(state = inputProfileState)
        )
        assertTrue(loadedState(repository).groups.any { group -> group is LaunchableGroup.PrivateSpace })

        inputProfileState.profiles.value = listOf(inputPrivateProfile.copy(isEntrypointHidden = true))
        assertTrue(loadedState(repository).groups.none { group -> group is LaunchableGroup.PrivateSpace })

        inputProfileState.profiles.value = listOf(inputPrivateProfile)
        assertTrue(loadedState(repository).groups.any { group -> group is LaunchableGroup.PrivateSpace })
    }

    @Test
    fun `shows an Unlocked Private Space while Rail and the system hide it`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputPrivateApp)),
            mockPreferencesRepository = createPreferencesRepositoryMock(settings = Settings(hidePrivateSpaceWhenLocked = true)),
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile.copy(isEntrypointHidden = true))
        )

        val actualGroup: LaunchableGroup.PrivateSpace = loadedState(repository).groups
            .filterIsInstance<LaunchableGroup.PrivateSpace>()
            .single()

        assertFalse(actualGroup.isLocked)
        assertEquals(listOf(inputPrivateApp.key), actualGroup.items.map { item -> item.key })
    }

    @Test
    fun `lists private Apps A-Z in the Unlocked Private Space`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputZulu: App = createApp(name = "Zulu", packageName = "com.example.zulu", profileInfo = inputPrivateProfile)
        val inputAlpha: App = createApp(name = "alpha", packageName = "com.example.alpha", profileInfo = inputPrivateProfile)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputZulu, inputAlpha)),
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        )

        val actualGroup: LaunchableGroup.PrivateSpace = loadedState(repository).groups
            .filterIsInstance<LaunchableGroup.PrivateSpace>()
            .single()

        assertFalse(actualGroup.isLocked)
        assertEquals(listOf(inputAlpha.key, inputZulu.key), actualGroup.items.map { item -> item.key })
    }

    @Test
    fun `lists private Apps in the picker groups while the Private Space is Unlocked`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputPrivateApp)),
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        )

        val actualPickerGroups: List<LaunchableGroup.Listed> = loadedState(repository).pickerGroups

        val actualPrivateGroup: LaunchableGroup.PrivateSpace = actualPickerGroups.filterIsInstance<LaunchableGroup.PrivateSpace>().single()
        assertFalse(actualPrivateGroup.isLocked)
        assertEquals(listOf(inputPrivateApp.key), actualPrivateGroup.items.map { item -> item.key })
        assertTrue(actualPickerGroups.filterIsInstance<LaunchableGroup.InitialGroup>().isEmpty())
    }

    @Test
    fun `keeps private Apps out of the picker groups while the Private Space is Locked`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputPrivateApp)),
            mockProfileSource = createProfileSourceMock(profile = inputPrivateProfile)
        )

        val actualState: LaunchableState = loadedState(repository)

        assertTrue(actualState.pickerGroups.none { group -> group is LaunchableGroup.PrivateSpace })
        assertTrue(actualState.pickerGroups.none { group -> group.items.any { item -> item.key == inputPrivateApp.key } })
        assertEquals(setOf(inputPrivateProfile.user.toProfileToken()), actualState.keptKeys)
    }

    @Test
    fun `keeps the keys of a Locked Profile until it is removed`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(
                inputAppState = MutableStateFlow(emptyList()),
                inputEnumeratedProfiles = MutableStateFlow(setOf(inputPrivateProfile.user))
            ),
            mockProfileSource = createProfileSourceMock(state = inputProfileState)
        )
        assertTrue(loadedState(repository).keptKeys.isEmpty())

        inputProfileState.profiles.value = listOf(inputPrivateProfile.copy(isQuiet = true))
        assertEquals(setOf(inputPrivateProfile.user.toProfileToken()), loadedState(repository).keptKeys)

        inputProfileState.profiles.value = emptyList()
        assertTrue(loadedState(repository).keptKeys.isEmpty())
    }

    @Test
    fun `keeps private Favorites, Folder members and Header tap actions while the Private Space Unlocks`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputFavorite: FavoriteRecord = FavoriteRecord(key = inputPrivateApp.key, position = FIRST_POSITION)
        seedFavorite(key = inputFavorite.key, position = inputFavorite.position)
        val inputFolder: Folder = Folder(folderId = FOLDER_ID)
        database.getFolderDao().insert(FolderRecord(id = FOLDER_ID))
        val inputMember: PopupItemRecord =
            PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputPrivateApp.key, position = FIRST_POSITION)
        database.getPopupDao().insertItems(listOf(inputMember))
        val inputTapTarget: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputPrivateApp.key)
        database.getHeaderSettingsDao().upsert(HeaderSettings(timeTapTarget = inputTapTarget))
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile.copy(isQuiet = true)))
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(emptyList())
        val inputEnumeratedState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
        val repositories: PrivateSpaceRepositories = createPrivateSpaceRepositories(
            profileState = inputProfileState,
            appState = inputAppState,
            enumeratedState = inputEnumeratedState
        )
        finishPendingWork()
        assertEquals(listOf(inputFavorite), favoriteRecords())
        assertEquals(emptyList<Launchable>(), repositories.favorites.favorites.first())
        assertEquals(emptyList<PopupItem>(), repositories.popups.items(inputFolder.key).first())
        assertEquals(inputTapTarget, repositories.headerSettings.headerSettings.first().timeTapTarget)

        inputProfileState.profiles.value = listOf(inputPrivateProfile)
        finishPendingWork()

        assertEquals(listOf(inputFavorite), favoriteRecords())
        assertEquals(emptyList<Launchable>(), repositories.favorites.favorites.first())
        assertEquals(emptyList<PopupItem>(), repositories.popups.items(inputFolder.key).first())
        assertEquals(inputTapTarget, repositories.headerSettings.headerSettings.first().timeTapTarget)

        inputAppState.value = listOf(inputPrivateApp)
        inputEnumeratedState.value = setOf(inputPrivateProfile.user)
        finishPendingWork()

        assertEquals(listOf(inputFavorite), favoriteRecords())
        assertEquals(listOf(inputPrivateApp), repositories.favorites.favorites.first())
        assertEquals(listOf(inputMember), database.getPopupDao().getItems(inputFolder.key))
        assertEquals(
            listOf(inputPrivateApp.key),
            repositories.popups.items(inputFolder.key).first().filterIsInstance<PopupItem.Launchable>().map { item -> item.launchable.key }
        )
        assertEquals(inputTapTarget, repositories.headerSettings.headerSettings.first().timeTapTarget)
        val actualState: LaunchableState = loadedState(repositories.launchables)
        assertTrue(actualState.keptKeys.isEmpty())
        assertEquals(setOf(inputPrivateProfile.user.toProfileToken()), actualState.enumeratedKeys)
    }

    @Test
    fun `removes a private Favorite once the Private Space lists its Apps without it`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputGoneApp: App = createApp(name = "Gone", packageName = "com.example.gone", profileInfo = inputPrivateProfile)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputGoneFavorite: FavoriteRecord = FavoriteRecord(key = inputGoneApp.key, position = FIRST_POSITION)
        val inputFavorite: FavoriteRecord = FavoriteRecord(key = inputPrivateApp.key, position = SECOND_POSITION)
        seedFavorite(key = inputGoneFavorite.key, position = inputGoneFavorite.position)
        seedFavorite(key = inputFavorite.key, position = inputFavorite.position)
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile.copy(isQuiet = true)))
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(emptyList())
        val inputEnumeratedState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
        val repositories: PrivateSpaceRepositories = createPrivateSpaceRepositories(
            profileState = inputProfileState,
            appState = inputAppState,
            enumeratedState = inputEnumeratedState
        )
        finishPendingWork()
        assertEquals(setOf(inputGoneFavorite, inputFavorite), favoriteRecords().toSet())

        inputProfileState.profiles.value = listOf(inputPrivateProfile)
        finishPendingWork()
        assertEquals(setOf(inputGoneFavorite, inputFavorite), favoriteRecords().toSet())

        inputAppState.value = listOf(inputPrivateApp)
        inputEnumeratedState.value = setOf(inputPrivateProfile.user)
        finishPendingWork()

        assertEquals(listOf(inputFavorite), favoriteRecords())
        assertEquals(listOf(inputPrivateApp), repositories.favorites.favorites.first())
    }

    @Test
    fun `removes a private Favorite when the Private Space Unlocks with no Apps`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputFavorite: FavoriteRecord = FavoriteRecord(key = inputPrivateApp.key, position = FIRST_POSITION)
        seedFavorite(key = inputFavorite.key, position = inputFavorite.position)
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile.copy(isQuiet = true)))
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(emptyList())
        val inputEnumeratedState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
        val repositories: PrivateSpaceRepositories = createPrivateSpaceRepositories(
            profileState = inputProfileState,
            appState = inputAppState,
            enumeratedState = inputEnumeratedState
        )
        finishPendingWork()
        assertEquals(listOf(inputFavorite), favoriteRecords())

        inputProfileState.profiles.value = listOf(inputPrivateProfile)
        inputEnumeratedState.value = setOf(inputPrivateProfile.user)
        finishPendingWork()

        assertTrue(favoriteRecords().isEmpty())
        assertTrue(loadedState(repositories.launchables).keptKeys.isEmpty())
    }

    @Test
    fun `keeps a private Favorite when the Lock is not observed`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputFavorite: FavoriteRecord = FavoriteRecord(key = inputPrivateApp.key, position = FIRST_POSITION)
        seedFavorite(key = inputFavorite.key, position = inputFavorite.position)
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(emptyList())
        val inputEnumeratedState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
        val repositories: PrivateSpaceRepositories = createPrivateSpaceRepositories(
            profileState = inputProfileState,
            appState = inputAppState,
            enumeratedState = inputEnumeratedState
        )
        finishPendingWork()
        assertEquals(listOf(inputFavorite), favoriteRecords())

        inputProfileState.profiles.value = listOf(inputPrivateProfile.copy(isQuiet = true))
        inputProfileState.profiles.value = listOf(inputPrivateProfile)
        finishPendingWork()

        assertEquals(listOf(inputFavorite), favoriteRecords())
        assertTrue(repositories.favorites.favorites.first().isEmpty())
    }

    @Test
    fun `hides and restores the private Apps as the Private Space locks`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputProfileState: ProfileSourceState = ProfileSourceState(profiles = listOf(inputPrivateProfile))
        val repository: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputApps = listOf(inputPrivateApp)),
            mockProfileSource = createProfileSourceMock(state = inputProfileState)
        )
        assertEquals(
            listOf(inputPrivateApp.key),
            loadedState(repository).groups.filterIsInstance<LaunchableGroup.PrivateSpace>().single().items.map { item -> item.key }
        )

        inputProfileState.profiles.value = listOf(inputPrivateProfile.copy(isQuiet = true))
        val actualLockedGroup: LaunchableGroup.PrivateSpace = loadedState(repository).groups
            .filterIsInstance<LaunchableGroup.PrivateSpace>()
            .single()
        assertTrue(actualLockedGroup.isLocked)
        assertTrue(actualLockedGroup.items.isEmpty())

        inputProfileState.profiles.value = listOf(inputPrivateProfile)
        val actualRestoredGroup: LaunchableGroup.PrivateSpace = loadedState(repository).groups
            .filterIsInstance<LaunchableGroup.PrivateSpace>()
            .single()
        assertFalse(actualRestoredGroup.isLocked)
        assertEquals(listOf(inputPrivateApp.key), actualRestoredGroup.items.map { item -> item.key })
    }

    @Test
    fun `restores personal data and cleans restored keys for missing Profiles`() = databaseTest {
        val inputPersonalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputWorkProfile: ProfileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
        val inputMissingWorkProfile: ProfileInfo = createProfileInfo(userId = MISSING_WORK_USER_ID, kind = ProfileKind.Work)
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputMissingPrivateProfile: ProfileInfo = createProfileInfo(
            userId = MISSING_PRIVATE_USER_ID,
            kind = ProfileKind.Private
        )
        val inputPersonalApp: App = createApp(
            name = "Personal",
            packageName = "com.example.personal",
            profileInfo = inputPersonalProfile
        )
        val inputWorkApp: App = createApp(name = "Work", packageName = "com.example.work", profileInfo = inputWorkProfile)
        val inputMissingWorkApp: App = createApp(
            name = "Old work",
            packageName = "com.example.old-work",
            profileInfo = inputMissingWorkProfile
        )
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputMissingPrivateApp: App = createApp(
            name = "Old secret",
            packageName = "com.example.old-private",
            profileInfo = inputMissingPrivateProfile
        )
        val inputRestoredApps: List<App> =
            listOf(inputPersonalApp, inputWorkApp, inputMissingWorkApp, inputPrivateApp, inputMissingPrivateApp)
        val inputFolder: Folder = seedRestoredRows(
            apps = inputRestoredApps,
            headerSettings = HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = inputPersonalApp.key),
                dateTapTarget = HeaderTapSetting.Launchable(key = inputMissingWorkApp.key)
            )
        )
        database.getPopupDao().insertHiddenShortcut(ownerKey = inputPrivateApp.key, shortcutId = FIRST_SHORTCUT_ID)
        database.getPopupDao().insertHiddenShortcut(ownerKey = inputMissingPrivateApp.key, shortcutId = SECOND_SHORTCUT_ID)
        val inputProfileState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(inputPersonalProfile, inputWorkProfile, inputPrivateProfile)
        )
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(listOf(inputPersonalApp, inputWorkApp))
        val inputEnumeratedState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
        val inputPreferencesRepository: IPreferencesRepository = createPreferencesRepository()
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock()
        val actualRepositories: PrivateSpaceRepositories = createPrivateSpaceRepositories(
            profileState = inputProfileState,
            appState = inputAppState,
            enumeratedState = inputEnumeratedState,
            preferencesRepository = inputPreferencesRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository
        )
        finishPendingWork()

        assertEquals(
            listOf(inputPersonalApp.key, inputWorkApp.key),
            actualRepositories.favorites.favorites.first().map { favorite -> favorite.key }
        )
        assertEquals(
            listOf(inputPersonalApp.key, inputWorkApp.key, inputPrivateApp.key),
            actualFavoriteKeys()
        )
        assertEquals(
            setOf(
                LaunchableCustomization(key = inputPersonalApp.key, name = "Personal restored"),
                LaunchableCustomization(key = inputWorkApp.key, name = "Work restored"),
                LaunchableCustomization(key = inputPrivateApp.key, name = "Secret restored")
            ),
            customizations().toSet()
        )
        val actualHeaderSettings: HeaderSettings = actualRepositories.headerSettings.headerSettings.first()
        assertEquals(HeaderTapSetting.Launchable(key = inputPersonalApp.key), actualHeaderSettings.timeTapTarget)
        assertNull(actualHeaderSettings.dateTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, database.getHeaderSettingsDao().getRow()?.dateTapTarget)
        assertEquals(
            listOf(inputPersonalApp.key, inputWorkApp.key, inputPrivateApp.key),
            actualPopupItemKeys(folder = inputFolder)
        )
        assertEquals(listOf(FIRST_SHORTCUT_ID), database.getPopupDao().observeHiddenShortcuts(inputPrivateApp.key).first())
        assertEquals(emptyList<String>(), database.getPopupDao().observeHiddenShortcuts(inputMissingPrivateApp.key).first())
        assertEquals(
            listOf(inputPersonalApp.key, inputWorkApp.key),
            actualVisiblePopupItemKeys(repositories = actualRepositories, folder = inputFolder)
        )
        coVerify(exactly = 0) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputPrivateApp.key) }
        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputMissingPrivateApp.key) }
    }

    @Test
    fun `keeps a restored Private Space key while Locked and cleans it when the Space is removed`() = databaseTest {
        val inputPersonalProfile: ProfileInfo = createProfileInfo(userId = PERSONAL_USER_ID)
        val inputPrivateProfile: ProfileInfo = createProfileInfo(
            userId = PRIVATE_USER_ID,
            kind = ProfileKind.Private,
            isQuiet = true
        )
        val inputPersonalApp: App = createApp(
            name = "Personal",
            packageName = "com.example.personal",
            profileInfo = inputPersonalProfile
        )
        val inputPrivateApp: App = createApp(name = "Secret", packageName = PRIVATE_PACKAGE_NAME, profileInfo = inputPrivateProfile)
        val inputFolder: Folder = seedRestoredRows(
            apps = listOf(inputPersonalApp, inputPrivateApp),
            headerSettings = HeaderSettings(timeTapTarget = HeaderTapSetting.Launchable(key = inputPrivateApp.key))
        )
        database.getPopupDao().insertHiddenShortcut(ownerKey = inputPrivateApp.key, shortcutId = FIRST_SHORTCUT_ID)
        val inputProfileState: ProfileSourceState = ProfileSourceState(
            profiles = listOf(inputPersonalProfile, inputPrivateProfile)
        )
        val inputAppState: MutableStateFlow<List<App>?> = MutableStateFlow(listOf(inputPersonalApp))
        val inputEnumeratedState: MutableStateFlow<Set<UserHandle>> = MutableStateFlow(emptySet())
        val inputPreferencesRepository: IPreferencesRepository = createPreferencesRepository()
        val actualRepositories: PrivateSpaceRepositories = createPrivateSpaceRepositories(
            profileState = inputProfileState,
            appState = inputAppState,
            enumeratedState = inputEnumeratedState,
            preferencesRepository = inputPreferencesRepository
        )
        finishPendingWork()

        assertEquals(
            listOf(inputPersonalApp.key),
            actualRepositories.favorites.favorites.first().map { favorite -> favorite.key }
        )
        assertEquals(listOf(inputPersonalApp.key, inputPrivateApp.key), actualFavoriteKeys())
        assertEquals(
            setOf(
                LaunchableCustomization(key = inputPersonalApp.key, name = "Personal restored"),
                LaunchableCustomization(key = inputPrivateApp.key, name = "Secret restored")
            ),
            customizations().toSet()
        )
        assertEquals(listOf(FIRST_SHORTCUT_ID), database.getPopupDao().observeHiddenShortcuts(inputPrivateApp.key).first())
        assertEquals(
            listOf(inputPersonalApp.key, inputPrivateApp.key),
            actualPopupItemKeys(folder = inputFolder)
        )
        assertEquals(
            listOf(inputPersonalApp.key),
            actualVisiblePopupItemKeys(repositories = actualRepositories, folder = inputFolder)
        )
        val actualLockedHeaderSettings: HeaderSettings = actualRepositories.headerSettings.headerSettings.first()
        assertEquals(HeaderTapSetting.Launchable(key = inputPrivateApp.key), actualLockedHeaderSettings.timeTapTarget)
        assertEquals(
            HeaderTapSetting.Launchable(key = inputPrivateApp.key),
            database.getHeaderSettingsDao().getRow()?.timeTapTarget
        )

        inputProfileState.profiles.value = listOf(inputPersonalProfile, inputPrivateProfile.copy(isQuiet = false))
        inputAppState.value = listOf(inputPersonalApp, inputPrivateApp)
        inputEnumeratedState.value = setOf(inputPrivateProfile.user)
        finishPendingWork()

        assertEquals(
            listOf(inputPersonalApp.key, inputPrivateApp.key),
            actualRepositories.favorites.favorites.first().map { favorite -> favorite.key }
        )
        assertEquals(
            listOf(inputPersonalApp.key, inputPrivateApp.key),
            actualVisiblePopupItemKeys(repositories = actualRepositories, folder = inputFolder)
        )
        val actualUnlockedHeaderSettings: HeaderSettings = actualRepositories.headerSettings.headerSettings.first()
        assertEquals(HeaderTapSetting.Launchable(key = inputPrivateApp.key), actualUnlockedHeaderSettings.timeTapTarget)

        inputProfileState.profiles.value = listOf(inputPersonalProfile)
        inputAppState.value = listOf(inputPersonalApp)
        inputEnumeratedState.value = emptySet()
        finishPendingWork()

        assertEquals(listOf(inputPersonalApp.key), actualFavoriteKeys())
        assertEquals(
            setOf(LaunchableCustomization(key = inputPersonalApp.key, name = "Personal restored")),
            customizations().toSet()
        )
        assertEquals(listOf(inputPersonalApp.key), actualPopupItemKeys(folder = inputFolder))
        assertEquals(emptyList<String>(), database.getPopupDao().observeHiddenShortcuts(inputPrivateApp.key).first())
        val actualRemovedHeaderSettings: HeaderSettings = actualRepositories.headerSettings.headerSettings.first()
        assertNull(actualRemovedHeaderSettings.timeTapTarget)
        assertEquals(HeaderTapSetting.SystemDefault, database.getHeaderSettingsDao().getRow()?.timeTapTarget)
    }

    private fun TestScope.createPreferencesRepository(
        launchableCustomizationDao: LaunchableCustomizationDao = database.getLaunchableCustomizationDao()
    ): IPreferencesRepository = IPreferencesRepositoryImpl(
        launchableCustomizationDao = launchableCustomizationDao,
        settingsDao = database.getSettingsDao(),
        scope = backgroundScope
    )

    /** Seeds the rows a restored database holds for [apps] and returns the Folder that owns their Pop-up. */
    private suspend fun seedRestoredRows(apps: List<App>, headerSettings: HeaderSettings): Folder {
        apps.forEachIndexed { position, app -> seedFavorite(key = app.key, position = position) }
        apps.forEach { app -> database.getLaunchableCustomizationDao().setName(key = app.key, name = "${app.name} restored") }
        val folder: Folder = Folder(folderId = FOLDER_ID)
        database.getFolderDao().insert(FolderRecord(id = FOLDER_ID))
        database.getPopupDao().insertItems(
            apps.mapIndexed { position, app ->
                PopupItemRecord(ownerKey = folder.key, itemKey = app.key, position = position)
            }
        )
        database.getHeaderSettingsDao().upsert(headerSettings)
        return folder
    }

    private suspend fun actualFavoriteKeys(): List<String> =
        favoriteRecords().sortedBy { record -> record.position }.map { record -> record.key }

    private suspend fun actualPopupItemKeys(folder: Folder): List<String> =
        database.getPopupDao().getItems(folder.key).sortedBy { item -> item.position }.map { item -> item.itemKey }

    private suspend fun actualVisiblePopupItemKeys(repositories: PrivateSpaceRepositories, folder: Folder): List<String> =
        repositories.popups.items(folder.key).first()
            .filterIsInstance<PopupItem.Launchable>()
            .map { item -> item.launchable.key }

    private suspend fun TestScope.loadedState(repository: ILaunchableRepository): LaunchableState {
        val collection: Job = backgroundScope.launch { repository.launchableState.collect { } }
        try {
            finishPendingWork()
            val actualState: LaunchableState = repository.launchableState.first()
            assertTrue(actualState.isLoaded)
            return actualState
        } finally {
            collection.cancel()
        }
    }

    private fun TestScope.createRepository(
        mockAppRepository: IAppRepository,
        mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
        mockIconRepository: IIconRepository = createIconRepositoryMock(inputIcon = Icon.ActivityIcon(inputComponentName)),
        mockProfileSource: IProfileSource = createProfileSourceMock(),
        folderDao: FolderDao = database.getFolderDao(),
        popupDao: PopupDao = database.getPopupDao(),
        scope: CoroutineScope = backgroundScope
    ): ILaunchableRepository = ILaunchableRepositoryImpl(
        appRepository = mockAppRepository,
        iconRepository = mockIconRepository,
        profileSource = mockProfileSource,
        preferencesRepository = mockPreferencesRepository,
        folderDao = folderDao,
        popupDao = popupDao,
        scope = scope
    )

    private fun TestScope.createPrivateSpaceRepositories(
        profileState: ProfileSourceState,
        appState: MutableStateFlow<List<App>?>,
        enumeratedState: MutableStateFlow<Set<UserHandle>>,
        preferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(),
        mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock()
    ): PrivateSpaceRepositories {
        val launchables: ILaunchableRepository = createRepository(
            mockAppRepository = createAppRepositoryMock(inputAppState = appState, inputEnumeratedProfiles = enumeratedState),
            mockPreferencesRepository = preferencesRepository,
            mockProfileSource = createProfileSourceMock(state = profileState)
        )
        return PrivateSpaceRepositories(
            launchables = launchables,
            favorites = createFavoritesRepository(favoriteDao = database.getFavoriteDao(), launchableRepository = launchables),
            popups = createPopupRepository(
                popupDao = database.getPopupDao(),
                launchableRepository = launchables,
                scope = backgroundScope,
                shortcutSource = createShortcutSourceMock(),
                widgetAreaRepository = mockWidgetAreaRepository,
                appWidgetHost = createAppWidgetHostMock()
            ),
            headerSettings = IWidgetSettingsRepositoryImpl(
                headerSettingsDao = database.getHeaderSettingsDao(),
                eventSettingsDao = database.getEventSettingsDao(),
                widgetAreaSettingsDao = database.getWidgetAreaSettingsDao(),
                mediaPlayerSettingsDao = database.getMediaPlayerSettingsDao(),
                launchableRepository = launchables
            )
        )
    }

    private fun LaunchableState.singleAppName(): String = launchables.filterIsInstance<App>().single().name
}

private data class PrivateSpaceRepositories(
    val launchables: ILaunchableRepository,
    val favorites: IFavoritesRepositoryImpl,
    val popups: IPopupRepository,
    val headerSettings: IWidgetSettingsRepository
)
