package core.ui.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import core.data.launchables.model.LaunchableGroup
import core.data.testing.TEST_SDK
import core.ui.GroupMark
import core.ui.composables.scrollrail.ScrollRail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class GroupMarkContentTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `draws the shield in the App List Private Space header`() {
        composeRule.setContent {
            LaunchableGroupHeader(group = LaunchableGroup.PrivateSpace.Locked)
        }

        assertPrivateSpaceShield()
    }

    @Test
    fun `draws the shield on the Rail at rest and in the scrub bubble`() {
        composeRule.setContent {
            ScrollRail(
                items = listOf(GroupMark.PrivateSpaceShield),
                onScroll = {},
                onScrollEnded = {}
            )
        }

        assertPrivateSpaceShield()
        composeRule.onNodeWithTag("scrollRail").performTouchInput { down(center) }
        composeRule.onAllNodesWithContentDescription("Private Space").assertCountEquals(2)
    }

    private fun assertPrivateSpaceShield() {
        composeRule.onNodeWithContentDescription("Private Space").assertExists()
        composeRule.onNodeWithText(LaunchableGroup.PrivateSpace.LABEL).assertDoesNotExist()
    }
}
