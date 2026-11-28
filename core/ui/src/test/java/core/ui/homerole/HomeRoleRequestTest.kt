package core.ui.homerole

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import core.data.homerole.IHomeRoleSource
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.failActivityStarts
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class HomeRoleRequestTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val mockContext: Context = mockk(relaxed = true)
    private val mockHomeRoleSource: IHomeRoleSource = mockk()
    private lateinit var actualRequestHomeRole: () -> Unit

    @Test
    fun `given a Home role request Intent, when the shared action runs, then it starts that Intent`() {
        val inputIntent: Intent = Intent(Intent.ACTION_VIEW)
        val actualStartedIntents: MutableList<Intent> = mutableListOf()
        every { mockHomeRoleSource.createHomeRoleRequestIntent() } returns inputIntent
        mockContext.failActivityStarts(shouldFail = { false }, onStart = { intent -> actualStartedIntents.add(intent) })
        setHomeRoleRequest()

        composeRule.runOnIdle { actualRequestHomeRole() }

        assertSame(inputIntent, actualStartedIntents.single())
        verify(exactly = 1) { mockHomeRoleSource.createHomeRoleRequestIntent() }
    }

    @Test
    fun `given no Home role request Intent, when the shared action runs, then it logs and starts nothing`() {
        val actualLogRecorder: TimberLogRecorder = TimberLogRecorder()
        every { mockHomeRoleSource.createHomeRoleRequestIntent() } returns null
        setHomeRoleRequest()

        actualLogRecorder.record {
            composeRule.runOnIdle { actualRequestHomeRole() }
        }

        actualLogRecorder.assertLogged("The system offers no Home role request Intent")
        verify(exactly = 0) { mockContext.startActivity(any()) }
    }

    private fun setHomeRoleRequest() {
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides mockContext) {
                actualRequestHomeRole = rememberHomeRoleRequest(mockHomeRoleSource)
            }
        }
    }
}
