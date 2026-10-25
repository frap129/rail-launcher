package core.data.popups

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
            )
        )
        val repository: IPopupRepositoryImpl = createRepository(mockPopupDao = mockPopupDao)

        repository.deleteOwner(inputOwner.key)

        assertTrue(mockPopupDao.records.none { record -> record.ownerKey == inputOwner.key })
        assertEquals(listOf(PopupHiddenShortcut(ownerKey = inputOtherOwner.key, shortcutId = SECOND_SHORTCUT_ID)), mockPopupDao.records)
    }
}

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
