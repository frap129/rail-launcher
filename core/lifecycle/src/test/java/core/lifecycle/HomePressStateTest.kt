package core.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePressStateTest {
    @Test
    fun `a Home press while resumed and focused on Home counts as focused`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        assertTrue(inputState.wasFocusedAtHomeIntent())
    }

    @Test
    fun `a Home press delivered while resumed and focused without a pause counts as focused`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        val actualFocused: Boolean = inputState.wasFocusedAtHomeIntent()
        assertTrue(actualFocused)
    }

    @Test
    fun `repeated Home presses stay focused when window focus never changes`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        inputState.onResume()
        inputState.onPause()
        assertTrue(inputState.wasFocusedAtHomeIntent())
    }

    @Test
    fun `a Home press delivered while resumed without a pause shows Home`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        val actualShouldShowHome: Boolean = inputState.shouldShowHomeAtHomeIntent()
        assertTrue(actualShouldShowHome)
    }

    @Test
    fun `a Home press without an intervening stop shows Home when focus is lost before pause`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onWindowFocusChanged(hasFocus = false)
        inputState.onPause()
        val actualShouldShowHome: Boolean = inputState.shouldShowHomeAtHomeIntent()
        assertTrue(actualShouldShowHome)
    }

    @Test
    fun `a Home press returning from another app does not count as focused`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        inputState.onStop()
        assertFalse(inputState.wasFocusedAtHomeIntent())
    }

    @Test
    fun `a Home press after the activity stopped keeps the current screen`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        inputState.onStop()
        val actualShouldShowHome: Boolean = inputState.shouldShowHomeAtHomeIntent()
        assertFalse(actualShouldShowHome)
    }

    @Test
    fun `a Home press returning from Recents does not count as focused`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        inputState.onWindowFocusChanged(hasFocus = false)
        inputState.onStop()
        assertFalse(inputState.wasFocusedAtHomeIntent())
    }

    @Test
    fun `a Home press after Rail launched another app keeps the current screen before the stop`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onUserLeaveHint()
        inputState.onPause()
        inputState.onWindowFocusChanged(hasFocus = false)
        val actualShouldShowHome: Boolean = inputState.shouldShowHomeAtHomeIntent()
        assertFalse(actualShouldShowHome)
    }

    @Test
    fun `a Home press after returning to Rail from a launched app shows Home`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onUserLeaveHint()
        inputState.onPause()
        inputState.onResume()
        val actualShouldShowHome: Boolean = inputState.shouldShowHomeAtHomeIntent()
        assertTrue(actualShouldShowHome)
    }

    @Test
    fun `a Home press after a pause Rail did not leave for shows Home without counting as focused`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        inputState.onWindowFocusChanged(hasFocus = false)
        assertTrue(inputState.shouldShowHomeAtHomeIntent())
        assertFalse(inputState.wasFocusedAtHomeIntent())
    }

    @Test
    fun `a Home press before the first resume neither counts as focused nor shows Home`() {
        val inputState: HomePressState = HomePressState()
        assertFalse(inputState.wasFocusedAtHomeIntent())
        assertFalse(inputState.shouldShowHomeAtHomeIntent())
    }

    @Test
    fun `a Home press after the activity resumes again shows Home without counting as focused`() {
        val inputState: HomePressState = HomePressState()
        inputState.onResume()
        inputState.onWindowFocusChanged(hasFocus = true)
        inputState.onPause()
        inputState.onStop()
        inputState.onResume()
        assertTrue(inputState.shouldShowHomeAtHomeIntent())
        assertFalse(inputState.wasFocusedAtHomeIntent())
    }
}
