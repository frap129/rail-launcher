package core.popups

import app.cash.turbine.test
import core.data.folders.Folder
import core.data.launchables.model.LaunchableGroup
import core.data.testing.FakePreferencesRepository
import core.data.testing.FolderCreateRequest
import core.data.testing.TEST_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val RENAMED_NAME: String = "Hobbies"
private const val BLANK_NAME: String = "   "

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupFolderViewModelTest : PopupTestBase() {

    @Test
    fun `shows a Folder in the App List`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()

        viewModel.setShowInAppList(inputFolder, true)
        runCurrent()

        assertEquals(listOf(inputFolder to true), fakeFolderRepository.showInAppListChanges)
    }

    @Test
    fun `opens the rename dialog for the Folder`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()

        viewModel.requestRename(inputFolder)

        assertEquals(FolderDialogState.Renaming(inputFolder), viewModel.folderDialogState.value)
    }

    @Test
    fun `renames the Folder through the preferences repository`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        viewModel.requestRename(inputFolder)

        viewModel.confirmRename(RENAMED_NAME)
        runCurrent()

        assertEquals(FolderDialogState.Closed, viewModel.folderDialogState.value)
        assertEquals(
            listOf(FakePreferencesRepository.ItemRename(item = inputFolder, name = RENAMED_NAME)),
            mockPreferencesRepository.renames
        )
    }

    @Test
    fun `a blank rename resets the Folder name`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        viewModel.requestRename(inputFolder)

        viewModel.confirmRename(BLANK_NAME)
        runCurrent()

        assertEquals(listOf(inputFolder), mockPreferencesRepository.resets)
    }

    @Test
    fun `dismissing the rename dialog keeps the Folder name`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        viewModel.requestRename(inputFolder)

        viewModel.dismissFolderDialog()

        assertEquals(FolderDialogState.Closed, viewModel.folderDialogState.value)
        assertTrue(mockPreferencesRepository.renames.isEmpty())
    }

    @Test
    fun `add to folder lists the Folders in creation order and reflects membership`() = runTest {
        fakePopupRepository.setItems(ownerKey = inputSecondFolder.key, items = listOf(inputApp))
        val viewModel: PopupFolderViewModel = createFolder()
        backgroundScope.launch { viewModel.addToFolderState.collect() }

        viewModel.startAddToFolder(inputApp)
        runCurrent()

        val actualState: AddToFolderState.Open = viewModel.addToFolderState.value as AddToFolderState.Open
        assertEquals(inputApp, actualState.launchable)
        assertEquals(listOf(inputFolder, inputSecondFolder), actualState.folders)
        assertEquals(setOf(inputSecondFolder.key), actualState.membership)
    }

    @Test
    fun `toggling membership adds and removes the app immediately`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        viewModel.toggleMembership(folderKey = inputFolder.key, member = true)
        runCurrent()

        assertEquals(setOf(inputFolder.key), (viewModel.addToFolderState.value as AddToFolderState.Open).membership)
        assertEquals(
            listOf(Triple(inputFolder.key, listOf(inputApp.key), emptySet<String>())),
            fakePopupRepository.setItemsCalls
        )

        viewModel.toggleMembership(folderKey = inputFolder.key, member = false)
        runCurrent()

        assertTrue((viewModel.addToFolderState.value as AddToFolderState.Open).membership.isEmpty())
        assertEquals(
            Triple(inputFolder.key, emptyList<String>(), setOf(inputApp.key)),
            fakePopupRepository.setItemsCalls.last()
        )
    }

    @Test
    fun `startNewFolder creates a favorite Folder with the app and reports it`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        val addedFolders: MutableList<Folder> = mutableListOf()
        backgroundScope.launch { viewModel.folderAdditions.collect { folder -> addedFolders += folder } }
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        viewModel.startNewFolder(name = "Games")
        runCurrent()

        assertEquals(
            listOf(FolderCreateRequest(name = "Games", initialItems = listOf(inputApp.key), addToFavorites = true)),
            fakeFolderRepository.createRequests
        )
        assertEquals(listOf("Games"), addedFolders.map { folder -> folder.name })
    }

    @Test
    fun `the add to folder sheet closes when its Launchable disappears`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()
        assertTrue(viewModel.addToFolderState.value is AddToFolderState.Open)

        fakeLaunchableRepository.setGroups(
            groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
        )
        runCurrent()

        assertEquals(AddToFolderState.Closed, viewModel.addToFolderState.value)
    }

    @Test
    fun `a deleted Folder disappears from the add to folder sheet`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        fakeFolderRepository.delete(inputSecondFolder)
        runCurrent()

        val actualState: AddToFolderState.Open = viewModel.addToFolderState.value as AddToFolderState.Open
        assertEquals(listOf(inputFolder), actualState.folders)
    }

    @Test
    fun `ignores adding a Folder to a Folder`() = runTest {
        val viewModel: PopupFolderViewModel = createFolder()
        viewModel.addToFolderState.test {
            assertEquals(AddToFolderState.Closed, expectMostRecentItem())

            viewModel.startAddToFolder(inputFolder)
            runCurrent()

            expectNoEvents()
        }
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
        assertEquals(listOf(inputFolder), fakeFolderRepository.deletedFolders)
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
        assertTrue(fakeFolderRepository.deletedFolders.isEmpty())
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
}
