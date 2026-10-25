package core.data.popups

import android.content.ComponentName
import android.os.UserHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupDao
import core.data.testing.FakeShortcutSource
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.PinCall
import core.data.testing.TEST_SDK
import core.data.testing.appWidgetPlace
import core.data.testing.createRailLaunchable
import core.data.widgets.AppWidgetId
import core.data.widgets.IRemoveOwnerWidgetsImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FIRST_SHORTCUT_ID: String = "first"
private const val SECOND_SHORTCUT_ID: String = "second"
private const val TEST_USER_ID: Int = 0
private const val APP_PACKAGE_NAME: String = "com.example.app"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class IPopupRepositoryImplTest {
    private val profile: UserHandle = UserHandle.getUserHandleForUid(TEST_USER_ID)
    private val inputOwner: RailLaunchable = createRailLaunchable(name = "Owner")
    private val inputOtherOwner: RailLaunchable = createRailLaunchable(name = "Other")

    @Test
    fun `hides and shows a shortcut`() = runTest {
        val mockPopupDao: FakePopupDao = FakePopupDao()
        val repository: IPopupRepositoryImpl = createRepository(mockPopupDao = mockPopupDao)

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = true)
        assertEquals(setOf(FIRST_SHORTCUT_ID), repository.hiddenShortcuts(inputOwner.key).first())

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = false)
        assertEquals(emptySet<String>(), repository.hiddenShortcuts(inputOwner.key).first())
    }

    @Test
    fun `keeps hidden shortcuts separate per owner`() = runTest {
        val mockPopupDao: FakePopupDao = FakePopupDao()
        val repository: IPopupRepositoryImpl = createRepository(mockPopupDao = mockPopupDao)

        repository.setShortcutHidden(inputOwner.key, FIRST_SHORTCUT_ID, hidden = true)
        repository.setShortcutHidden(inputOtherOwner.key, SECOND_SHORTCUT_ID, hidden = true)

        assertEquals(setOf(FIRST_SHORTCUT_ID), repository.hiddenShortcuts(inputOwner.key).first())
        assertEquals(setOf(SECOND_SHORTCUT_ID), repository.hiddenShortcuts(inputOtherOwner.key).first())
    }

    @Test
    fun `removes stale owners only after the Launchables load`() = runTest {
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialRecords = listOf(
                PopupHiddenShortcut(ownerKey = inputOwner.key, shortcutId = FIRST_SHORTCUT_ID),
                PopupHiddenShortcut(ownerKey = "com.gone_gone", shortcutId = SECOND_SHORTCUT_ID)
            )
        )
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(mockPopupDao = mockPopupDao, mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(2, mockPopupDao.records.size)
        mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOwner))))
        runCurrent()
        assertEquals(listOf(PopupHiddenShortcut(ownerKey = inputOwner.key, shortcutId = FIRST_SHORTCUT_ID)), mockPopupDao.records)
    }

    @Test
    fun `deletes only the given owner`() = runTest {
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialRecords = listOf(
                PopupHiddenShortcut(ownerKey = inputOwner.key, shortcutId = FIRST_SHORTCUT_ID),
                PopupHiddenShortcut(ownerKey = inputOtherOwner.key, shortcutId = SECOND_SHORTCUT_ID)
            ),
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = "item", position = 0),
                PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = "other", position = 0)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(mockPopupDao = mockPopupDao)

        repository.deleteOwner(inputOwner.key)

        assertTrue(mockPopupDao.records.none { record -> record.ownerKey == inputOwner.key })
        assertEquals(listOf(PopupHiddenShortcut(ownerKey = inputOtherOwner.key, shortcutId = SECOND_SHORTCUT_ID)), mockPopupDao.records)
        assertTrue(mockPopupDao.items.none { item -> item.ownerKey == inputOwner.key })
        assertEquals(listOf(PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = "other", position = 0)), mockPopupDao.items)
    }

    @Test
    fun `emits the App Widget ids freed with a stale owner`() = runTest {
        val mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L, appWidgetId = 55, ownerKey = inputOwner.key))
        )
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = FakePopupDao(),
            mockLaunchableRepository = mockLaunchableRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )

        repository.removedWidgetIds.test {
            mockLaunchableRepository.setGroups(groups = emptyList())
            runCurrent()

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
            mockPopupDao = FakePopupDao(),
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
            mockPopupDao = FakePopupDao(),
            mockLaunchableRepository = mockLaunchableRepository,
            mockWidgetAreaRepository = mockWidgetAreaRepository,
            mockAppWidgetHost = mockAppWidgetHost
        )
        runCurrent()
        assertEquals(1, mockWidgetAreaRepository.placesFor(ownerKey = inputOwner.key).first().size)

        mockLaunchableRepository.setGroups(groups = emptyList())
        runCurrent()

        assertTrue(mockWidgetAreaRepository.placesFor(ownerKey = inputOwner.key).first().isEmpty())
        assertEquals(listOf(AppWidgetId(55)), mockAppWidgetHost.deletedIds)
    }

    @Test
    fun `appends added items in order`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val mockPopupDao: FakePopupDao = FakePopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second)
        )
        runCurrent()

        repository.setItems(ownerKey = inputOwner.key, add = listOf(first.key, second.key), remove = emptySet())
        runCurrent()

        assertEquals(listOf(first, second).asPopupItems(), repository.items(inputOwner.key).first())
        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            ),
            mockPopupDao.items.sortedBy { item -> item.position }
        )
    }

    @Test
    fun `adds and removes items in one change`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val third: RailLaunchable = createRailLaunchable(name = "Third")
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second, third)
        )
        runCurrent()

        repository.setItems(ownerKey = inputOwner.key, add = listOf(third.key), remove = setOf(first.key))
        runCurrent()

        assertEquals(listOf(second, third).asPopupItems(), repository.items(inputOwner.key).first())
    }

    @Test
    fun `counts the items shown in each Pop-up`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val third: RailLaunchable = createRailLaunchable(name = "Third")
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1),
                PopupItemRecord(ownerKey = inputOtherOwner.key, itemKey = third.key, position = 0)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, inputOtherOwner, first, second, third)
        )
        runCurrent()

        assertEquals(
            mapOf(inputOwner.key to 2, inputOtherOwner.key to 1),
            repository.itemCounts.first()
        )
    }

    @Test
    fun `rejects adding the owner to its own Pop-up`() = runTest {
        val item: RailLaunchable = createRailLaunchable(name = "Item")
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = FakePopupDao(),
            mockLaunchableRepository = loadedRepository(inputOwner, item)
        )
        runCurrent()

        repository.setItems(ownerKey = inputOwner.key, add = listOf(inputOwner.key, item.key), remove = emptySet())
        runCurrent()

        assertEquals(listOf(item).asPopupItems(), repository.items(inputOwner.key).first())
    }

    @Test
    fun `removes an item`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second)
        )
        runCurrent()

        repository.removeItem(ownerKey = inputOwner.key, itemKey = first.key)
        runCurrent()

        assertEquals(listOf(second).asPopupItems(), repository.items(inputOwner.key).first())
    }

    @Test
    fun `moves an item and rewrites positions`() = runTest {
        val first: RailLaunchable = createRailLaunchable(name = "First")
        val second: RailLaunchable = createRailLaunchable(name = "Second")
        val third: RailLaunchable = createRailLaunchable(name = "Third")
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 1),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = third.key, position = 2)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second, third)
        )
        runCurrent()

        repository.setItemOrder(
            ownerKey = inputOwner.key,
            orderedItemKeys = listOf(second.key, third.key, first.key)
        )
        runCurrent()

        assertEquals(listOf(second, third, first).asPopupItems(), repository.items(inputOwner.key).first())
        assertEquals(
            listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = third.key, position = 1),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 2)
            ),
            mockPopupDao.items.sortedBy { item -> item.position }
        )
    }

    @Test
    fun `reorders listed items without moving unlisted ones`() = runTest {
        val first: App = createApp(name = "First")
        val second: App = createApp(name = "Second", packageName = "com.example.second")
        val shortcutApp: App = createApp(name = "Shortcut app", packageName = "com.example.shortcuts")
        val hiddenShortcutKey: ShortcutKey = ShortcutKey(packageName = shortcutApp.packageName, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = first.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = hiddenShortcutKey.format(), position = 1),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = second.key, position = 2)
            )
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolveUnavailable()
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, first, second, shortcutApp),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()

        repository.setItemOrder(ownerKey = inputOwner.key, orderedItemKeys = listOf(second.key, first.key))
        runCurrent()

        assertEquals(
            listOf(second.key, hiddenShortcutKey.format(), first.key),
            mockPopupDao.items.sortedBy { item -> item.position }.map { item -> item.itemKey }
        )
    }

    @Test
    fun `removes stale items and owners only after the Launchables load`() = runTest {
        val known: RailLaunchable = createRailLaunchable(name = "Known")
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = known.key, position = 0),
                PopupItemRecord(ownerKey = inputOwner.key, itemKey = "com.gone_gone", position = 1),
                PopupItemRecord(ownerKey = "com.gone_gone", itemKey = "also-gone", position = 0)
            )
        )
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        createRepository(mockPopupDao = mockPopupDao, mockLaunchableRepository = mockLaunchableRepository)
        runCurrent()
        assertEquals(3, mockPopupDao.items.size)
        mockLaunchableRepository.setGroups(
            groups = listOf(LaunchableGroup.InitialGroup(label = "K", items = listOf(inputOwner, known)))
        )
        runCurrent()
        assertEquals(listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = known.key, position = 0)), mockPopupDao.items)
    }

    @Test
    fun `returns a stored shortcut item with its enabled state`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val shortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID, isEnabled = false)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolvedShortcuts(shortcuts = mapOf(shortcutKey to shortcut))
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()

        assertEquals(listOf(PopupItem.Shortcut(launchable = shortcut, isEnabled = false)), repository.items(owner.key).first())
    }

    @Test
    fun `removes a shortcut item when its package is gone`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner)
        )
        runCurrent()

        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first())
        assertEquals(emptyList<PopupItemRecord>(), mockPopupDao.items)
    }

    @Test
    fun `removes a shortcut item when its shortcut is gone`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolvedShortcuts(shortcuts = mapOf(shortcutKey to null))
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()

        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first())
        assertEquals(emptyList<PopupItemRecord>(), mockPopupDao.items)
    }

    @Test
    fun `removes a malformed shortcut key`() = runTest {
        val owner: App = createApp()
        val malformedKey: String = "shortcut:malformed"
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = malformedKey, position = 0))
        )
        createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner)
        )
        runCurrent()

        assertEquals(emptyList<PopupItemRecord>(), mockPopupDao.items)
    }

    @Test
    fun `pins an added shortcut`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val shortcut: ShortcutLaunchable = createShortcut(id = FIRST_SHORTCUT_ID)
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolvedShortcuts(shortcuts = mapOf(shortcutKey to shortcut))
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = FakePopupDao(),
            mockLaunchableRepository = loadedRepository(owner, createApp()),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()
        mockShortcutSource.pinCalls.clear()

        repository.setItems(ownerKey = owner.key, add = listOf(shortcutKey.format()), remove = emptySet())
        runCurrent()

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
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = owner.key, itemKey = firstKey.format(), position = 0),
                PopupItemRecord(ownerKey = owner.key, itemKey = secondKey.format(), position = 1)
            )
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolvedShortcuts(
            shortcuts = mapOf(
                firstKey to createShortcut(id = FIRST_SHORTCUT_ID),
                secondKey to createShortcut(id = SECOND_SHORTCUT_ID)
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner, createApp()),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()
        mockShortcutSource.pinCalls.clear()

        repository.removeItem(ownerKey = owner.key, itemKey = firstKey.format())
        runCurrent()

        assertEquals(
            listOf(PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(SECOND_SHORTCUT_ID))),
            mockShortcutSource.pinCalls
        )
    }

    @Test
    fun `keeps the union pinned across Pop-ups and unpins after the last owner is deleted`() = runTest {
        val firstOwner: App = createApp(name = "First")
        val secondOwner: App = createApp(name = "Second", packageName = "com.example.second")
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(
                PopupItemRecord(ownerKey = firstOwner.key, itemKey = shortcutKey.format(), position = 0),
                PopupItemRecord(ownerKey = secondOwner.key, itemKey = shortcutKey.format(), position = 0)
            )
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolvedShortcuts(shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID)))
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(firstOwner, secondOwner, createApp()),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()
        mockShortcutSource.pinCalls.clear()

        repository.removeItem(ownerKey = firstOwner.key, itemKey = shortcutKey.format())
        runCurrent()

        assertEquals(
            listOf(PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(FIRST_SHORTCUT_ID))),
            mockShortcutSource.pinCalls
        )

        repository.deleteOwner(ownerKey = secondOwner.key)
        runCurrent()

        assertEquals(
            listOf(
                PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(FIRST_SHORTCUT_ID)),
                PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = emptySet())
            ),
            mockShortcutSource.pinCalls
        )
    }

    @Test
    fun `does not clean up shortcut items while shortcuts are unavailable`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolveUnavailable()
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner, createApp()),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()

        assertEquals(emptyList<PopupItem>(), repository.items(owner.key).first())
        assertEquals(1, mockPopupDao.items.size)
    }

    @Test
    fun `keeps a shortcut item when its app is missing while shortcuts are unavailable`() = runTest {
        val owner: App = createApp()
        val hiddenPackageName: String = "com.example.hidden"
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = hiddenPackageName, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolveUnavailable()
        createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(owner),
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()

        assertEquals(1, mockPopupDao.items.size)
    }

    @Test
    fun `pins stored shortcuts on start after the Launchables load`() = runTest {
        val owner: App = createApp()
        val shortcutKey: ShortcutKey = ShortcutKey(packageName = APP_PACKAGE_NAME, id = FIRST_SHORTCUT_ID, profile = profile)
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = owner.key, itemKey = shortcutKey.format(), position = 0))
        )
        val mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val mockShortcutSource: FakeShortcutSource = FakeShortcutSource()
        mockShortcutSource.setResolvedShortcuts(
            shortcuts = mapOf(shortcutKey to createShortcut(id = FIRST_SHORTCUT_ID))
        )
        createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = mockLaunchableRepository,
            mockShortcutSource = mockShortcutSource
        )
        runCurrent()
        assertEquals(emptyList<PinCall>(), mockShortcutSource.pinCalls)

        mockLaunchableRepository.setGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "A", items = listOf(owner))))
        runCurrent()

        assertEquals(
            listOf(PinCall(packageName = APP_PACKAGE_NAME, profile = profile, ids = setOf(FIRST_SHORTCUT_ID))),
            mockShortcutSource.pinCalls
        )
    }

    @Test
    fun `adding the same item twice keeps one row`() = runTest {
        val item: RailLaunchable = createRailLaunchable(name = "Item")
        val mockPopupDao: FakePopupDao = FakePopupDao()
        val repository: IPopupRepositoryImpl = createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner, item)
        )
        runCurrent()

        repository.setItems(ownerKey = inputOwner.key, add = listOf(item.key), remove = emptySet())
        repository.setItems(ownerKey = inputOwner.key, add = listOf(item.key), remove = emptySet())
        runCurrent()

        assertEquals(listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = item.key, position = 0)), mockPopupDao.items)
    }
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

private fun TestScope.createRepository(
    mockPopupDao: FakePopupDao,
    mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
        groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = emptyList()))
    ),
    mockShortcutSource: FakeShortcutSource = FakeShortcutSource(),
    mockWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(),
    mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
): IPopupRepositoryImpl = IPopupRepositoryImpl(
    popupDao = mockPopupDao,
    launchableRepository = mockLaunchableRepository,
    shortcutSource = mockShortcutSource,
    scope = backgroundScope,
    widgetAreaRepository = mockWidgetAreaRepository,
    removeOwnerWidgets = IRemoveOwnerWidgetsImpl(repository = mockWidgetAreaRepository, host = mockAppWidgetHost)
)
