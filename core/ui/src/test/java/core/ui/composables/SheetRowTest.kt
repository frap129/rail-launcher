package core.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.Search
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val ACTION_LABEL: String = "Search settings"
private const val SWITCH_TITLE: String = "Search suggestions"
private const val SETTINGS_TITLE: String = "Settings row"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetRowTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `paints an action row with the sheet container colour`() {
        composeRule.assertRowsPaintSheetColor(
            content = { SheetActionRow(imageVector = FontAwesomeIcons.Solid.Cog, label = ACTION_LABEL, onClick = {}) },
            labels = { listOf(composeRule.onNodeWithText(ACTION_LABEL)) }
        )
    }

    @Test
    fun `paints a sheet switch row with the sheet container colour`() {
        composeRule.assertRowsPaintSheetColor(
            content = {
                SettingsSwitchRow(
                    title = SWITCH_TITLE,
                    checked = true,
                    onCheckedChange = {},
                    leadingIcon = FontAwesomeIcons.Solid.Search,
                    containerColor = sheetContainerColor
                )
            },
            labels = { listOf(composeRule.onNodeWithText(SWITCH_TITLE)) }
        )
    }

    @Test
    fun `keeps a settings switch row on its own container colour`() {
        val expectedSettingsColor: MutableState<Color?> = mutableStateOf(null)
        composeRule.setContent {
            RowTestContent {
                expectedSettingsColor.value = MaterialTheme.colorScheme.surface
                SettingsSwitchRow(title = SETTINGS_TITLE, checked = true, onCheckedChange = {})
            }
        }

        composeRule.onNodeWithText(SETTINGS_TITLE).assertPaintsTopEdge(requireNotNull(expectedSettingsColor.value))
    }

    @Test
    fun `reads the sheet container colour from the theme`() {
        val actualSheetContainerColor: MutableState<Color?> = mutableStateOf(null)
        val expectedSheetColor: MutableState<Color?> = mutableStateOf(null)
        composeRule.setContent {
            RowTestContent {
                actualSheetContainerColor.value = sheetContainerColor
                expectedSheetColor.value = MaterialTheme.colorScheme.surfaceContainerLow
            }
        }

        assertEquals(requireNotNull(expectedSheetColor.value), actualSheetContainerColor.value)
    }

    @Test
    fun `starts an action row's label where a sheet switch row starts its title`() {
        composeRule.setContent {
            RowTestContent {
                SheetActionRow(imageVector = FontAwesomeIcons.Solid.Cog, label = ACTION_LABEL, onClick = {})
                SettingsSwitchRow(
                    title = SWITCH_TITLE,
                    checked = true,
                    onCheckedChange = {},
                    leadingIcon = FontAwesomeIcons.Solid.Search,
                    containerColor = sheetContainerColor
                )
            }
        }

        assertActionLabelMatchesSwitchTitle(
            actionLabel = composeRule.onNodeWithText(ACTION_LABEL, useUnmergedTree = true),
            switchTitle = composeRule.onNodeWithText(SWITCH_TITLE, useUnmergedTree = true)
        )
    }

    @Test
    fun `gives a one-line action row and sheet switch row the same height and bounds`() {
        composeRule.setContent {
            RowTestContent {
                SheetActionRow(imageVector = FontAwesomeIcons.Solid.Cog, label = ACTION_LABEL, onClick = {})
                SettingsSwitchRow(
                    title = SWITCH_TITLE,
                    checked = true,
                    onCheckedChange = {},
                    leadingIcon = FontAwesomeIcons.Solid.Search,
                    containerColor = sheetContainerColor
                )
            }
        }

        assertActionRowMatchesSwitchRow(
            actionRow = composeRule.onNodeWithText(ACTION_LABEL),
            switchRow = composeRule.onNodeWithText(SWITCH_TITLE)
        )
        val actualActionRow: DpRect = composeRule.onNodeWithText(ACTION_LABEL).getUnclippedBoundsInRoot()
        val actualSwitchRow: DpRect = composeRule.onNodeWithText(SWITCH_TITLE).getUnclippedBoundsInRoot()
        assertEquals(actualSwitchRow.bottom - actualSwitchRow.top, actualActionRow.bottom - actualActionRow.top)
    }
}
