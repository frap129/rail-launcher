package core.data.popups

import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.profiles.ProfileInfo
import core.data.profiles.ProfileKind
import core.data.profiles.ProfileToken
import core.data.profiles.toProfileToken
import core.data.room.TransactionEffects
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutResolution
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PRIVATE_PACKAGE_NAME
import core.data.testing.PRIVATE_USER_ID
import core.data.testing.PinCall
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.TimberLogRecorder
import core.data.testing.awaitPin
import core.data.testing.createApp
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPopupRepository
import core.data.testing.createPrivateApp
import core.data.testing.createProfileInfo
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcutSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.packageLevelKey
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TEST_USER_ID: Int = 0
private const val APP_PACKAGE_NAME: String = "com.example.app"
private const val GONE_OWNER_KEY: String = "com.gone_gone"
private val inputOwner: RailLaunchable = createRailLaunchable(name = "Owner")
private val inputOtherOwner: RailLaunchable = createRailLaunchable(name = "Other")

@OptIn(ExperimentalCoroutinesApi::class)
class IPopupRepositoryImplTest : InMemoryDatabaseTest() {
    private val profile: UserHandle = UserHandle.getUserHandleForUid(TEST_USER_ID)

    @Test
    fun `hides and shows a shortcut`() = databaseTest {
        val repository: IPopupRepositoryImpl = createRepository(popupDao = database.getPopupDao())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = true)
        assertEquals(setOf(FIRST_SHORTCUT_ID), repository.hiddenShortcuts(inputOwner.key).first())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = false)
        assertEquals(emptySet<String>(), repository.hiddenShortcuts(inputOwner.key).first())
    }

    @Test
    fun `removes a hidden shortcut stored under a package-level key while its App stays`() = databaseTest {
        val inputApp: App = createApp(packageName = APP_PACKAGE_NAME)
        val inputPackageKey: String = inputApp.packageLevelKey()
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertHiddenShortcut(ownerKey = inputPackageKey, shortcutId = FIRST_SHORTCUT_ID)
        popupDao.insertHiddenShortcut(ownerKey = inputApp.key, shortcutId = SECOND_SHORTCUT_ID)
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputApp))
        )
        finishPendingWork()

        assertEquals(emptyList<String>(), popupDao.observeHiddenShortcuts(inputPackageKey).first())
        assertEquals(listOf(SECOND_SHORTCUT_ID), popupDao.observeHiddenShortcuts(inputApp.key).first())
    }

    @Test
    fun `removes Pop-up rows stored under a package-level key while its App stays`() = databaseTest {
        val inputApp: App = createApp(packageName = APP_PACKAGE_NAME)
        val inputOther: Launchable = createRailLaunchable(name = "Other")
        val inputPackageKey: String = inputApp.packageLevelKey()
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputPackageKey, itemKey = inputApp.key, position = 0),
                PopupItemRecord(ownerKey = inputApp.key, itemKey = inputPackageKey, position = 0),
                PopupItemRecord(ownerKey = inputApp.key, itemKey = inputOther.key, position = 1)
            )
        )
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputApp, inputOther))
        )
        finishPendingWork()

        assertEquals(listOf(PopupItemRecord(ownerKey = inputApp.key, itemKey = inputOther.key, position = 1)), allItems())
    }

    @Test
    fun `keeps hidden shortcuts separate per owner`() = databaseTest {
        val repository: IPopupRepositoryImpl = createRepository(popupDao = database.getPopupDao())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = true)
        repository.setShortcutHidden(inputOtherOwner.key, SECOND_SHORTCUT_ID, hidden = true)

        assertEquals(setOf(FIRST_SHORTCUT_ID), repository.hiddenShortcuts(inputOwner.key).first())
        assertEquals(setOf(SECOND_SHORTCUT_ID), repository.hiddenShortcuts(inputOtherOwner.key).first())
    }

    @Test
    fun `removes stale owners only after the Launchables load`() = databaseTest {
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertHiddenShortcut(ownerKey = inputOwner.key, shortcutId = FIRST_SHORTCUT_ID)
        popupDao.insertHiddenShortcut(ownerKey = GONE_OWNER_KEY, shortcutId = SECOND_SHORTCUT_ID)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(popupDao = popupDao, mockLaunchableRepository = mockLaunchableRepository)
        finishPendingWork()
        assertEquals(listOf(SECOND_SHORTCUT_ID), popupDao.observeHiddenShortcuts(GONE_OWNER_KEY).first())
        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner), label = "O")
        finishPendingWork()
        assertEquals(emptyList<String>(), popupDao.observeHiddenShortcuts(GONE_OWNER_KEY).first())
        assertEquals(listOf(FIRST_SHORTCUT_ID), popupDao.observeHiddenShortcuts(inputOwner.key).first())
    }

    @Test
    fun `logs a stale owner cleanup failure without the owner key`() = databaseTest {
        val inputPrivateApp: App = createPrivateApp()
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertHiddenShortcut(ownerKey = inputPrivateApp.key, shortcutId = FIRST_SHORTCUT_ID)
        val mockPopupDao: PopupDao = spyk(popupDao)
        coEvery {
            mockPopupDao.deleteHiddenForOwner(inputPrivateApp.key)
        } throws RuntimeException("Could not remove Pop-up preferences")
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        createRepository(
            popupDao = mockPopupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchableState)
        )
        finishPendingWork()
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOtherOwner), label = "O")
            finishPendingWork()
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.key, inputPrivateApp.packageName)
    }

    @Test
    fun `keeps the items and hidden shortcuts of a Locked Profile until it is removed`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(
            name = "Secret",
            packageName = PRIVATE_PACKAGE_NAME,
            profileInfo = inputPrivateProfile
        )
        val inputOtherApp: App = createApp()
        val inputFolder: Folder = Folder(folderId = 1L)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputPrivateApp.key, position = 0),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputOtherApp.key, position = 1)
            )
        )
        popupDao.insertHiddenShortcut(ownerKey = inputPrivateApp.key, shortcutId = FIRST_SHORTCUT_ID)
        val inputKeptKeys: Set<ProfileToken> = setOf(inputPrivateProfile.user.toProfileToken())
        fun state(items: List<Launchable>, keptKeys: Set<ProfileToken> = emptySet()): LaunchableState = createLaunchableState(
            groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = items)),
            folders = listOf(inputFolder),
            keptKeys = keptKeys
        )
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            state(items = listOf(inputOtherApp), keptKeys = inputKeptKeys)
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchableState)
        )

        assertItems(repository = repository, ownerKey = inputFolder.key, expected = listOf(inputOtherApp).asPopupItems())
        assertEquals(listOf(FIRST_SHORTCUT_ID), popupDao.observeHiddenShortcuts(inputPrivateApp.key).first())
        assertEquals(2, allItems().size)

        launchableState.value = state(items = listOf(inputPrivateApp, inputOtherApp))

        assertItems(
            repository = repository,
            ownerKey = inputFolder.key,
            expected = listOf(inputPrivateApp, inputOtherApp).asPopupItems()
        )

        launchableState.value = state(items = listOf(inputOtherApp), keptKeys = inputKeptKeys)

        assertItems(repository = repository, ownerKey = inputFolder.key, expected = listOf(inputOtherApp).asPopupItems())

        launchableState.value = state(items = listOf(inputOtherApp))

        assertItems(repository = repository, ownerKey = inputFolder.key, expected = listOf(inputOtherApp).asPopupItems())
        assertEquals(emptyList<String>(), popupDao.observeHiddenShortcuts(inputPrivateApp.key).first())
        assertEquals(listOf(PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputOtherApp.key, position = 1)), allItems())
    }

    @Test
    fun `leaves a Locked Profile's shortcut pins alone until it is removed`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputOwner: App = createApp()
        val inputPrivateShortcut: ShortcutKey = ShortcutKey(
            packageName = PRIVATE_PACKAGE_NAME,
            id = FIRST_SHORTCUT_ID,
            profile = inputPrivateProfile.user
        )
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = inputPrivateShortcut.format(), position = 0)))
        val inputKeptKeys: Set<ProfileToken> = setOf(inputPrivateProfile.user.toProfileToken())
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(launchables = listOf(inputOwner), keptKeys = inputKeptKeys)
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchableState),
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        finishPendingWork()

        assertEquals(emptyList<PinCall>(), pinCalls.value)
        assertEquals(1, allItems().size)

        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner))

        finishPendingWork()
        assertEquals(
            listOf(PinCall(packageName = PRIVATE_PACKAGE_NAME, profile = inputPrivateProfile.user, ids = emptySet())),
            pinCalls.value
        )
        assertTrue(allItems().isEmpty())
    }

    @Test
    fun `reorders Folder members around kept ones while Locked`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputPrivateApp: App = createApp(
            name = "Secret",
            packageName = PRIVATE_PACKAGE_NAME,
            profileInfo = inputPrivateProfile
        )
        val inputFolder: Folder = Folder(folderId = 1L)
        val inputFirst: App = createApp(name = "First", packageName = "com.example.first")
        val inputSecond: App = createApp(name = "Second", packageName = "com.example.second")
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputPrivateApp.key, position = 0),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputFirst.key, position = 1),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputSecond.key, position = 2)
            )
        )
        val inputKeptKeys: Set<ProfileToken> = setOf(inputPrivateProfile.user.toProfileToken())
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(
                groups = listOf(LaunchableGroup.InitialGroup(label = "F", items = listOf(inputFirst, inputSecond))),
                folders = listOf(inputFolder),
                keptKeys = inputKeptKeys
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchableState)
        )

        repository.setItemOrder(ownerKey = inputFolder.key, orderedItemKeys = listOf(inputSecond.key, inputFirst.key))

        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputPrivateApp.key, position = 0),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputSecond.key, position = 1),
                PopupItemRecord(ownerKey = inputFolder.key, itemKey = inputFirst.key, position = 2)
            ),
            allItems()
        )

        launchableState.value = createLaunchableState(
            groups = listOf(
                LaunchableGroup.InitialGroup(label = "F", items = listOf(inputFirst, inputSecond)),
                LaunchableGroup.PrivateSpace.Unlocked(items = listOf(inputPrivateApp))
            ),
            folders = listOf(inputFolder)
        )

        assertItems(
            repository = repository,
            ownerKey = inputFolder.key,
            expected = listOf(inputPrivateApp, inputSecond, inputFirst).asPopupItems()
        )
    }

    @Test
    fun `reapplies a Locked Profile's pins when it becomes Unlocked`() = databaseTest {
        val inputPrivateProfile: ProfileInfo = createProfileInfo(userId = PRIVATE_USER_ID, kind = ProfileKind.Private)
        val inputOwner: App = createApp()
        val inputPrivateApp: App = createApp(
            name = "Secret",
            packageName = PRIVATE_PACKAGE_NAME,
            profileInfo = inputPrivateProfile
        )
        val inputPrivateShortcut: ShortcutKey = ShortcutKey(
            packageName = PRIVATE_PACKAGE_NAME,
            id = FIRST_SHORTCUT_ID,
            profile = inputPrivateProfile.user
        )
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = inputPrivateShortcut.format(), position = 0)))
        val inputKeptKeys: Set<ProfileToken> = setOf(inputPrivateProfile.user.toProfileToken())
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLoadedLaunchableState(launchables = listOf(inputOwner), keptKeys = inputKeptKeys)
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchableState),
            mockShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        )
        finishPendingWork()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        launchableState.value = createLoadedLaunchableState(
            launchables = listOf(inputOwner, inputPrivateApp),
            enumeratedKeys = inputKeptKeys
        )

        pinCalls.awaitPin(
            PinCall(packageName = PRIVATE_PACKAGE_NAME, profile = inputPrivateProfile.user, ids = setOf(FIRST_SHORTCUT_ID))
        )
    }

    @Test
    fun `deletes only the given owner`() = databaseTest {
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertHiddenShortcut(ownerKey = inputOwner.key, shortcutId = FIRST_SHORTCUT_ID)
        popupDao.insertHiddenShortcut(ownerKey = inputOtherOwner.key, shortcutId = SECOND_SHORTCUT_ID)
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = inputOwner.key, position = 0),
                PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = inputOtherOwner.key, position = 0)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(popupDao = popupDao)

        repository.deleteOwner(inputOwner.key)

        assertTrue(popupDao.observeHiddenShortcuts(inputOwner.key).first().isEmpty())
        assertEquals(listOf(SECOND_SHORTCUT_ID), popupDao.observeHiddenShortcuts(inputOtherOwner.key).first())
        assertEquals(listOf(PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = inputOtherOwner.key, position = 0)), allItems())
    }

    @Test
    fun `emits the App Widget ids freed with a stale owner`() = databaseTest {
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            popupOwnerKeysFlow = MutableStateFlow(listOf(inputOwner.key)),
            removeOwnerPlacesAnswer = { listOf(AppWidgetId(55)) }
        )
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = mockLaunchableRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )

        repository.removedWidgetIds.test {
            launchableState.value = createLaunchableState(groups = emptyList())

            assertEquals(AppWidgetId(55), awaitItem())
        }
        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputOwner.key) }
        verify(exactly = 1) { mockAppWidgetHost.deleteId(id = AppWidgetId(55)) }
    }

    @Test
    fun `deleteOwner frees the owner's widget place and system id`() = databaseTest {
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            removeOwnerPlacesAnswer = { listOf(AppWidgetId(55)) }
        )
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )

        val actualFreedIds: List<AppWidgetId> = repository.deleteOwner(inputOwner.key)

        assertEquals(listOf(AppWidgetId(55)), actualFreedIds)
        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputOwner.key) }
        verify(exactly = 1) { mockAppWidgetHost.deleteId(id = AppWidgetId(55)) }
    }

    @Test
    fun `removes a Pop-up Place stored under a package-level key after the Launchables load`() = databaseTest {
        val inputApp: App = createApp(packageName = APP_PACKAGE_NAME)
        val inputPackageKey: String = inputApp.packageLevelKey()
        val mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            popupOwnerKeysFlow = MutableStateFlow(listOf(inputPackageKey)),
            removeOwnerPlacesAnswer = { listOf(AppWidgetId(55)) }
        )
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = mockLaunchableRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )
        finishPendingWork()
        coVerify(exactly = 0) { mockWidgetAreaRepository.removeOwnerPlaces(any()) }

        repository.removedWidgetIds.test {
            launchableState.value = createLoadedLaunchableState(launchables = listOf(inputApp))
            assertEquals(AppWidgetId(55), awaitItem())
        }

        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputPackageKey) }
        coVerify(exactly = 0) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputApp.key) }
        verify(exactly = 1) { mockAppWidgetHost.deleteId(id = AppWidgetId(55)) }
    }

    @Test
    fun `appends added items in order`() = databaseTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, first, second), label = "O")
        )

        repository.setItems(ownerKey = inputOwner.key, add = listOf(first.key, second.key), remove = emptySet())

        assertItems(repository = repository, ownerKey = inputOwner.key, expected = listOf(first, second).asPopupItems())
        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            ),
            allItems()
        )
    }

    @Test
    fun `adds and removes items in one change`() = databaseTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val third: RailLaunchable = createRailLaunchable(name = "Third")
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, first, second, third), label = "O")
        )

        repository.setItems(ownerKey = inputOwner.key, add = listOf(third.key), remove = setOf(first.key))

        assertItems(repository = repository, ownerKey = inputOwner.key, expected = listOf(second, third).asPopupItems())
    }

    @Test
    fun `counts the items shown in each Pop-up`() = databaseTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val third: RailLaunchable = createRailLaunchable(name = "Third")
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1),
                PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = third.key, position = 0)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(inputOwner, inputOtherOwner, first, second, third),
                label = "O"
            )
        )

        finishPendingWork()
        assertEquals(
            mapOf(inputOwner.key to 2, inputOtherOwner.key to 1),
            repository.itemCounts.first()
        )
    }

    @Test
    fun `rejects adding the owner to its own Pop-up`() = databaseTest {
        val item: RailLaunchable = createRailLaunchable(name = "Item")
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, item), label = "O")
        )

        repository.setItems(ownerKey = inputOwner.key, add = listOf(inputOwner.key, item.key), remove = emptySet())

        assertItems(repository = repository, ownerKey = inputOwner.key, expected = listOf(item).asPopupItems())
    }

    @Test
    fun `removes an item`() = databaseTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, first, second), label = "O")
        )

        repository.removeItem(ownerKey = inputOwner.key, itemKey = first.key)

        assertItems(repository = repository, ownerKey = inputOwner.key, expected = listOf(second).asPopupItems())
    }

    @Test
    fun `moves an item and rewrites positions`() = databaseTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val third: RailLaunchable = createRailLaunchable(name = "Third")
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = third.key, position = 2)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, first, second, third), label = "O")
        )

        repository.setItemOrder(
            ownerKey = inputOwner.key,
            orderedItemKeys = listOf(second.key, third.key, first.key)
        )
        finishPendingWork()

        assertEquals(listOf(second, third, first).asPopupItems(), repository.items(inputOwner.key).first())
        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = third.key, position = 1),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 2)
            ),
            allItems()
        )
    }

    @Test
    fun `reorders listed items without moving unlisted ones`() = databaseTest {
        val first: App = createApp(name = "First")
        val second: App = createApp(name = "Second", packageName = "com.example.second")
        val shortcutApp: App = createApp(name = "Shortcut app", packageName = "com.example.shortcuts")
        val hiddenShortcutKey: ShortcutKey = ShortcutKey(packageName = shortcutApp.packageName, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = hiddenShortcutKey.format(), position = 1),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 2)
            )
        )
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(null)
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(inputOwner, first, second, shortcutApp),
                label = "O"
            ),
            mockShortcutSource = mockShortcutSource
        )

        repository.setItemOrder(ownerKey = inputOwner.key, orderedItemKeys = listOf(second.key, first.key))

        assertEquals(
            listOf(second.key, hiddenShortcutKey.format(), first.key),
            allItems().map { item -> item.itemKey }
        )
    }

    @Test
    fun `removes stale items and owners only after the Launchables load`() = databaseTest {
        val known: RailLaunchable = createRailLaunchable(name = "Known")
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = known.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = GONE_OWNER_KEY, position = 1),
                PopupItemRecord(ownerKey = GONE_OWNER_KEY, itemKey = "also-gone", position = 0)
            )
        )
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(popupDao = popupDao, mockLaunchableRepository = mockLaunchableRepository)
        finishPendingWork()
        assertEquals(3, allItems().size)
        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner, known), label = "K")
        finishPendingWork()

        assertEquals(
            listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = known.key, position = 0)),
            allItems()
        )
    }

    @Test
    fun `returns a stored shortcut item with its enabled state`() = databaseTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val shortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID, isEnabled = false)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(shortcuts = mapOf(shortcutKey to shortcut))
        )
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner), label = "O"),
            mockShortcutSource = mockShortcutSource
        )

        assertItems(
            repository = repository,
            ownerKey = owner.key,
            expected = listOf(PopupItem.Shortcut(launchable = shortcut, isEnabled = false))
        )
    }

    @Test
    fun `removes a shortcut item when its app is uninstalled even if the shortcut still resolves`() = databaseTest {
        val owner: App = createApp()
        val removedPackageName: String = "com.example.removed"
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = removedPackageName, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID)))
        )
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner), label = "O"),
            mockShortcutSource = mockShortcutSource
        )

        assertItemsRemoved(repository = repository, ownerKey = owner.key)
    }

    @Test
    fun `removes a shortcut item when its shortcut is gone`() = databaseTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(goneKeys = setOf(shortcutKey))
        )
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner), label = "O"),
            mockShortcutSource = mockShortcutSource
        )

        assertItemsRemoved(repository = repository, ownerKey = owner.key)
    }

    @Test
    fun `restores a kept shortcut item in its stored position`() = databaseTest {
        val owner: App = createApp()
        val otherLaunchable: RailLaunchable = createRailLaunchable(name = "Other")
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val inputShortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0),
                PopupItemRecord(ownerKey = owner.key, itemKey = otherLaunchable.key, position = 1)
            )
        )
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(ShortcutResolution())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner, otherLaunchable), label = "O"),
            mockShortcutSource = mockShortcutSource
        )

        assertItems(
            repository = repository,
            ownerKey = owner.key,
            expected = listOf(PopupItem.Launchable(launchable = otherLaunchable))
        )

        shortcutResolution.value = ShortcutResolution(shortcuts = mapOf(shortcutKey to inputShortcut))

        assertItems(
            repository = repository,
            ownerKey = owner.key,
            expected = listOf(
                PopupItem.Shortcut(launchable = inputShortcut, isEnabled = true),
                PopupItem.Launchable(launchable = otherLaunchable)
            )
        )
    }

    @Test
    fun `keeps a disabled shortcut item in its stored position`() = databaseTest {
        val owner: App = createApp()
        val otherLaunchable: RailLaunchable = createRailLaunchable(name = "Other")
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val inputDisabledShortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID, isEnabled = false)
        val inputEnabledShortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID, isEnabled = true)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0),
                PopupItemRecord(ownerKey = owner.key, itemKey = otherLaunchable.key, position = 1)
            )
        )
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(shortcuts = mapOf(shortcutKey to inputDisabledShortcut))
        )
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner, otherLaunchable), label = "O"),
            mockShortcutSource = mockShortcutSource
        )

        assertItems(
            repository = repository,
            ownerKey = owner.key,
            expected = listOf(
                PopupItem.Shortcut(launchable = inputDisabledShortcut, isEnabled = false),
                PopupItem.Launchable(launchable = otherLaunchable)
            )
        )

        shortcutResolution.value = ShortcutResolution(shortcuts = mapOf(shortcutKey to inputEnabledShortcut))

        assertItems(
            repository = repository,
            ownerKey = owner.key,
            expected = listOf(
                PopupItem.Shortcut(launchable = inputEnabledShortcut, isEnabled = true),
                PopupItem.Launchable(launchable = otherLaunchable)
            )
        )
    }

    @Test
    fun `removes a malformed shortcut key`() = databaseTest {
        val owner: App = createApp()
        val malformedKey: String = "shortcut:malformed"
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = malformedKey, position = 0)))
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner), label = "O")
        )

        assertNoItems()
    }

    @Test
    fun `pins an added shortcut`() = databaseTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val shortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID)
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(shortcuts = mapOf(shortcutKey to shortcut))
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(
            resolution = shortcutResolution,
            pinCalls = pinCalls
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner, createApp()), label = "O"),
            mockShortcutSource = mockShortcutSource
        )
        finishPendingWork()
        pinCalls.value = emptyList()

        repository.setItems(ownerKey = owner.key, add = listOf(shortcutKey.format()), remove = emptySet())

        assertEquals(
            listOf(PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(FIRST_SHORTCUT_ID))),
            pinCalls.value
        )
    }

    @Test
    fun `repins the remaining shortcuts when one is removed`() = databaseTest {
        val owner: App = createApp()
        val firstKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val secondKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = SECOND_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = owner.key, itemKey = firstKey.format(), position = 0),
                PopupItemRecord(ownerKey = owner.key, itemKey = secondKey.format(), position = 1)
            )
        )
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(
                shortcuts = mapOf(
                    firstKey to createShortcut(id = FIRST_SHORTCUT_ID),
                    secondKey to createShortcut(id = SECOND_SHORTCUT_ID)
                )
            )
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(
            resolution = shortcutResolution,
            pinCalls = pinCalls
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner, createApp()), label = "O"),
            mockShortcutSource = mockShortcutSource
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        pinCalls.value = emptyList()

        repository.removeItem(ownerKey = owner.key, itemKey = firstKey.format())

        pinCalls.awaitPin(pin(ids = setOf(SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(SECOND_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `deleteItemsByKey defers pins until the transaction commits`() = databaseTest {
        val inputOwner: App = createApp()
        val inputOtherOwner: App = createApp(name = "Other", packageName = "com.example.other")
        val inputRemovedKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val inputRemainingKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = SECOND_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = inputRemovedKey.format(), position = 0),
                PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = inputRemainingKey.format(), position = 0)
            )
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(inputOwner, inputOtherOwner, createApp()),
                label = "O"
            ),
            mockShortcutSource = mockShortcutSource
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        pinCalls.value = emptyList()
        val effects: TransactionEffects = TransactionEffects()

        database.getFolderDao().inTransaction {
            withContext(effects) {
                repository.deleteItemsByKey(listOf(inputRemovedKey.format()))
            }
        }

        assertEquals(emptyList<PinCall>(), pinCalls.value)
        effects.run()
        pinCalls.awaitPin(pin(ids = setOf(SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(SECOND_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `deleteItemsByKey keeps pins when the transaction fails`() = databaseTest {
        val inputOwner: App = createApp()
        val inputOtherOwner: App = createApp(name = "Other", packageName = "com.example.other")
        val inputRemovedKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val inputRemainingKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = SECOND_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = inputRemovedKey.format(), position = 0),
                PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = inputRemainingKey.format(), position = 0)
            )
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(pinCalls = pinCalls)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(inputOwner, inputOtherOwner, createApp()),
                label = "O"
            ),
            mockShortcutSource = mockShortcutSource
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        pinCalls.value = emptyList()
        val effects: TransactionEffects = TransactionEffects()

        val actualFailure: Throwable? = runCatching {
            database.getFolderDao().inTransaction {
                withContext(effects) {
                    repository.deleteItemsByKey(listOf(inputRemovedKey.format()))
                    throw IllegalStateException("Folder delete failed")
                }
            }
        }.exceptionOrNull()

        assertTrue(actualFailure is IllegalStateException)
        assertEquals(emptyList<PinCall>(), pinCalls.value)
    }

    @Test
    fun `keeps the union pinned across Pop-ups and unpins after the last owner is deleted`() = databaseTest {
        val firstOwner: App = createApp(name = "First")
        val secondOwner: App = createApp(name = "Second", packageName = "com.example.second")
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(
            listOf(
                PopupItemRecord(ownerKey = firstOwner.key, itemKey = shortcutKey.format(), position = 0),
                PopupItemRecord(ownerKey = secondOwner.key, itemKey = shortcutKey.format(), position = 0)
            )
        )
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID)))
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(
            resolution = shortcutResolution,
            pinCalls = pinCalls
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                launchables = listOf(firstOwner, secondOwner, createApp()),
                label = "O"
            ),
            mockShortcutSource = mockShortcutSource
        )
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        pinCalls.value = emptyList()

        repository.removeItem(ownerKey = firstOwner.key, itemKey = shortcutKey.format())
        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))

        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)

        repository.deleteOwner(ownerKey = secondOwner.key)
        pinCalls.awaitPin(pin(ids = emptySet()))

        assertEquals(
            listOf(pin(ids = setOf(FIRST_SHORTCUT_ID)), pin(ids = emptySet())),
            pinCalls.value
        )
    }

    @Test
    fun `does not clean up shortcut items while shortcuts are unavailable`() = databaseTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(null)
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner, createApp()), label = "O"),
            mockShortcutSource = mockShortcutSource
        )
        finishPendingWork()

        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first())
        assertEquals(1, allItems().size)
    }

    @Test
    fun `removes a shortcut item when its app is uninstalled while shortcuts are unavailable`() = databaseTest {
        val owner: App = createApp()
        val hiddenPackageName: String = "com.example.hidden"
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = hiddenPackageName, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(null)
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(resolution = shortcutResolution)
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner), label = "O"),
            mockShortcutSource = mockShortcutSource
        )

        assertNoItems()
    }

    @Test
    fun `pins stored shortcuts on start after the Launchables load`() = databaseTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        val shortcutResolution: MutableStateFlow<ShortcutResolution?> = MutableStateFlow(
            ShortcutResolution(shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID)))
        )
        val pinCalls: MutableStateFlow<List<PinCall>> = MutableStateFlow(emptyList())
        val mockShortcutSource: IShortcutSource = createShortcutSourceMock(
            resolution = shortcutResolution,
            pinCalls = pinCalls
        )
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = mockLaunchableRepository,
            mockShortcutSource = mockShortcutSource
        )
        finishPendingWork()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        launchableState.value = createLoadedLaunchableState(launchables = listOf(owner))

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `adding the same item twice keeps one row`() = databaseTest {
        val item: RailLaunchable = createRailLaunchable(name = "Item")
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, item), label = "O")
        )

        repository.setItems(ownerKey = inputOwner.key, add = listOf(item.key), remove = emptySet())
        repository.setItems(ownerKey = inputOwner.key, add = listOf(item.key), remove = emptySet())

        assertEquals(listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = item.key, position = 0)), allItems())
    }

    @Test
    fun `notifications are off by default`() = databaseTest {
        val repository: IPopupRepositoryImpl = createRepository(popupDao = database.getPopupDao())

        assertFalse(repository.notificationsEnabled.first())
    }

    @Test
    fun `stores whether Pop-ups show notifications`() = databaseTest {
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(popupDao = popupDao)

        repository.setNotificationsEnabled(true)

        assertTrue(repository.notificationsEnabled.first())
        assertTrue(requireNotNull(popupDao.observeSettings().first()).notificationsEnabled)
    }

    private suspend fun allItems(): List<PopupItemRecord> =
        database.getPopupDao().observeAllItems().first().sortedBy { item -> item.position }

    private suspend fun TestScope.assertNoItems() {
        finishPendingWork()
        assertTrue(allItems().isEmpty())
    }

    private suspend fun TestScope.assertItemsRemoved(repository: IPopupRepositoryImpl, ownerKey: String) {
        assertNoItems()
        assertEquals(emptyList<PopupItem>(), repository.items(ownerKey).first())
    }

    private suspend fun TestScope.assertItems(repository: IPopupRepositoryImpl, ownerKey: String, expected: List<PopupItem>) {
        finishPendingWork()
        assertEquals(expected, repository.items(ownerKey).first())
    }

    private fun pin(ids: Set<String>): PinCall = PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = ids)

    private fun TestScope.createRepository(
        popupDao: PopupDao,
        mockLaunchableRepository: ILaunchableRepository =
            createLaunchableRepositoryMock(launchables = listOf(inputOwner, inputOtherOwner), label = "O"),
        mockShortcutSource: IShortcutSource = createShortcutSourceMock(),
        mockWidgetAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(),
        mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock()
    ): IPopupRepositoryImpl = createPopupRepository(
        popupDao = popupDao,
        launchableRepository = mockLaunchableRepository,
        scope = backgroundScope,
        shortcutSource = mockShortcutSource,
        widgetAreaRepository = mockWidgetAreaRepository,
        appWidgetHost = mockAppWidgetHost
    )
}

private fun List<Launchable>.asPopupItems(): List<PopupItem> = map { launchable -> PopupItem.Launchable(launchable) }

private fun createShortcut(id: String, isEnabled: Boolean = true): ShortcutLaunchable = ShortcutLaunchable(
    packageName = APP_PACKAGE_NAME,
    shortcutId = id,
    profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
    shortLabel = id,
    longLabel = id,
    isEnabled = isEnabled
)
