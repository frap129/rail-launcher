package core.data.launchables.model

import core.data.folders.Folder
import core.data.testing.createRailLaunchable
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchableAccessibilityNameTest {
    @Test
    fun `uses the name for a Folder`() {
        val inputFolder: Folder = Folder(folderId = 1L, name = "Games")

        assertEquals("Games", inputFolder.accessibilityName)
    }

    @Test
    fun `uses the name for a Rail Launchable`() {
        val inputRailLaunchable: RailLaunchable = createRailLaunchable(name = "Rail Settings")

        assertEquals("Rail Settings", inputRailLaunchable.accessibilityName)
    }
}
