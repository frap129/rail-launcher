package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.assertActionLabelMatchesSwitchTitle
import core.ui.composables.assertActionRowMatchesSwitchRow
import core.ui.composables.assertPaintsTopEdge
import core.ui.composables.assertRowsPaintSheetColor
import core.ui.composables.assertSheetTitleMatchesFirstRow
import core.ui.composables.rowTestBackgroundColor
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val SHEET_ROW_LABELS: List<Int> = listOf(
    CoreUiR.string.search_web_suggestions,
    CoreUiR.string.search_contacts,
    CoreUiR.string.search_suggestions,
    CoreUiR.string.search_calculator_row,
    CoreUiR.string.search_swipe_up,
    R.string.search_settings
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchOptionsSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `paints every row with the sheet container colour`() {
        composeRule.assertRowsPaintSheetColor(
            content = { SheetContent() },
            labels = {
                SHEET_ROW_LABELS.map { label -> composeRule.onNodeWithText(getString(label)).performScrollTo() }
            }
        )
    }

    @Test
    fun `lets the contrasting background show through the title`() {
        setSheetContent()

        composeRule.onNodeWithText(getString(CoreUiR.string.search)).assertPaintsTopEdge(rowTestBackgroundColor)
    }

    @Test
    fun `lines the title up with the row content`() {
        setSheetContent()

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithText(getString(CoreUiR.string.search), useUnmergedTree = true)
        )
    }

    @Test
    fun `starts the Search settings label where the switch rows start their titles`() {
        setSheetContent()

        assertActionLabelMatchesSwitchTitle(
            actionLabel = composeRule.onNodeWithText(getString(R.string.search_settings), useUnmergedTree = true)
                .performScrollTo(),
            switchTitle = composeRule.onNodeWithText(getString(CoreUiR.string.search_suggestions), useUnmergedTree = true)
                .performScrollTo()
        )
    }

    @Test
    fun `gives the Search settings action the same horizontal bounds as the switch rows`() {
        setSheetContent()

        assertActionRowMatchesSwitchRow(
            actionRow = composeRule.onNodeWithText(getString(R.string.search_settings)).performScrollTo(),
            switchRow = composeRule.onNodeWithText(getString(CoreUiR.string.search_suggestions)).performScrollTo()
        )
    }

    private fun setSheetContent() {
        composeRule.setContent {
            RowTestContent {
                SheetContent()
            }
        }
    }

    @Composable
    private fun SheetContent() {
        SearchOptionsContent(
            options = inputSearchOptions,
            contactsEvents = emptyFlow(),
            actions = inputSearchOptionsActions,
            onDismiss = {}
        )
    }
}
