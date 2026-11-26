package core.ui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import core.data.icons.model.Icon
import core.data.prefs.HeaderTapSetting
import core.data.testing.APP_NAME
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.widgets.HeaderTapTargetPart
import core.ui.widgets.TapActionLaunchable
import core.ui.widgets.TapActionPartUi
import core.ui.widgets.TapActionPickerContent
import core.ui.widgets.TapActionPickerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val APP_KEY: String = "Maps"
private const val SETTINGS_ROW_TITLE: String = "Name"
private const val SWITCH_ROW_TITLE: String = "On Home"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowsTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val inputPartUi: TapActionPartUi = TapActionPartUi(
        selected = HeaderTapSetting.SystemDefault,
        icon = FontAwesomeIcons.Solid.Clock,
        onSelect = {}
    )
    private val inputPickerState: TapActionPickerState = TapActionPickerState(
        part = HeaderTapTargetPart.Time,
        isLoaded = true,
        launchables = listOf(
            TapActionLaunchable(
                setting = HeaderTapSetting.Launchable(key = APP_KEY),
                name = APP_NAME,
                icon = Icon.RailIcon(resId = TEST_RAIL_ICON_RES_ID)
            )
        )
    )

    @Test
    fun `paints every tap action picker row with the sheet container colour`() {
        composeRule.assertRowsPaintSheetColor(
            content = { TapActionRows() },
            labels = {
                listOf(
                    composeRule.onNodeWithText(getString(CoreUiR.string.tap_action_system_default)),
                    composeRule.onNodeWithText(getString(CoreUiR.string.tap_action_nothing)),
                    composeRule.onNodeWithText(getString(CoreUiR.string.open_agenda)),
                    composeRule.onNodeWithText(APP_NAME)
                )
            }
        )
    }

    @Test
    fun `leaves the search field on the background behind it`() {
        composeRule.setContent {
            RowTestContent {
                TapActionRows()
            }
        }

        composeRule.onNodeWithText(getString(CoreUiR.string.search), useUnmergedTree = true)
            .assertPaintsTopEdge(rowTestBackgroundColor)
    }

    @Test
    fun `keeps settings rows on the settings container colour outside sheets`() {
        composeRule.assertRowsPaintColor(
            expectedColor = { MaterialTheme.colorScheme.surface },
            content = {
                SettingsActionRow(title = SETTINGS_ROW_TITLE, value = "", onClick = {})
                SettingsSwitchRow(title = SWITCH_ROW_TITLE, checked = true, onCheckedChange = {})
                SettingsDestructiveRow(title = getString(CoreUiR.string.delete_folder), onClick = {})
            },
            labels = {
                listOf(
                    composeRule.onNodeWithText(SETTINGS_ROW_TITLE),
                    composeRule.onNodeWithText(SWITCH_ROW_TITLE),
                    composeRule.onNodeWithText(getString(CoreUiR.string.delete_folder))
                )
            }
        )
    }

    @Composable
    private fun TapActionRows() {
        TapActionPickerContent(
            state = inputPickerState,
            partUi = inputPartUi,
            onQueryChange = {}
        )
    }
}
