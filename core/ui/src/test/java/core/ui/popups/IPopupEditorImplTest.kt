package core.ui.popups

import app.cash.turbine.test
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IPopupEditorImplTest : PopupTestBase() {

    @Test
    fun `edits an App's Pop-up with its live shortcuts and hidden ids`() = runTest {
        val inputShortcuts: ShortcutsResult = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
        setShortcutResult(inputShortcuts)
        setHiddenShortcuts(inputApp.key, setOf(inputShortcut.shortcutId))
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            editor.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(inputApp, actualState.owner)
            assertEquals(inputShortcuts, actualState.shortcuts)
            assertEquals(setOf(inputShortcut.shortcutId), actualState.hiddenIds)
        }
    }

    @Test
    fun `updates the editor when the shortcut list changes`() = runTest {
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            editor.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            setShortcutResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)), actualState.shortcuts)
        }
    }

    @Test
    fun `allows editing during Edit Favorites`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            editor.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
        }
    }

    @Test
    fun `ignores editing in Widgets and Resizing`() = runTest {
        listOf(HomeEditMode.Widgets, HomeEditMode.Resizing(placeId = 1L)).forEach { inputMode ->
            val editState: HomeEditState = HomeEditState()
            editState.tryEnter(inputMode)
            val editor: IPopupEditor = createEditor(homeEditState = editState)
            editor.editState.test {
                assertEquals(PopupEditState.Closed, expectMostRecentItem())
                editor.startEdit(inputApp)
                runCurrent()
                expectNoEvents()
                assertEquals(PopupEditState.Closed, editor.editState.value)
            }
        }
    }

    @Test
    fun `ignores editing a Launchable that is not an App`() = runTest {
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            editor.startEdit(inputRailLaunchable)
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `forwards a shortcut hidden state change for the edited App`() = runTest {
        val editor: IPopupEditor = createEditor()
        editor.startEdit(inputApp)
        runCurrent()
        editor.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
        runCurrent()
        coVerify(exactly = 1) { mockPopupRepository.setShortcutHidden(any(), any(), any()) }
        coVerify(exactly = 1) { mockPopupRepository.setShortcutHidden(inputApp.key, inputShortcut.shortcutId, true) }
    }

    @Test
    fun `closes the editor when its owner disappears`() = runTest {
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            editor.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            setLaunchableGroups(
                groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
            )
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `edits an App's Pop-up with its live items`() = runTest {
        seedPopupItem()
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            editor.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), actualState.items)
        }
    }

    @Test
    fun `edit sheet keeps disabled shortcuts`() = runTest {
        setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(
                PopupItem.Shortcut(launchable = inputShortcut, isEnabled = false),
                PopupItem.Launchable(launchable = inputOtherApp)
            )
        )
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            editor.startEdit(inputApp)
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
    fun `removing an item while editing uses the edited owner`() = runTest {
        seedPopupItem()
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            editor.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            editor.removeEditedItem(inputOtherApp)
            runCurrent()
            val updatedState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(emptyList<PopupItem>(), updatedState.items)
        }
        coVerify(exactly = 1) { mockPopupRepository.removeItem(any(), any()) }
        coVerify(exactly = 1) { mockPopupRepository.removeItem(inputApp.key, inputOtherApp.key) }
    }

    @Test
    fun `sets the item order for the edited App`() = runTest {
        val editor: IPopupEditor = createEditor()
        editor.startEdit(inputApp)
        runCurrent()
        val orderedKeys: List<String> = listOf(inputOtherApp.key, inputApp.key)

        editor.setItemOrder(orderedItemKeys = orderedKeys)
        runCurrent()

        coVerify(exactly = 1) { mockPopupRepository.setItemOrder(any(), any()) }
        coVerify(exactly = 1) { mockPopupRepository.setItemOrder(inputApp.key, orderedKeys) }
    }

    private fun seedPopupItem(item: Launchable = inputOtherApp) {
        setPopupLaunchables(ownerKey = inputApp.key, launchables = listOf(item))
    }

    @Test
    fun `edits a Folder during Edit Favorites`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val editor: IPopupEditor = createEditor()
        editor.editState.test {
            editor.startEdit(inputFolder)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(inputFolder, actualState.owner)
            assertEquals(ShortcutsResult.Available(shortcuts = emptyList()), actualState.shortcuts)
        }
    }
}
