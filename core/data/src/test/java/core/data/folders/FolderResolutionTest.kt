package core.data.folders

import core.data.prefs.LaunchableCustomization
import core.data.prefs.toLaunchableNames
import org.junit.Assert.assertEquals
import org.junit.Test

private const val RENAMED_FOLDER_ID: Long = 1L
private const val NEW_FOLDER_ID: Long = 2L
private const val RENAMED_FOLDER_KEY: String = "rail-folder:1"
private const val RENAMED_FOLDER_NAME: String = "Games"
private const val DEFAULT_FOLDER_NAME: String = "Folder"

class FolderResolutionTest {
    @Test
    fun `resolves customization names and keeps the default for a new Folder`() {
        val inputRecords: List<FolderRecord> = listOf(
            FolderRecord(id = RENAMED_FOLDER_ID, showInAppList = true),
            FolderRecord(id = NEW_FOLDER_ID)
        )
        val inputCustomizations: List<LaunchableCustomization> = listOf(
            LaunchableCustomization(key = RENAMED_FOLDER_KEY, name = RENAMED_FOLDER_NAME)
        )

        val actualFolders: List<Folder> = inputRecords.resolveFolders(names = inputCustomizations.toLaunchableNames())

        assertEquals(listOf(RENAMED_FOLDER_NAME, DEFAULT_FOLDER_NAME), actualFolders.map { folder -> folder.name })
        assertEquals(listOf(RENAMED_FOLDER_ID, NEW_FOLDER_ID), actualFolders.map { folder -> folder.folderId })
        assertEquals(listOf(true, false), actualFolders.map { folder -> folder.showInAppList })
    }
}
