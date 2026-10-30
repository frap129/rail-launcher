package core.ui.popups

import app.cash.turbine.test
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createFolderRepositoryMock
import core.data.testing.createPrivateApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.verify
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
class IPopupFolderActionsImplTest : PopupTestBase() {

    @Test
    fun `opens the rename dialog for the Folder`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()

        folderActions.requestRename(inputFolder)

        assertEquals(FolderDialogState.Renaming(inputFolder), folderActions.folderDialogState.value)
    }

    @Test
    fun `renames the Folder through the preferences repository`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        folderActions.requestRename(inputFolder)

        folderActions.confirmRename(RENAMED_NAME)
        runCurrent()

        assertEquals(FolderDialogState.Closed, folderActions.folderDialogState.value)
        verifyItemName(item = inputFolder, name = RENAMED_NAME)
    }

    @Test
    fun `forwards a blank rename to the preferences repository`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        folderActions.requestRename(inputFolder)

        folderActions.confirmRename(BLANK_NAME)
        runCurrent()

        verifyItemName(item = inputFolder, name = BLANK_NAME)
    }

    @Test
    fun `dismissing the rename dialog keeps the Folder name`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        folderActions.requestRename(inputFolder)

        folderActions.dismissFolderDialog()

        assertEquals(FolderDialogState.Closed, folderActions.folderDialogState.value)
        verify(exactly = 0) { mockPreferencesRepository.setItemName(any(), any()) }
    }

    @Test
    fun `add to folder lists the Folders in creation order and reflects membership`() = runTest {
        setPopupLaunchables(ownerKey = inputSecondFolder.key, launchables = listOf(inputApp))
        val folderActions: IPopupFolderActions = createFolder()
        backgroundScope.launch { folderActions.addToFolderState.collect() }

        folderActions.startAddToFolder(inputApp)
        runCurrent()

        val actualState: AddToFolderState.Open = folderActions.addToFolderState.value as AddToFolderState.Open
        assertEquals(inputApp, actualState.launchable)
        assertEquals(listOf(inputFolder, inputSecondFolder), actualState.folders)
        assertEquals(setOf(inputSecondFolder.key), actualState.membership)
    }

    @Test
    fun `toggling membership adds and removes the app immediately`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputApp)
        runCurrent()

        folderActions.toggleMembership(folderKey = inputFolder.key, member = true)
        runCurrent()
        setPopupLaunchables(ownerKey = inputFolder.key, launchables = listOf(inputApp))
        runCurrent()

        assertEquals(setOf(inputFolder.key), (folderActions.addToFolderState.value as AddToFolderState.Open).membership)
        coVerify(exactly = 1) { mockPopupRepository.setItems(any(), any(), any()) }
        coVerify(exactly = 1) {
            mockPopupRepository.setItems(inputFolder.key, listOf(inputApp.key), emptySet())
        }

        folderActions.toggleMembership(folderKey = inputFolder.key, member = false)
        runCurrent()
        setPopupLaunchables(ownerKey = inputFolder.key, launchables = emptyList())
        runCurrent()

        assertTrue((folderActions.addToFolderState.value as AddToFolderState.Open).membership.isEmpty())
        coVerifyOrder {
            mockPopupRepository.setItems(inputFolder.key, listOf(inputApp.key), emptySet())
            mockPopupRepository.setItems(inputFolder.key, emptyList(), setOf(inputApp.key))
        }
        coVerify(exactly = 2) { mockPopupRepository.setItems(any(), any(), any()) }
    }

    @Test
    fun `logs a private Launchable Folder membership failure without its package or key`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        setLaunchableGroups(groups = listOf(LaunchableGroup.InitialGroup(label = "S", items = listOf(inputPrivateApp))))
        coEvery {
            mockPopupRepository.setItems(any(), any(), any())
        } throws RuntimeException("Could not change Folder membership")
        val folderActions: IPopupFolderActions = createFolder()
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputPrivateApp)
        runCurrent()
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            folderActions.toggleMembership(folderKey = inputFolder.key, member = true)
            runCurrent()
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.key, inputPrivateApp.packageName)
    }

    @Test
    fun `startNewFolder creates a favorite Folder with the app and reports it`() = runTest {
        val mockFolderRepository: IFolderRepository = createFolderRepositoryMock(
            createdFolder = Folder(folderId = 1L, name = "Games")
        )
        val folderActions: IPopupFolderActions = createFolder(mockFolderRepository = mockFolderRepository)
        val addedFolders: MutableList<Folder> = mutableListOf()
        backgroundScope.launch { folderActions.folderAdditions.collect { folder -> addedFolders += folder } }
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputApp)
        runCurrent()

        folderActions.startNewFolder(name = "Games")
        runCurrent()

        coVerify(exactly = 1) { mockFolderRepository.create(any(), any(), any()) }
        coVerify(exactly = 1) {
            mockFolderRepository.create(
                name = "Games",
                initialItems = listOf(inputApp.key),
                addToFavorites = true
            )
        }
        assertEquals(listOf("Games"), addedFolders.map { folder -> folder.name })
    }

    @Test
    fun `the add to folder sheet closes when its Launchable disappears`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputApp)
        runCurrent()
        assertTrue(folderActions.addToFolderState.value is AddToFolderState.Open)

        setLaunchableGroups(
            groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
        )
        runCurrent()

        assertEquals(AddToFolderState.Closed, folderActions.addToFolderState.value)
    }

    @Test
    fun `a deleted Folder disappears from the add to folder sheet`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputApp)
        runCurrent()

        deleteFolder(inputSecondFolder)
        runCurrent()

        val actualState: AddToFolderState.Open = folderActions.addToFolderState.value as AddToFolderState.Open
        assertEquals(listOf(inputFolder), actualState.folders)
    }

    private fun verifyItemName(item: Launchable, name: String) {
        verify(exactly = 1) { mockPreferencesRepository.setItemName(any(), any()) }
        verify(exactly = 1) { mockPreferencesRepository.setItemName(item, name) }
    }

    @Test
    fun `ignores adding a Folder to a Folder`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        folderActions.addToFolderState.test {
            assertEquals(AddToFolderState.Closed, expectMostRecentItem())

            folderActions.startAddToFolder(inputFolder)
            runCurrent()

            expectNoEvents()
        }
    }
}
