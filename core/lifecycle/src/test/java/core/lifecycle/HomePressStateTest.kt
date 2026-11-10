package core.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePressStateTest {
    @Test
    fun `a Home press while resumed and focused on Home counts as resumed`() {
        val state: HomePressState = HomePressState()
        state.onResume()
        state.onWindowFocusChanged(hasFocus = true)

        state.onPause()

        assertTrue(state.wasResumedAtHomeIntent())
    }

    @Test
    fun `a Home press returning from another app does not count as resumed`() {
        val state: HomePressState = HomePressState()
        state.onResume()
        state.onWindowFocusChanged(hasFocus = true)
        state.onPause()

        state.onStop()

        assertFalse(state.wasResumedAtHomeIntent())
    }

    @Test
    fun `a Home press returning from Recents does not count as resumed`() {
        val state: HomePressState = HomePressState()
        state.onResume()
        state.onWindowFocusChanged(hasFocus = true)
        state.onPause()
        state.onWindowFocusChanged(hasFocus = false)

        state.onStop()

        assertFalse(state.wasResumedAtHomeIntent())
    }

    @Test
    fun `a Home press dismissing a translucent activity does not count as resumed`() {
        val state: HomePressState = HomePressState()
        state.onResume()
        state.onWindowFocusChanged(hasFocus = true)
        state.onPause()

        state.onWindowFocusChanged(hasFocus = false)

        assertFalse(state.wasResumedAtHomeIntent())
    }

    @Test
    fun `a Home press before the first resume does not count as resumed`() {
        val state: HomePressState = HomePressState()

        assertFalse(state.wasResumedAtHomeIntent())
    }

    @Test
    fun `a Home press after the activity resumes again does not count as resumed`() {
        val state: HomePressState = HomePressState()
        state.onResume()
        state.onWindowFocusChanged(hasFocus = true)
        state.onPause()

        state.onResume()

        assertFalse(state.wasResumedAtHomeIntent())
    }
}
