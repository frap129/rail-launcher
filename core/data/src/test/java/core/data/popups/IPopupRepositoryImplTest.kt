package core.data.popups

import android.content.ComponentName
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.room.TransactionEffects
import core.data.room.dao.PopupDao
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakeShortcutSource
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.PinCall
import core.data.testing.SECOND_SHORTCUT_ID
import core.data.testing.appWidgetPlace
import core.data.testing.createPopupRepository
import core.data.testing.createRailLaunchable
import core.data.widgets.AppWidgetId
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(popupDao = popupDao, mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(listOf(SECOND_SHORTCUT_ID), popupDao.observeHiddenShortcuts(GONE_OWNER_KEY).first())
        mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOwner))))
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
        val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L, appWidgetId = 55, ownerKey = inputOwner.key))
        )
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = mockLaunchableRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )

        repository.removedWidgetIds.test {
            mockLaunchableRepository.setGroups(groups = emptyList())
            mockWidgetAreaRepository.placesFor(ownerKey = inputOwner.key).first { places -> places.isEmpty() }

            assertEquals(AppWidgetId(55), awaitItem())
        }
    }

    @Test
    fun `deleteOwner frees the owner's widget place and system id`() = runTest {
        val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            initialPlaces = listOf(appWidgetPlace(placeId = 1L, widgetId = 1L, appWidgetId = 55, ownerKey = inputOwner.key))
        )
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )

        val actualFreedIds: List<AppWidgetId> = repository.deleteOwner(inputOwner.key)

        assertEquals(listOf(AppWidgetId(55)), actualFreedIds)
        assertTrue(mockWidgetAreaRepository.placesFor(ownerKey = inputOwner.key).first().isEmpty())
        assertEquals(listOf(AppWidgetId(55)), mockAppWidgetHost.deletedIds)
    }

    @Test
    fun `removes a stale App's widget place after the Launchables load`() = runTest {
        val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            initialPlaces = listOf(appWidgetPlace(placeId = 1L, widgetId = 1L, appWidgetId = 55, ownerKey = inputOwner.key))
        )
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = mockLaunchableRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )
        runCurrent()
        assertEquals(1, mockWidgetAreaRepository.placesFor(ownerKey = inputOwner.key).first().size)

        mockLaunchableRepository.setGroups(groups = emptyList())

        assertTrue(mockWidgetAreaRepository.placesFor(ownerKey = inputOwner.key).first { places -> places.isEmpty() }.isEmpty())
        assertEquals(listOf(AppWidgetId(55)), mockAppWidgetHost.deletedIds)
    }

    @Test
    fun `appends added items in order`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second)
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
            mockLaunchableRepository = loadedRepository(inputOwner, first, second, third)
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
            mockLaunchableRepository = loadedRepository(inputOwner, inputOtherOwner, first, second, third)
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
            mockLaunchableRepository = loadedRepository(inputOwner, item)
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
            mockLaunchableRepository = loadedRepository(inputOwner, first, second)
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
            mockLaunchableRepository = loadedRepository(inputOwner, first, second, third)
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolveUnavailable()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second, shortcutApp),
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
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(popupDao = popupDao, mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(3, allItems().size)
        mockLaunchableRepository.setGroups(
            groups = listOf(LaunchableGroup.InitialGroup(label = "K", items = listOf(inputOwner, known)))
        )

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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(shortcuts = mapOf(shortcutKey to shortcut))
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner),
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(
            shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID))
        )
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner),
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(goneKeys = setOf(shortcutKey))
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner),
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner, otherLaunchable),
            mockShortcutSource = mockShortcutSource
        )

        assertEquals(
            listOf(PopupItem.Launchable(launchable = otherLaunchable)),
            repository.items(owner.key).first { items -> items.isNotEmpty() }
        )

        mockShortcutSource.setResolution(shortcuts = mapOf(shortcutKey to inputShortcut))

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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(shortcuts = mapOf(shortcutKey to inputDisabledShortcut))
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner, otherLaunchable),
            mockShortcutSource = mockShortcutSource
        )

        assertEquals(
            listOf(
                PopupItem.Shortcut(launchable = inputDisabledShortcut, isEnabled = false),
                PopupItem.Launchable(launchable = otherLaunchable)
            ),
            repository.items(owner.key).first { items -> items.size == 2 }
        )

        mockShortcutSource.setResolution(shortcuts = mapOf(shortcutKey to inputEnabledShortcut))

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
            mockLaunchableRepository = loadedRepository(owner)
        )

        assertTrue(allItems { items -> items.isEmpty() }.isEmpty())
    }

    @Test
    fun `pins an added shortcut`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val shortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID)
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(shortcuts = mapOf(shortcutKey to shortcut))
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = database.getPopupDao(),
            mockLaunchableRepository = loadedRepository(owner, createApp()),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()
        mockShortcutSource.pinCalls.clear()

        repository.setItems(ownerKey = owner.key, add = listOf(shortcutKey.format()), remove = emptySet())

        assertEquals(
            listOf(PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(FIRST_SHORTCUT_ID))),
            mockShortcutSource.pinCalls
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(
            shortcuts = mapOf(
                firstKey to createShortcut(id = FIRST_SHORTCUT_ID),
                secondKey to createShortcut(id = SECOND_SHORTCUT_ID)
            )
        )
        val pins: Channel<PinCall> = Channel(Channel.UNLIMITED)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner, createApp()),
            mockShortcutSource = recordPins(mockShortcutSource = mockShortcutSource, pins = pins)
        )
        pins.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        mockShortcutSource.pinCalls.clear()

        repository.removeItem(ownerKey = owner.key, itemKey = firstKey.format())

        pins.awaitPin(pin(ids = setOf(SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(SECOND_SHORTCUT_ID))), mockShortcutSource.pinCalls)
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        val pins: Channel<PinCall> = Channel(Channel.UNLIMITED)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, inputOtherOwner, createApp()),
            mockShortcutSource = recordPins(mockShortcutSource = mockShortcutSource, pins = pins)
        )
        pins.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        mockShortcutSource.pinCalls.clear()
        val effects: TransactionEffects = TransactionEffects()

        database.getFolderDao().inTransaction {
            withContext(effects) {
                repository.deleteItemsByKey(listOf(inputRemovedKey.format()))
            }
        }

        assertEquals(emptyList<PinCall>(), mockShortcutSource.pinCalls)
        effects.run()
        pins.awaitPin(pin(ids = setOf(SECOND_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(SECOND_SHORTCUT_ID))), mockShortcutSource.pinCalls)
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        val pins: Channel<PinCall> = Channel(Channel.UNLIMITED)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, inputOtherOwner, createApp()),
            mockShortcutSource = recordPins(mockShortcutSource = mockShortcutSource, pins = pins)
        )
        pins.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID, SECOND_SHORTCUT_ID)))
        mockShortcutSource.pinCalls.clear()
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
        assertEquals(emptyList<PinCall>(), mockShortcutSource.pinCalls)
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID)))
        val pins: Channel<PinCall> = Channel(Channel.UNLIMITED)
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(firstOwner, secondOwner, createApp()),
            mockShortcutSource = recordPins(mockShortcutSource = mockShortcutSource, pins = pins)
        )
        pins.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        mockShortcutSource.pinCalls.clear()

        repository.removeItem(ownerKey = firstOwner.key, itemKey = shortcutKey.format())
        pins.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))

        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), mockShortcutSource.pinCalls)

        repository.deleteOwner(ownerKey = secondOwner.key)
        pins.awaitPin(pin(ids = emptySet()))

        assertEquals(
            listOf(pin(ids = setOf(FIRST_SHORTCUT_ID)), pin(ids = emptySet())),
            mockShortcutSource.pinCalls
        )
    }

    @Test
    fun `does not clean up shortcut items while shortcuts are unavailable`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val popupDao: PopupDao = database.getPopupDao()
        popupDao.insertItems(listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0)))
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolveUnavailable()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner, createApp()),
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
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolveUnavailable()
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(owner),
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
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolution(
            shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID))
        )
        val pins: Channel<PinCall> = Channel(Channel.UNLIMITED)
        createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = mockLaunchableRepository,
            mockShortcutSource = recordPins(mockShortcutSource = mockShortcutSource, pins = pins)
        )
        runCurrent()
        assertEquals(emptyList<PinCall>(), mockShortcutSource.pinCalls)

        mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(owner))))

        pins.awaitPin(pin(ids = setOf(FIRST_SHORTCUT_ID)))
        assertEquals(listOf(pin(ids = setOf(FIRST_SHORTCUT_ID))), mockShortcutSource.pinCalls)
    }

    @Test
    fun `adding the same item twice keeps one row`() = runTest {
        val item: RailLaunchable = createRailLaunchable(name = "Item")
        val popupDao: PopupDao = database.getPopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            popupDao = popupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, item)
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

    /** A spy on [mockShortcutSource] that keeps recording its pins and also sends each to [pins]. */
    private fun recordPins(mockShortcutSource: FakeShortcutSource, pins: Channel<PinCall>): FakeShortcutSource {
        val mockRecordingSource: FakeShortcutSource = spyk(mockShortcutSource)
        coEvery { mockRecordingSource.pin(any(), any(), any()) } coAnswers {
            val actualPin: PinCall = PinCall(packageName = firstArg(), profile = arg(1), ids = arg(2))
            mockShortcutSource.pin(packageName = actualPin.packageName, profile = actualPin.profile, ids = actualPin.ids)
            pins.send(actualPin)
        }
        return mockRecordingSource
    }

    /** Waits for the specific [expectedPin], skipping any pin that arrives earlier. */
    private suspend fun Channel<PinCall>.awaitPin(expectedPin: PinCall) {
        var actualPin: PinCall = receive()
        while (actualPin != expectedPin) {
            actualPin = receive()
        }
    }

    private fun TestScope.createRepository(
        popupDao: PopupDao,
        mockLaunchableRepository: FakeLaunchableRepository = loadedRepository(inputOwner, inputOtherOwner),
        mockShortcutSource: FakeShortcutSource = FakeShortcutSource(),
        mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(),
        mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
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

private fun loadedRepository(vararg launchables: Launchable): FakeLaunchableRepository = FakeLaunchableRepository(
    groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = launchables.toList()))
)

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
