package core.popups

import app.cash.turbine.test
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutsResult
import core.data.testing.TEST_SDK
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
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
class PopupEditorViewModelTest : PopupTestBase() {

    @Test
    fun `edits an App's Pop-up with its live shortcuts and hidden ids`() = runTest {
        val inputShortcuts: ShortcutsResult = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
        fakeShortcutSource.setResult(inputShortcuts)
        fakePopupRepository.setHiddenShortcuts(inputApp.key, setOf(inputShortcut.shortcutId))
        val viewModel: PopupEditorViewModel = createEditor()
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
        val viewModel: PopupEditorViewModel = createEditor()
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
        val viewModel: PopupEditorViewModel = createEditor()
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
            val viewModel: PopupEditorViewModel = createEditor(homeEditState = editState)
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
        val viewModel: PopupEditorViewModel = createEditor()
        viewModel.editState.test {
            viewModel.startEdit(inputRailLaunchable)
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `forwards a shortcut hidden state change for the edited App`() = runTest {
        val viewModel: PopupEditorViewModel = createEditor()
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
        runCurrent()
        assertEquals(listOf(Triple(inputApp.key, inputShortcut.shortcutId, true)), fakePopupRepository.setShortcutHiddenCalls)
    }

    @Test
    fun `closes the editor when its owner disappears`() = runTest {
        val viewModel: PopupEditorViewModel = createEditor()
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
    fun `edits an App's Pop-up with its live items`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupEditorViewModel = createEditor()
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
        val viewModel: PopupEditorViewModel = createEditor()
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
    fun `removing an item while editing uses the edited owner`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupEditorViewModel = createEditor()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            viewModel.removeEditedItem(inputOtherApp)
            runCurrent()
            val updatedState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(emptyList<PopupItem>(), updatedState.items)
        }
        assertEquals(listOf(inputApp.key to inputOtherApp.key), fakePopupRepository.removedItems)
    }

    @Test
    fun `sets the item order for the edited App`() = runTest {
        val viewModel: PopupEditorViewModel = createEditor()
        viewModel.startEdit(inputApp)
        runCurrent()
        val orderedKeys: List<String> = listOf(inputOtherApp.key, inputApp.key)

        viewModel.setItemOrder(orderedItemKeys = orderedKeys)
        runCurrent()

        assertEquals(listOf(inputApp.key to orderedKeys), fakePopupRepository.itemOrders)
    }

    @Test
    fun `edits a Folder during Edit Favorites`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: PopupEditorViewModel = createEditor()
        viewModel.editState.test {
            viewModel.startEdit(inputFolder)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(inputFolder, actualState.owner)
            assertEquals(ShortcutsResult.Available(shortcuts = emptyList()), actualState.shortcuts)
        }
    }

    @Test
    fun `removing an added item from the editor ignores another Pop-up's item menu`() = runTest {
        val session: PopupTestSession = openPopupWithEditor()
        session.card.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(session.card.menuState.value is PopupMenuState.Open)
        assertTrue(session.editor.editState.value is PopupEditState.Editing)

        session.editor.removeEditedItem(inputRailLaunchable)
        runCurrent()

        assertEquals(listOf(inputFolder.key to inputRailLaunchable.key), fakePopupRepository.removedItems)
        val actualPopup: PopupUiState.Open = session.card.popupState.value as PopupUiState.Open
        assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), actualPopup.content.items)
        assertEquals(emptyList<PopupItem>(), (session.editor.editState.value as PopupEditState.Editing).items)
    }

    @Test
    fun `removing an added item from the item menu uses the owning Pop-up`() = runTest {
        val session: PopupTestSession = openPopupWithEditor()
        session.card.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(session.card.menuState.value is PopupMenuState.Open)

        session.card.removeMenuItem()
        runCurrent()

        assertEquals(listOf(inputApp.key to inputOtherApp.key), fakePopupRepository.removedItems)
        assertEquals(emptyList<PopupItem>(), (session.card.popupState.value as PopupUiState.Open).content.items)
        assertEquals(
            listOf(PopupItem.Launchable(inputRailLaunchable)),
            (session.editor.editState.value as PopupEditState.Editing).items
        )
    }
}
