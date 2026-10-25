package core.popups

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupRepository
import core.data.testing.FakeShortcutSource
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_SDK
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PACKAGE_NAME: String = "com.example.app"
private const val ACTIVITY_NAME: String = "com.example.app.MainActivity"
private val INPUT_ANCHOR: PopupAnchor = PopupAnchor(rowTop = 400, rowBottom = 464, iconCenter = Offset(32f, 432f))

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val profile: UserHandle = UserHandle.getUserHandleForUid(0)
    private val inputApp: App = App(
        defaultName = "Example",
        profile = profile,
        componentName = ComponentName(PACKAGE_NAME, ACTIVITY_NAME)
    )
    private val inputOtherApp: App = App(
        defaultName = "Other",
        profile = profile,
        componentName = ComponentName("com.example.other", "com.example.other.MainActivity")
    )
    private val inputPausedApp: App = App(
        defaultName = "Paused",
        profile = profile,
        componentName = ComponentName("com.example.paused", "com.example.paused.MainActivity")
    )
    private val inputRailLaunchable: RailLaunchable = RailLaunchable(
        defaultName = "Rail Settings",
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail-launcher://settings/main"
    )
    private val inputGroups: List<LaunchableGroup> = listOf(
        LaunchableGroup.Star,
        LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
        LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
    )
    private val inputShortcut: ShortcutLaunchable = ShortcutLaunchable(
        packageName = PACKAGE_NAME,
        shortcutId = "shortcut-id",
        profile = profile,
        shortLabel = "Short label",
        longLabel = "Long label"
    )
    private val inputOtherShortcut: ShortcutLaunchable = ShortcutLaunchable(
        packageName = inputOtherApp.packageName,
        shortcutId = "other-shortcut-id",
        profile = profile,
        shortLabel = "Other short label",
        longLabel = "Other long label"
    )
    private val inputAppShortcuts: AppShortcuts = AppShortcuts(
        app = inputApp,
        result = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
    )
    private val inputOtherAppShortcuts: AppShortcuts = AppShortcuts(
        app = inputOtherApp,
        result = ShortcutsResult.Available(shortcuts = listOf(inputOtherShortcut))
    )
    private val fakeLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = inputGroups)
    private val fakeShortcutSource: FakeShortcutSource = FakeShortcutSource()
    private val fakePopupRepository: FakePopupRepository = FakePopupRepository()
    private val inputHomeEditState: HomeEditState = HomeEditState()

    @Test
    fun `starts closed`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `opens the swiped App with an empty shortcut list while loading`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputApp, actualState.owner)
            assertEquals(INPUT_ANCHOR, actualState.anchor)
            assertEquals(PopupEmptyReason.Empty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `updates the open card when shortcuts arrive`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            assertEquals(PopupEmptyReason.Empty, (expectMostRecentItem() as PopupUiState.Open).content.emptyReason)
            fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.None, actualState.content.emptyReason)
        }
    }

    @Test
    fun `ignores open for a Rail Launchable`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputRailLaunchable, INPUT_ANCHOR)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `ignores open while a Home edit mode is active`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `closes on Back and reports whether a Pop-up was open`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            assertTrue(viewModel.onBack())
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            assertFalse(viewModel.onBack())
        }
    }

    @Test
    fun `closes on the Home intent`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            viewModel.onHomeIntent()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes after a launch`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            viewModel.onLaunched()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes when a Home edit mode starts`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            inputHomeEditState.tryEnter(HomeEditMode.Favorites)
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes when the owner disappears after loading`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            fakeLaunchableRepository.setGroups(
                groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
            )
            runCurrent()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `moves an open Pop-up when its row is laid out again`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        val updatedAnchor: PopupAnchor = INPUT_ANCHOR.copy(rowTop = 800, rowBottom = 864, iconCenter = Offset(32f, 832f))
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            assertEquals(INPUT_ANCHOR, (expectMostRecentItem() as PopupUiState.Open).anchor)
            viewModel.onAnchorChanged(inputApp.key, updatedAnchor)
            assertEquals(updatedAnchor, (expectMostRecentItem() as PopupUiState.Open).anchor)
        }
    }

    @Test
    fun `requests the owner's shortcuts again after a reload`() = runTest {
        val viewModel: PopupViewModel = createViewModel()

        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputApp, actualState.owner)
            assertEquals(listOf(PACKAGE_NAME to profile), fakeShortcutSource.requests)
            viewModel.reloadShortcuts()
            runCurrent()
            assertEquals(listOf(PACKAGE_NAME to profile, PACKAGE_NAME to profile), fakeShortcutSource.requests)
        }
    }

    @Test
    fun `opens the menu with Edit Pop-up and App info for an App owner`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
            assertEquals(inputApp, actualMenu.target)
            assertEquals(listOf(PopupMenuItem.EditPopup, PopupMenuItem.AppInfo), actualMenu.items)
        }
    }

    @Test
    fun `closes the menu without closing the card`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            viewModel.closeMenu()
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
        viewModel.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
        }
    }

    @Test
    fun `starting an edit closes the menu`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            viewModel.startEdit(inputApp)
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `edits an App's Pop-up with its live shortcuts and hidden ids`() = runTest {
        val inputShortcuts: ShortcutsResult = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
        fakeShortcutSource.setResult(inputShortcuts)
        fakePopupRepository.setHiddenShortcuts(inputApp.key, setOf(inputShortcut.shortcutId))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(inputApp, actualState.owner)
            assertEquals(inputShortcuts, actualState.shortcuts)
            assertEquals(setOf(inputShortcut.shortcutId), actualState.hiddenIds)
        }
    }

    @Test
    fun `updates the editor when the shortcut list changes`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)), actualState.shortcuts)
        }
    }

    @Test
    fun `allows editing during Edit Favorites`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
        }
    }

    @Test
    fun `ignores editing in Widgets and Resizing`() = runTest {
        listOf(HomeEditMode.Widgets, HomeEditMode.Resizing(placeId = 1L)).forEach { inputMode ->
            val editState: HomeEditState = HomeEditState()
            editState.tryEnter(inputMode)
            val viewModel: PopupViewModel = createViewModel(homeEditState = editState)
            viewModel.editState.test {
                assertEquals(PopupEditState.Closed, expectMostRecentItem())
                viewModel.startEdit(inputApp)
                runCurrent()
                expectNoEvents()
                assertEquals(PopupEditState.Closed, viewModel.editState.value)
            }
        }
    }

    @Test
    fun `ignores editing a Launchable that is not an App`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputRailLaunchable)
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `forwards a shortcut hidden state change for the edited App`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
        runCurrent()
        assertEquals(listOf(Triple(inputApp.key, inputShortcut.shortcutId, true)), fakePopupRepository.setShortcutHiddenCalls)
    }

    @Test
    fun `updates the card content when a shortcut is hidden`() = runTest {
        fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            assertEquals(listOf(inputShortcut), (expectMostRecentItem() as PopupUiState.Open).content.shortcuts)
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
            runCurrent()
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(emptyList<ShortcutLaunchable>(), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.Empty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `closes the editor when its owner disappears`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            fakeLaunchableRepository.setGroups(
                groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
            )
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `Back closes the editor before the card`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            assertTrue(viewModel.onBack())
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
        viewModel.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
        }
    }

    @Test
    fun `Back closes the menu before the card`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            assertTrue(viewModel.onBack())
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
        viewModel.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
            assertTrue(viewModel.onBack())
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            assertFalse(viewModel.onBack())
        }
    }

    @Test
    fun `edits an App's Pop-up with its live items`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), actualState.items)
        }
    }

    @Test
    fun `edit sheet keeps disabled shortcuts`() = runTest {
        fakePopupRepository.setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(
                PopupItem.Shortcut(launchable = inputShortcut, isEnabled = false),
                PopupItem.Launchable(launchable = inputOtherApp)
            )
        )
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(
                listOf(
                    PopupItem.Shortcut(launchable = inputShortcut, isEnabled = false),
                    PopupItem.Launchable(inputOtherApp)
                ),
                actualState.items
            )
            assertEquals(mapOf(inputShortcut.key to inputApp.defaultName), actualState.shortcutSourceNames)
        }
    }

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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.pickerState.test {
            assertEquals(PopupPickerState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPicking()
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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertTrue(picking.shortcutsUnavailable)
        }
    }

    @Test
    fun `item menu shows App info only for App items`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp, inputRailLaunchable))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
            viewModel.menuState.test {
                viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
                val appMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
                assertEquals(inputOtherApp, appMenu.target)
                assertEquals(
                    listOf(PopupMenuItem.RemoveFromPopup, PopupMenuItem.AppInfo),
                    appMenu.items
                )
                viewModel.closeMenu()
                assertEquals(PopupMenuState.Closed, expectMostRecentItem())
                viewModel.openItemMenu(PopupItem.Launchable(inputRailLaunchable))
                val railMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
                assertEquals(inputRailLaunchable, railMenu.target)
                assertEquals(listOf(PopupMenuItem.RemoveFromPopup), railMenu.items)
            }
        }
    }

    @Test
    fun `removing an item closes its menu and updates the card`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        val openState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), openState.content.items)
        viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(viewModel.menuState.value is PopupMenuState.Open)
        viewModel.removeItem(inputOtherApp)
        runCurrent()
        assertEquals(PopupMenuState.Closed, viewModel.menuState.value)
        val updatedState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<PopupItem>(), updatedState.content.items)
        assertEquals(PopupEmptyReason.Empty, updatedState.content.emptyReason)
        assertEquals(listOf(inputApp.key to inputOtherApp.key), fakePopupRepository.removedItems)
    }

    @Test
    fun `removing an item while editing uses the edited owner`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            viewModel.removeItem(inputOtherApp)
            runCurrent()
            val updatedState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(emptyList<PopupItem>(), updatedState.items)
        }
        assertEquals(listOf(inputApp.key to inputOtherApp.key), fakePopupRepository.removedItems)
    }

    @Test
    fun `sets the item order for the edited App`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.startEdit(inputApp)
        runCurrent()
        val orderedKeys: List<String> = listOf(inputOtherApp.key, inputApp.key)

        viewModel.setItemOrder(orderedItemKeys = orderedKeys)
        runCurrent()

        assertEquals(listOf(inputApp.key to orderedKeys), fakePopupRepository.itemOrders)
    }

    @Test
    fun `Back closes the shortcut picker before the editor`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.editState.collect() }
        backgroundScope.launch { viewModel.shortcutPickerState.collect() }
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.startPickingShortcuts()
        runCurrent()
        assertTrue(viewModel.shortcutPickerState.value is ShortcutPickerState.Picking)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)

        assertTrue(viewModel.onBack())

        assertEquals(ShortcutPickerState.Closed, viewModel.shortcutPickerState.value)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)
    }

    @Test
    fun `Back closes the item menu before the editor`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.editState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(viewModel.menuState.value is PopupMenuState.Open)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)

        assertTrue(viewModel.onBack())

        assertEquals(PopupMenuState.Closed, viewModel.menuState.value)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)
    }

    private fun createViewModel(homeEditState: HomeEditState = inputHomeEditState): PopupViewModel = PopupViewModel(
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        homeEditState = homeEditState
    )
}
