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
class IPopupFolderActionsImplTest : PopupTestBase() {

    @Test
    fun `shows a Folder in the App List`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()

        folderActions.setShowInAppList(inputFolder, true)
        runCurrent()

        assertEquals(listOf(inputFolder to true), fakeFolderRepository.showInAppListChanges)
    }

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
        assertEquals(
            listOf(FakePreferencesRepository.ItemRename(item = inputFolder, name = RENAMED_NAME)),
            mockPreferencesRepository.renames
        )
    }

    @Test
    fun `a blank rename resets the Folder name`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        folderActions.requestRename(inputFolder)

        folderActions.confirmRename(BLANK_NAME)
        runCurrent()

        assertEquals(listOf(inputFolder), mockPreferencesRepository.resets)
    }

    @Test
    fun `dismissing the rename dialog keeps the Folder name`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        folderActions.requestRename(inputFolder)

        folderActions.dismissFolderDialog()

        assertEquals(FolderDialogState.Closed, folderActions.folderDialogState.value)
        assertTrue(mockPreferencesRepository.renames.isEmpty())
    }

    @Test
    fun `add to folder lists the Folders in creation order and reflects membership`() = runTest {
        fakePopupRepository.setItems(ownerKey = inputSecondFolder.key, items = listOf(inputApp))
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

        assertEquals(setOf(inputFolder.key), (folderActions.addToFolderState.value as AddToFolderState.Open).membership)
        assertEquals(
            listOf(Triple(inputFolder.key, listOf(inputApp.key), emptySet<String>())),
            fakePopupRepository.setItemsCalls
        )

        folderActions.toggleMembership(folderKey = inputFolder.key, member = false)
        runCurrent()

        assertTrue((folderActions.addToFolderState.value as AddToFolderState.Open).membership.isEmpty())
        assertEquals(
            Triple(inputFolder.key, emptyList<String>(), setOf(inputApp.key)),
            fakePopupRepository.setItemsCalls.last()
        )
    }

    @Test
    fun `startNewFolder creates a favorite Folder with the app and reports it`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        val addedFolders: MutableList<Folder> = mutableListOf()
        backgroundScope.launch { folderActions.folderAdditions.collect { folder -> addedFolders += folder } }
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputApp)
        runCurrent()

        folderActions.startNewFolder(name = "Games")
        runCurrent()

        assertEquals(
            listOf(FolderCreateRequest(name = "Games", initialItems = listOf(inputApp.key), addToFavorites = true)),
            fakeFolderRepository.createRequests
        )
        assertEquals(listOf("Games"), addedFolders.map { folder -> folder.name })
    }

    @Test
    fun `the add to folder sheet closes when its Launchable disappears`() = runTest {
        val folderActions: IPopupFolderActions = createFolder()
        backgroundScope.launch { folderActions.addToFolderState.collect() }
        folderActions.startAddToFolder(inputApp)
        runCurrent()
        assertTrue(folderActions.addToFolderState.value is AddToFolderState.Open)

        fakeLaunchableRepository.setGroups(
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

        fakeFolderRepository.delete(inputSecondFolder)
        runCurrent()

        val actualState: AddToFolderState.Open = folderActions.addToFolderState.value as AddToFolderState.Open
        assertEquals(listOf(inputFolder), actualState.folders)
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
