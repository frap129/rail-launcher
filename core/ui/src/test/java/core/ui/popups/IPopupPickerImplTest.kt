package core.ui.popups

import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createPrivateApp
import io.mockk.coEvery
import io.mockk.coVerify
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
class IPopupPickerImplTest : PopupTestBase() {
    private val inputPickerGroups: List<LaunchableGroup> = listOf(
        LaunchableGroup.Star,
        LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
        LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp))
    )
    private val inputBuiltinGroup: LaunchableGroup.Builtin = LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
    private val inputAppShortcutsList: List<AppShortcuts> = listOf(inputAppShortcuts, inputOtherAppShortcuts)

    @Test
    fun `shortcut picker groups Apps with their shortcuts and checks existing items`() = runTest {
        setLaunchableGroups(groups = inputPickerGroups + inputBuiltinGroup)
        setAppsWithShortcuts(apps = inputAppShortcutsList)
        setPopupItems(
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
            assertEquals(listOf("O"), picking.groups.map { group -> group.launchableGroup.label })
            assertEquals(listOf(inputOtherAppShortcuts), picking.groups.flatMap { group -> group.apps })
        }
    }

    @Test
    fun `picker excludes the owner and shortcuts and checks existing items`() = runTest {
        setLaunchableGroups(
            groups = inputPickerGroups +
                LaunchableGroup.InitialGroup(label = "S", items = listOf(inputShortcut)) +
                inputBuiltinGroup
        )
        setPopupLaunchables(ownerKey = inputApp.key, launchables = listOf(inputOtherApp))
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
        setLaunchableGroups(groups = inputPickerGroups)
        setAppsWithShortcuts(apps = inputAppShortcutsList)
        setPopupItems(
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
        coVerify(exactly = 1) { mockPopupRepository.setItems(any(), any(), any()) }
        coVerify(exactly = 1) {
            mockPopupRepository.setItems(
                inputApp.key,
                listOf(inputOtherShortcut.key),
                setOf(inputShortcut.key)
            )
        }
    }

    @Test
    fun `logs a private Pop-up picker failure without its package or key`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        setLaunchableGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp))))
        setPopupLaunchables(ownerKey = inputPrivateApp.key, launchables = emptyList())
        coEvery {
            mockPopupRepository.setItems(any(), any(), any())
        } throws RuntimeException("Could not change the items")
        val picker: IPopupPicker = createPicker()
        backgroundScope.launch { picker.pickerState.collect() }
        picker.startPicking(inputPrivateApp)
        runCurrent()
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            picker.confirmPicking(selectedInOrder = listOf(inputOtherApp.key))
            runCurrent()
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.key, inputPrivateApp.packageName)
    }

    @Test
    fun `cancelling the shortcut picker changes nothing`() = runTest {
        setAppsWithShortcuts(apps = listOf(inputOtherAppShortcuts))
        val picker: IPopupPicker = createPicker()
        picker.shortcutPickerState.test {
            picker.startPickingShortcuts(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            picker.cancelShortcutPicking()
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        coVerify(exactly = 0) { mockPopupRepository.setItems(any(), any(), any()) }
    }

    @Test
    fun `shortcut picker lists paused Apps and leaves out Apps with no shortcuts`() = runTest {
        setLaunchableGroups(groups = inputPickerGroups + LaunchableGroup.InitialGroup(label = "P", items = listOf(inputPausedApp)))
        setAppsWithShortcuts(
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
            assertEquals(listOf("P"), picking.groups.map { group -> group.launchableGroup.label })
            assertEquals(listOf(inputPausedApp), picking.groups.flatMap { group -> group.apps }.map { appShortcuts -> appShortcuts.app })
        }
    }

    @Test
    fun `shortcut picker reports when shortcuts are unavailable`() = runTest {
        setLaunchableGroups(groups = inputPickerGroups)
        setAppsWithShortcuts(
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
