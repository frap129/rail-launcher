package core.data.folders

import core.data.icons.model.Icon
import core.data.launchables.model.LaunchableIcons
import core.data.prefs.LaunchableCustomization
import core.data.prefs.toLaunchableNames
import core.data.testing.createIconRepositoryMock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val RENAMED_FOLDER_ID: Long = 1L
private const val NEW_FOLDER_ID: Long = 2L
private const val RENAMED_FOLDER_KEY: String = "rail-folder:1"
private const val RENAMED_FOLDER_NAME: String = "Games"
private const val DEFAULT_FOLDER_NAME: String = "Folder"

class FolderResolutionTest {
    @Test
    fun `resolves customization names and keeps the default for a new Folder`() = runTest {
        val inputRecords: List<FolderRecord> = listOf(
            FolderRecord(id = RENAMED_FOLDER_ID),
            FolderRecord(id = NEW_FOLDER_ID)
        )
        val inputCustomizations: List<LaunchableCustomization> = listOf(
            LaunchableCustomization(key = RENAMED_FOLDER_KEY, name = RENAMED_FOLDER_NAME)
        )

        val actualFolders: List<Folder> = inputRecords.resolveFolders(
            names = inputCustomizations.toLaunchableNames(),
            icons = LaunchableIcons(emptyMap()),
            iconRepository = createIconRepositoryMock(inputIcon = Icon.FolderIcon(shade = 0))
        )

        assertEquals(listOf(RENAMED_FOLDER_NAME, DEFAULT_FOLDER_NAME), actualFolders.map { folder -> folder.name })
        assertEquals(listOf(RENAMED_FOLDER_ID, NEW_FOLDER_ID), actualFolders.map { folder -> folder.folderId })
        assertEquals(listOf(RENAMED_FOLDER_KEY, "rail-folder:$NEW_FOLDER_ID"), actualFolders.map { folder -> folder.key })
    }
}
