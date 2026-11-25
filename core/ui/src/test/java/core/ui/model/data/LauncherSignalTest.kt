package core.ui.model.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSignalTest {
    @Test
    fun `a focused HOME intent on Home keeps its focus`() {
        val inputSignal: LauncherSignal.Home = LauncherSignal.Home(shouldShowHome = true, wasFocused = true)
        val actualSignal: LauncherSignal.Home = inputSignal.narrowSearchToStartDestination(isStartDestination = true)
        assertEquals(inputSignal, actualSignal)
    }

    @Test
    fun `a focused HOME intent outside Home still shows Home without opening Search`() {
        val inputSignal: LauncherSignal.Home = LauncherSignal.Home(shouldShowHome = true, wasFocused = true)
        val actualSignal: LauncherSignal.Home = inputSignal.narrowSearchToStartDestination(isStartDestination = false)
        assertEquals(LauncherSignal.Home(shouldShowHome = true, wasFocused = false), actualSignal)
    }

    @Test
    fun `a HOME intent returning from another app stays a return outside Home`() {
        val inputSignal: LauncherSignal.Home = LauncherSignal.Home(shouldShowHome = false, wasFocused = false)
        val actualSignal: LauncherSignal.Home = inputSignal.narrowSearchToStartDestination(isStartDestination = false)
        assertEquals(inputSignal, actualSignal)
    }

    @Test
    fun `launcher signals keep their Pop-up card behavior`() {
        val inputExpectations: Map<LauncherSignal, Boolean> = mapOf(
            LauncherSignal.Home(shouldShowHome = true, wasFocused = true) to true,
            LauncherSignal.Home(shouldShowHome = false, wasFocused = false) to true,
            LauncherSignal.OpenSearch to true,
            LauncherSignal.OpenAgenda to true,
            LauncherSignal.OpenSettings to true,
            LauncherSignal.ToggleAppList to false,
            LauncherSignal.OpenKeyboardShortcutsHelp to true,
            LauncherSignal.OpenIndex(index = 8) to false,
            LauncherSignal.TypedCharacter(character = "a") to false
        )

        inputExpectations.forEach { (inputSignal, expectedClosesPopupCard) ->
            val actualClosesPopupCard: Boolean = inputSignal.closesPopupCard
            assertEquals(inputSignal.toString(), expectedClosesPopupCard, actualClosesPopupCard)
        }
    }
}
