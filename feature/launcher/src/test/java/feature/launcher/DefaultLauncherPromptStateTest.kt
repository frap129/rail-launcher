package feature.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLauncherPromptStateTest {
    @Test
    fun `starts with the prompt not dismissed`() {
        val state: DefaultLauncherPromptState = DefaultLauncherPromptState()
        assertFalse(state.isDismissed.value)
    }

    @Test
    fun `keeps the prompt dismissed`() {
        val state: DefaultLauncherPromptState = DefaultLauncherPromptState()
        state.dismiss()
        assertTrue(state.isDismissed.value)
    }
}
