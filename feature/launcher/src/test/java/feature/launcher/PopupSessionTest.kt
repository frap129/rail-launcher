package feature.launcher

import app.cash.turbine.test
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutLaunchable
import core.data.testing.TEST_SDK
import core.ui.popups.AddToFolderState
import core.ui.popups.FolderDialogState
import core.ui.popups.PopupEditState
import core.ui.popups.PopupPickerState
import core.ui.popups.ShortcutPickerState
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
class PopupSessionTest : PopupSessionTestBase() {

    @Test
    fun `removing an added item from the editor ignores another Pop-up's item menu`() = runTest {
        val session: PopupTestSession = openPopupWithEditor()
        session.card.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(session.card.menuState.value is PopupMenuState.Open)
        assertTrue(session.editor.editState.value is PopupEditState.Editing)

        session.editor.removeEditedItem(inputRailLaunchable)
        runCurrent()
        setPopupLaunchables(ownerKey = inputFolder.key, launchables = emptyList())
        runCurrent()

        verifyItemRemoved(ownerKey = inputFolder.key, itemKey = inputRailLaunchable.key)
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
        setPopupLaunchables(ownerKey = inputApp.key, launchables = emptyList())
        runCurrent()

        verifyItemRemoved(ownerKey = inputApp.key, itemKey = inputOtherApp.key)
        assertEquals(emptyList<PopupItem>(), (session.card.popupState.value as PopupUiState.Open).content.items)
        assertEquals(
            listOf(PopupItem.Launchable(inputRailLaunchable)),
            (session.editor.editState.value as PopupEditState.Editing).items
        )
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
        coVerify(exactly = 1) { mockPopupRepository.setItems(any(), any(), any()) }
        coVerify(exactly = 1) {
            mockPopupRepository.setItems(inputFolder.key, listOf(inputOtherApp.key), emptySet())
        }
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

    @Test
    fun `deleting a Folder confirms, closes its card and removes it`() = runTest {
        val session: PopupTestSession = createSession()
        backgroundScope.launch { session.card.popupState.collect() }
        backgroundScope.launch { session.card.menuState.collect() }
        session.card.open(inputFolder, TEST_INPUT_ANCHOR)
        runCurrent()
        session.card.openMenu()
        runCurrent()
        session.folder.requestDeleteFolder(inputFolder)
        assertEquals(FolderDialogState.Deleting(inputFolder), session.folder.folderDialogState.value)
        session.folder.confirmDeleteFolder()
        runCurrent()
        assertEquals(FolderDialogState.Closed, session.folder.folderDialogState.value)
        assertEquals(PopupUiState.Closed, session.card.popupState.value)
        coVerify(exactly = 1) { mockFolderRepository.delete(any()) }
        coVerify(exactly = 1) { mockFolderRepository.delete(inputFolder) }
    }

    @Test
    fun `cancelling Folder deletion keeps the card and the Folder`() = runTest {
        val session: PopupTestSession = createSession()
        backgroundScope.launch { session.card.popupState.collect() }
        backgroundScope.launch { session.card.menuState.collect() }
        session.card.open(inputFolder, TEST_INPUT_ANCHOR)
        runCurrent()
        session.card.openMenu()
        runCurrent()
        session.folder.requestDeleteFolder(inputFolder)
        session.folder.dismissFolderDialog()
        assertEquals(FolderDialogState.Closed, session.folder.folderDialogState.value)
        assertTrue(session.card.popupState.value is PopupUiState.Open)
        coVerify(exactly = 0) { mockFolderRepository.delete(any()) }
    }

    @Test
    fun `Back closes the add to folder sheet`() = runTest {
        val session: PopupTestSession = createSession()
        backgroundScope.launch { session.folder.addToFolderState.collect() }
        session.folder.startAddToFolder(inputApp)
        runCurrent()

        assertTrue(session.card.onBack())

        assertEquals(AddToFolderState.Closed, session.folder.addToFolderState.value)
    }

    @Test
    fun `starting an edit closes the add to folder sheet`() = runTest {
        val session: PopupTestSession = createSession()
        backgroundScope.launch { session.folder.addToFolderState.collect() }
        session.folder.startAddToFolder(inputApp)
        runCurrent()

        session.editor.startEdit(inputApp)
        runCurrent()

        assertEquals(AddToFolderState.Closed, session.folder.addToFolderState.value)
    }

    private fun verifyItemRemoved(ownerKey: String, itemKey: String) {
        coVerify(exactly = 1) { mockPopupRepository.removeItem(any(), any()) }
        coVerify(exactly = 1) { mockPopupRepository.removeItem(ownerKey, itemKey) }
    }
}
