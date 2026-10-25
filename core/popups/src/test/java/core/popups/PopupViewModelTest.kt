package core.popups

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.icons.model.Icon
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
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
            assertEquals(inputApp, actualMenu.owner)
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

    private fun createViewModel(homeEditState: HomeEditState = inputHomeEditState): PopupViewModel = PopupViewModel(
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        homeEditState = homeEditState
    )
}
