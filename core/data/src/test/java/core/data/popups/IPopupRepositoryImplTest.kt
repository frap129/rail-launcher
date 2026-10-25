package core.data.popups

import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupDao
import core.data.testing.createRailLaunchable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIRST_SHORTCUT_ID: String = "first"
private const val SECOND_SHORTCUT_ID: String = "second"

@OptIn(ExperimentalCoroutinesApi::class)
class IPopupRepositoryImplTest {
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

        assertEquals(listOf(first, second), repository.items(inputOwner.key).first())
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

        assertEquals(listOf(second, third), repository.items(inputOwner.key).first())
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

        assertEquals(listOf(item), repository.items(inputOwner.key).first())
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

        assertEquals(listOf(second), repository.items(inputOwner.key).first())
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

        repository.moveItem(ownerKey = inputOwner.key, from = 0, to = 2)
        runCurrent()

        assertEquals(listOf(second, third, first), repository.items(inputOwner.key).first())
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
    fun `keeps stored shortcut items during stale cleanup`() = runTest {
        val shortcutKey: String = "shortcut:com.example:first"
        val mockPopupDao: FakePopupDao = FakePopupDao(
            initialItems = listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = shortcutKey, position = 0))
        )
        createRepository(
            mockPopupDao = mockPopupDao,
            mockLaunchableRepository = loadedRepository(inputOwner)
        )

        runCurrent()

        assertEquals(listOf(PopupItemRecord(ownerKey = inputOwner.key, itemKey = shortcutKey, position = 0)), mockPopupDao.items)
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

private fun loadedRepository(vararg launchables: Launchable): FakeLaunchableRepository = FakeLaunchableRepository(
    groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = launchables.toList()))
)

private fun TestScope.createRepository(
    mockPopupDao: FakePopupDao,
    mockLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
        groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = emptyList()))
    )
): IPopupRepositoryImpl = IPopupRepositoryImpl(
    popupDao = mockPopupDao,
    launchableRepository = mockLaunchableRepository,
    scope = backgroundScope
)
