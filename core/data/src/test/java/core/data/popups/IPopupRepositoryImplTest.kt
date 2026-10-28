package core.data.popups

import android.content.ComponentName
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.RailLaunchable
import core.data.room.TransactionEffects
import core.data.room.dao.PopupDao
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutResolution
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PinCall
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.awaitPin
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPopupRepository
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcutSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    fun `hides and shows a shortcut`() = runTest {
        val repository: IPopupRepositoryImpl = createRepository(popupDao = database.getPopupDao())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = true)
        assertEquals(setOf(FIRST_SHORTCUT_ID), repository.hiddenShortcuts(inputOwner.key).first())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = false)
        assertEquals(emptySet<String>(), repository.hiddenShortcuts(inputOwner.key).first())
    }

    @Test
    fun `keeps hidden shortcuts separate per owner`() = runTest {
        val repository: IPopupRepositoryImpl = createRepository(popupDao = database.getPopupDao())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = true)
        repository.setShortcutHidden(inputOtherOwner.key, SECOND_SHORTCUT_ID, hidden = true)

        assertEquals(setOf(FIRST_SHORTCUT_ID), repository.hiddenShortcuts(inputOwner.key).first())
        assertEquals(setOf(SECOND_SHORTCUT_ID), repository.hiddenShortcuts(inputOtherOwner.key).first())
    }

    @Test
    fun `removes stale owners only after the Launchables load`() = runTest {
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertHiddenShortcut(ownerKey = inputOwner.key, shortcutId = FIRST_SHORTCUT_ID)
        popupDao.insertHiddenShortcut(ownerKey = GONE_OWNER_KEY, shortcutId = SECOND_SHORTCUT_ID)
        val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
        createRepository(popupDao = popupDao, mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(listOf(SECOND_SHORTCUT_ID), popupDao.observeHiddenShortcuts(GONE_OWNER_KEY).first())
        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner), label = "O")
        assertTrue(popupDao.observeHiddenShortcuts(GONE_OWNER_KEY).first { shortcutIds -> shortcutIds.isEmpty() }.isEmpty())
        assertEquals(listOf(FIRST_SHORTCUT_ID), popupDao.observeHiddenShortcuts(inputOwner.key).first())
    }

    @Test
    fun `deletes only the given owner`() = runTest {
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
    fun `emits the App Widget ids freed with a stale owner`() = runTest {
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
    fun `deleteOwner frees the owner's widget place and system id`() = runTest {
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
    fun `removes a stale App's widget place after the Launchables load`() = runTest {
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
        runCurrent()
        coVerify(exactly = 0) { mockWidgetAreaRepository.removeOwnerPlaces(any()) }

        repository.removedWidgetIds.test {
            launchableState.value = createLaunchableState(groups = emptyList())
            assertEquals(AppWidgetId(55), awaitItem())
        }

        coVerify(exactly = 1) { mockWidgetAreaRepository.removeOwnerPlaces(ownerKey = inputOwner.key) }
        verify(exactly = 1) { mockAppWidgetHost.deleteId(id = AppWidgetId(55)) }
    }

    @Test
    fun `appends added items in order`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, first, second), label = "O")
        )

        repository.setItems(ownerKey = inputOwner.key, add = listOf(first.key, second.key), remove = emptySet())

        assertEquals(listOf(first, second).asPopupItems(), repository.items(inputOwner.key).first { items -> items.isNotEmpty() })
        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            ),
            allItems()
        )
    }

    @Test
    fun `adds and removes items in one change`() = runTest {
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

        assertEquals(listOf(second, third).asPopupItems(), repository.items(inputOwner.key).first { items -> items.size == 2 })
    }

    @Test
    fun `counts the items shown in each Pop-up`() = runTest {
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

        assertEquals(
            mapOf(inputOwner.key to 2, inputOtherOwner.key to 1),
            repository.itemCounts.first { counts -> counts.isNotEmpty() }
        )
    }

    @Test
    fun `rejects adding the owner to its own Pop-up`() = runTest {
        val item: RailLaunchable = createRailLaunchable(name = "Item")
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(inputOwner, item), label = "O")
        )

        repository.setItems(ownerKey = inputOwner.key, add = listOf(inputOwner.key, item.key), remove = emptySet())

        assertEquals(listOf(item).asPopupItems(), repository.items(inputOwner.key).first { items -> items.isNotEmpty() })
    }

    @Test
    fun `removes an item`() = runTest {
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

        assertEquals(listOf(second).asPopupItems(), repository.items(inputOwner.key).first { items -> items.size == 1 })
    }

    @Test
    fun `moves an item and rewrites positions`() = runTest {
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

        assertEquals(listOf(second, third, first).asPopupItems(), repository.items(inputOwner.key).first { items -> items.size == 3 })
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
    fun `reorders listed items without moving unlisted ones`() = runTest {
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
    fun `removes stale items and owners only after the Launchables load`() = runTest {
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
        runCurrent()
        assertEquals(3, allItems().size)
        launchableState.value = createLoadedLaunchableState(launchables = listOf(inputOwner, known), label = "K")

        assertEquals(
            listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = known.key, position = 0)),
            allItems { items -> items.size == 1 }
        )
    }

    @Test
    fun `returns a stored shortcut item with its enabled state`() = runTest {
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

        assertEquals(
            listOf(PopupItem.Shortcut(launchable = shortcut, isEnabled = false)),
            repository.items(owner.key).first { items -> items.isNotEmpty() }
        )
    }

    @Test
    fun `removes a shortcut item when its app is uninstalled even if the shortcut still resolves`() = runTest {
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

        assertTrue(allItems { items -> items.isEmpty() }.isEmpty())
        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first { items -> items.isEmpty() })
    }

    @Test
    fun `removes a shortcut item when its shortcut is gone`() = runTest {
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

        assertTrue(allItems { items -> items.isEmpty() }.isEmpty())
        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first { items -> items.isEmpty() })
    }

    @Test
    fun `restores a kept shortcut item in its stored position`() = runTest {
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

        assertEquals(
            listOf(PopupItem.Launchable(launchable = otherLaunchable)),
            repository.items(owner.key).first { items -> items.isNotEmpty() }
        )

        shortcutResolution.value = ShortcutResolution(shortcuts = mapOf(shortcutKey to inputShortcut))

        assertEquals(
            listOf(
                PopupItem.Shortcut(launchable = inputShortcut, isEnabled = true),
                PopupItem.Launchable(launchable = otherLaunchable)
            ),
            repository.items(owner.key).first { items -> items.size == 2 }
        )
    }

    @Test
    fun `keeps a disabled shortcut item in its stored position`() = runTest {
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

        assertEquals(
            listOf(
                PopupItem.Shortcut(launchable = inputDisabledShortcut, isEnabled = false),
                PopupItem.Launchable(launchable = otherLaunchable)
            ),
            repository.items(owner.key).first { items -> items.size == 2 }
        )

        shortcutResolution.value = ShortcutResolution(shortcuts = mapOf(shortcutKey to inputEnabledShortcut))

        assertEquals(
            listOf(
                PopupItem.Shortcut(launchable = inputEnabledShortcut, isEnabled = true),
                PopupItem.Launchable(launchable = otherLaunchable)
            ),
            repository.items(owner.key).first { items -> items.firstOrNull() == PopupItem.Shortcut(inputEnabledShortcut, true) }
        )
    }

    @Test
    fun `removes a malformed shortcut key`() = runTest {
        val owner: App = createApp()
        val malformedKey: String = "shortcut:malformed"
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = malformedKey, position = 0)))
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = createLaunchableRepositoryMock(launchables = listOf(owner), label = "O")
        )

        assertTrue(allItems { items -> items.isEmpty() }.isEmpty())
    }

    @Test
    fun `pins an added shortcut`() = runTest {
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
        runCurrent()
        pinCalls.value = emptyList()

        repository.setItems(ownerKey = owner.key, add = listOf(shortcutKey.format()), remove = emptySet())

        assertEquals(
            listOf(PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(FIRST_SHORTCUT_ID))),
            pinCalls.value
        )
    }

    @Test
    fun `repins the remaining shortcuts when one is removed`() = runTest {
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
    fun `deleteItemsByKey defers pins until the transaction commits`() = runTest {
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
    fun `deleteItemsByKey keeps pins when the transaction fails`() = runTest {
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
    fun `keeps the union pinned across Pop-ups and unpins after the last owner is deleted`() = runTest {
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
    fun `does not clean up shortcut items while shortcuts are unavailable`() = runTest {
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
        runCurrent()

        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first())
        assertEquals(1, allItems().size)
    }

    @Test
    fun `removes a shortcut item when its app is uninstalled while shortcuts are unavailable`() = runTest {
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

        assertTrue(allItems { items -> items.isEmpty() }.isEmpty())
    }

    @Test
    fun `pins stored shortcuts on start after the Launchables load`() = runTest {
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
        runCurrent()
        assertEquals(emptyList<PinCall>(), pinCalls.value)

        launchableState.value = createLoadedLaunchableState(launchables = listOf(owner))

        pinCalls.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), pinCalls.value)
    }

    @Test
    fun `adding the same item twice keeps one row`() = runTest {
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
    fun `notifications are off by default`() = runTest {
        val repository: IPopupRepositoryImpl = createRepository(popupDao = database.getPopupDao())

        assertFalse(repository.notificationsEnabled.first())
    }

    @Test
    fun `stores whether Pop-ups show notifications`() = runTest {
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(popupDao = popupDao)

        repository.setNotificationsEnabled(true)

        assertTrue(repository.notificationsEnabled.first())
        assertTrue(requireNotNull(popupDao.observeSettings().first()).notificationsEnabled)
    }

    private suspend fun allItems(predicate: (List<PopupItemRecord>) -> Boolean = { true }): List<PopupItemRecord> =
        database.getPopupDao().observeAllItems().first(predicate).sortedBy { item -> item.position }

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

private fun createApp(name: String = "App", packageName: String = APP_PACKAGE_NAME): App = App(
    defaultName = name,
    profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
    componentName = ComponentName(packageName, "$packageName.MainActivity")
)

private fun createShortcut(id: String, isEnabled: Boolean = true): ShortcutLaunchable = ShortcutLaunchable(
    packageName = APP_PACKAGE_NAME,
    shortcutId = id,
    profile = UserHandle.getUserHandleForUid(TEST_USER_ID),
    shortLabel = id,
    longLabel = id,
    isEnabled = isEnabled
)
