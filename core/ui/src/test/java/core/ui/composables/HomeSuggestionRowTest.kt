package core.ui.composables

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.ui.testing.createLauncherItemActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val ROW_WIDTH: Dp = 360.dp
private val ROW_HEIGHT: Dp = 80.dp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class HomeSuggestionRowTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val inputSuggestion: Launchable = createApp(name = "Suggestion", packageName = "com.example.suggestion")

    @Test
    fun `announces a Home suggestion as suggested`() {
        setRow(itemActions = createLauncherItemActions())
        val actualNode: SemanticsNode = composeRule.onNodeWithText("Suggestion").fetchSemanticsNode()
        assertEquals(listOf("Suggestion, Suggested"), actualNode.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `opens a Home suggestion on tap`() {
        val actualTapped: MutableList<Launchable> = mutableListOf()
        setRow(itemActions = createLauncherItemActions().copy(onItemTap = { launchable, _ -> actualTapped += launchable }))
        composeRule.onNodeWithText("Suggestion").performClick()
        assertEquals(listOf(inputSuggestion), actualTapped)
    }

    @Test
    fun `opens the Pop-up of a Home suggestion on swipe right`() {
        val actualOpened: MutableList<Launchable> = mutableListOf()
        setRow(itemActions = createLauncherItemActions().copy(onItemOpenPopup = { launchable, _ -> actualOpened += launchable }))
        composeRule.onNodeWithText("Suggestion").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(listOf(inputSuggestion), actualOpened)
    }

    private fun setRow(itemActions: LauncherItemActions) {
        composeRule.setContent {
            HomeSuggestionRowsTestContent(
                suggestions = listOf(inputSuggestion),
                itemActions = itemActions,
                isPopupSwipeEnabled = true,
                modifier = Modifier.size(width = ROW_WIDTH, height = ROW_HEIGHT)
            )
        }
    }
}
