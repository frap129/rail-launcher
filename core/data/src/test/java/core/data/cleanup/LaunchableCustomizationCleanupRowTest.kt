package core.data.cleanup

import core.data.icons.IIconImageStore
import core.data.icons.model.CustomIcon
import core.data.prefs.IPreferencesRepository
import core.data.testing.InMemoryDatabaseTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val STALE_KEY: String = "stale-key"
private const val STALE_IMAGE_FILE_NAME: String = "stale-image.png"

class LaunchableCustomizationCleanupRowTest : InMemoryDatabaseTest() {
    @Test
    fun `deletes a stale customization row and its image file`() = databaseTest {
        val inputImageStore: IIconImageStore = createImageStoreWithFiles(STALE_IMAGE_FILE_NAME)
        database.getLaunchableCustomizationDao().setIcon(key = STALE_KEY, icon = CustomIcon.Image(fileName = STALE_IMAGE_FILE_NAME))
        val repository: IPreferencesRepository = createPreferencesRepository(iconImageStore = inputImageStore)
        val row: IStaleKeyRow = LaunchableCustomizationCleanupRow(preferencesRepository = repository)

        row.delete(keys = setOf(STALE_KEY))

        assertTrue(customizations().isEmpty())
        assertFalse(inputImageStore.hasImage(fileName = STALE_IMAGE_FILE_NAME))
    }
}
