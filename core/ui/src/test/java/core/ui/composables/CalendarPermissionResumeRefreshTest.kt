package core.ui.composables

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import core.data.calendar.ICalendarPermissionGate
import core.data.testing.TEST_SDK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class CalendarPermissionResumeRefreshTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `refreshes the Header's calendar permission each time the launcher resumes`() {
        val mockCalendarPermissionGate: ICalendarPermissionGate = mockk(relaxed = true)
        val inputLifecycle: LifecycleFixture = createLifecycleFixture(state = Lifecycle.State.CREATED)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides inputLifecycle.owner) {
                CalendarPermissionResumeRefresh(calendarPermissionGate = mockCalendarPermissionGate)
            }
        }
        verify(exactly = 0) { mockCalendarPermissionGate.refreshPermission() }

        composeRule.runOnIdle { inputLifecycle.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        verify(exactly = 1) { mockCalendarPermissionGate.refreshPermission() }

        composeRule.runOnIdle { inputLifecycle.registry.currentState = Lifecycle.State.CREATED }
        composeRule.runOnIdle { inputLifecycle.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        verify(exactly = 2) { mockCalendarPermissionGate.refreshPermission() }
    }
}
