package core.ui.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.testing.TEST_SDK
import core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class WarningRowTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `exposes one clickable action per warning row`() {
        var actualClickCount: Int = 0
        composeRule.setContent {
            WarningRow(
                title = getString(R.string.calendar_permission_denied_title),
                supportingText = getString(R.string.calendar_permission_needed),
                actionLabel = getString(R.string.grant),
                onActionClick = { actualClickCount++ }
            )
        }

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithText(getString(R.string.grant)).performClick()

        assertEquals(1, actualClickCount)
    }
}
