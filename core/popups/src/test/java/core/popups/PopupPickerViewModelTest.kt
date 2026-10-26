package core.popups

import app.cash.turbine.test
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupPickerViewModelTest : PopupTestBase() {

    @Test
    fun `shortcut picker groups Apps with their shortcuts and checks existing items`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp)),
                LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(apps = listOf(inputAppShortcuts, inputOtherAppShortcuts))
        fakePopupRepository.setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(PopupItem.Shortcut(launchable = inputOtherShortcut, isEnabled = true))
        )
        val viewModel: PopupPickerViewModel = createPicker()
        viewModel.shortcutPickerState.test {
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
            viewModel.startPickingShortcuts(inputApp)
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertEquals(setOf(inputOtherShortcut.key), picking.selectedKeys)
            assertEquals(listOf("O"), picking.groups.map { group -> group.label })
            assertEquals(listOf(inputOtherAppShortcuts), picking.groups.flatMap { group -> group.apps })
        }
    }

    @Test
    fun `picker excludes the owner and shortcuts and checks existing items`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp)),
                LaunchableGroup.InitialGroup(label = "S", items = listOf(inputShortcut)),
                LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
            )
        )
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupPickerViewModel = createPicker()
        viewModel.pickerState.test {
            assertEquals(PopupPickerState.Closed, expectMostRecentItem())
            viewModel.startPicking(inputApp)
            val picking: PopupPickerState.Picking = expectMostRecentItem() as PopupPickerState.Picking
            assertEquals(setOf(inputOtherApp.key), picking.selectedKeys)
            assertEquals(listOf("O", "⌂"), picking.groups.map { group -> group.label })
            val candidates: List<Launchable> = picking.groups.flatMap { group -> group.items }
            assertEquals(listOf(inputOtherApp, inputRailLaunchable), candidates)
            assertFalse(candidates.any { candidate -> candidate == inputApp || candidate is ShortcutLaunchable })
        }
    }

    @Test
    fun `confirming the shortcut picker adds checked shortcuts and removes unchecked ones`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(apps = listOf(inputAppShortcuts, inputOtherAppShortcuts))
        fakePopupRepository.setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(PopupItem.Shortcut(launchable = inputShortcut, isEnabled = true))
        )
        val viewModel: PopupPickerViewModel = createPicker()
        viewModel.shortcutPickerState.test {
            viewModel.startPickingShortcuts(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            viewModel.confirmShortcuts(selectedInOrder = listOf(inputOtherShortcut.key))
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        assertEquals(
            listOf(Triple(inputApp.key, listOf(inputOtherShortcut.key), setOf(inputShortcut.key))),
            fakePopupRepository.setItemsCalls
        )
    }

    @Test
    fun `cancelling the shortcut picker changes nothing`() = runTest {
        fakeShortcutSource.setAppsWithShortcuts(apps = listOf(inputOtherAppShortcuts))
        val viewModel: PopupPickerViewModel = createPicker()
        viewModel.shortcutPickerState.test {
            viewModel.startPickingShortcuts(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            viewModel.cancelShortcutPicking()
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        assertTrue(fakePopupRepository.setItemsCalls.isEmpty())
    }

    @Test
    fun `shortcut picker lists paused Apps and leaves out Apps with no shortcuts`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp)),
                LaunchableGroup.InitialGroup(label = "P", items = listOf(inputPausedApp))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(
            apps = listOf(
                AppShortcuts(app = inputOtherApp, result = ShortcutsResult.Available(shortcuts = emptyList())),
                AppShortcuts(app = inputPausedApp, result = ShortcutsResult.ProfilePaused)
            )
        )
        val viewModel: PopupPickerViewModel = createPicker()
        viewModel.shortcutPickerState.test {
            viewModel.startPickingShortcuts(inputApp)
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertEquals(listOf("P"), picking.groups.map { group -> group.label })
            assertEquals(listOf(inputPausedApp), picking.groups.flatMap { group -> group.apps }.map { appShortcuts -> appShortcuts.app })
        }
    }

    @Test
    fun `shortcut picker reports when shortcuts are unavailable`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(
            apps = listOf(AppShortcuts(app = inputOtherApp, result = ShortcutsResult.Unavailable))
        )
        val viewModel: PopupPickerViewModel = createPicker()
        viewModel.shortcutPickerState.test {
            viewModel.startPickingShortcuts(inputApp)
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertTrue(picking.shortcutsUnavailable)
        }
    }

    @Test
    fun `an empty Folder card opens the picker directly`() = runTest {
        val session: PopupTestSession = createSession()
        session.picker.pickerState.test {
            session.card.open(inputFolder, TEST_INPUT_ANCHOR)
            runCurrent()
            session.picker.startFolderPicking()
            runCurrent()
            val picking: PopupPickerState.Picking = expectMostRecentItem() as PopupPickerState.Picking
            assertEquals(emptySet<String>(), picking.selectedKeys)
            val candidates: List<Launchable> = picking.groups.flatMap { group -> group.items }
            assertEquals(listOf(inputApp, inputRailLaunchable), candidates)
            assertFalse(candidates.any { candidate -> candidate is Folder || candidate is ShortcutLaunchable })
        }
    }

    @Test
    fun `confirming the Folder picker adds items without opening the editor`() = runTest {
        val session: PopupTestSession = createSession()
        session.picker.pickerState.test {
            session.card.open(inputFolder, TEST_INPUT_ANCHOR)
            runCurrent()
            session.picker.startFolderPicking()
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupPickerState.Picking)
            session.picker.confirmPicking(selectedInOrder = listOf(inputOtherApp.key))
            runCurrent()
            assertEquals(PopupPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        assertEquals(listOf(Triple(inputFolder.key, listOf(inputOtherApp.key), emptySet<String>())), fakePopupRepository.setItemsCalls)
        assertEquals(PopupEditState.Closed, session.editor.editState.value)
    }

    @Test
    fun `Back closes the shortcut picker before the editor`() = runTest {
        val session: PopupTestSession = createSession()
        backgroundScope.launch { session.editor.editState.collect() }
        backgroundScope.launch { session.picker.shortcutPickerState.collect() }
        session.editor.startEdit(inputApp)
        runCurrent()
        session.picker.startPickingShortcuts(inputApp)
        runCurrent()
        assertTrue(session.picker.shortcutPickerState.value is ShortcutPickerState.Picking)
        assertTrue(session.editor.editState.value is PopupEditState.Editing)

        assertTrue(session.card.onBack())

        assertEquals(ShortcutPickerState.Closed, session.picker.shortcutPickerState.value)
        assertTrue(session.editor.editState.value is PopupEditState.Editing)
    }
}
