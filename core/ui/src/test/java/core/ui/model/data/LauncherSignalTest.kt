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
}
