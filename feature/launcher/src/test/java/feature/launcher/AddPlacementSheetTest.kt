package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.assertSheetTitleMatchesFirstRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddPlacementSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private var actualBelowCount: Int = 0
    private var actualIntoStackCount: Int = 0

    @Test
    fun `paints the placement rows with the sheet container colour`() {
        composeRule.assertSheetRowsPaintSheetColor(
            content = { PlacementContent(canAddToStack = true) },
            labels = listOf(R.string.add_below, CoreUiR.string.add_next_to)
        )
    }

    @Test
    fun `shows the supporting text of each placement row`() {
        setPlacementContent(canAddToStack = true)

        composeRule.onNodeWithText(getString(R.string.add_below_supporting_text)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.add_next_to_supporting_text)).assertIsDisplayed()
    }

    @Test
    fun `lines the Add widget title up with the placement rows`() {
        setPlacementContent(canAddToStack = true)

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithText(getString(CoreUiR.string.add_widget), useUnmergedTree = true)
        )
    }

    @Test
    fun `hides the Add next to row when the widget cannot join a stack`() {
        setPlacementContent(canAddToStack = false)

        composeRule.onNodeWithText(getString(CoreUiR.string.add_next_to)).assertDoesNotExist()
    }

    @Test
    fun `adds below from its placement row`() {
        setPlacementContent(canAddToStack = true)

        composeRule.onNodeWithText(getString(R.string.add_below)).performClick()

        assertEquals(1, actualBelowCount)
        assertEquals(0, actualIntoStackCount)
    }

    @Test
    fun `adds into the stack from its placement row`() {
        setPlacementContent(canAddToStack = true)

        composeRule.onNodeWithText(getString(CoreUiR.string.add_next_to)).performClick()

        assertEquals(0, actualBelowCount)
        assertEquals(1, actualIntoStackCount)
    }

    private fun setPlacementContent(canAddToStack: Boolean) {
        composeRule.setContent {
            RowTestContent {
                PlacementContent(canAddToStack = canAddToStack)
            }
        }
    }

    @Composable
    private fun PlacementContent(canAddToStack: Boolean) {
        AddPlacementContent(
            canAddToStack = canAddToStack,
            onBelow = { actualBelowCount++ },
            onIntoStack = { actualIntoStackCount++ }
        )
    }
}
