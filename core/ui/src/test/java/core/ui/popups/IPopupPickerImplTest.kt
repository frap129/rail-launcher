package core.ui.popups

import app.cash.turbine.test
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class IPopupPickerImplTest : PopupTestBase() {

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
        val picker: IPopupPicker = createPicker()
        picker.shortcutPickerState.test {
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
            picker.startPickingShortcuts(inputApp)
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
        val picker: IPopupPicker = createPicker()
        picker.pickerState.test {
            assertEquals(PopupPickerState.Closed, expectMostRecentItem())
            picker.startPicking(inputApp)
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
        val picker: IPopupPicker = createPicker()
        picker.shortcutPickerState.test {
            picker.startPickingShortcuts(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            picker.confirmShortcuts(selectedInOrder = listOf(inputOtherShortcut.key))
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
        val picker: IPopupPicker = createPicker()
        picker.shortcutPickerState.test {
            picker.startPickingShortcuts(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            picker.cancelShortcutPicking()
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
        val picker: IPopupPicker = createPicker()
        picker.shortcutPickerState.test {
            picker.startPickingShortcuts(inputApp)
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
        val picker: IPopupPicker = createPicker()
        picker.shortcutPickerState.test {
            picker.startPickingShortcuts(inputApp)
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertTrue(picking.shortcutsUnavailable)
        }
    }
}
