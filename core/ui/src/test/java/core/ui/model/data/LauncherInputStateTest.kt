package core.ui.model.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherInputStateTest {
    @Test
    fun `reports whether Tab can toggle the App List`() {
        val inputLauncherInputState: LauncherInputState = LauncherInputState()
        val actualInitial: Boolean = inputLauncherInputState.canToggleAppList.value
        assertFalse(actualInitial)
        inputLauncherInputState.setCanToggleAppList(canToggleAppList = true)
        val actualAvailable: Boolean = inputLauncherInputState.canToggleAppList.value
        assertTrue(actualAvailable)
        inputLauncherInputState.setCanToggleAppList(canToggleAppList = false)
        val actualUnavailable: Boolean = inputLauncherInputState.canToggleAppList.value
        assertFalse(actualUnavailable)
    }
}
