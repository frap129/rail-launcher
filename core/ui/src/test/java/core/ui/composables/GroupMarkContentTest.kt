package core.ui.composables

import androidx.compose.ui.Modifier
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
import core.ui.composables.scrollrail.RailScrubState
import core.ui.composables.scrollrail.SCROLL_RAIL_TEST_TAG
import core.ui.composables.scrollrail.ScrollRail
import core.ui.composables.scrollrail.railScrubInput
import core.ui.composables.scrollrail.rememberRailScrubState
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
            val items: List<GroupMark> = listOf(GroupMark.PrivateSpaceShield)
            val state: RailScrubState = rememberRailScrubState(
                items = items,
                onScroll = {},
                onScrollEnded = {}
            )
            ScrollRail(
                state = state,
                items = items,
                modifier = Modifier.railScrubInput(state = state)
            )
        }

        assertPrivateSpaceShield()
        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performTouchInput { down(center) }
        composeRule.onAllNodesWithContentDescription("Private Space").assertCountEquals(2)
    }

    private fun assertPrivateSpaceShield() {
        composeRule.onNodeWithContentDescription("Private Space").assertExists()
        composeRule.onNodeWithText(LaunchableGroup.PrivateSpace.LABEL).assertDoesNotExist()
    }
}
