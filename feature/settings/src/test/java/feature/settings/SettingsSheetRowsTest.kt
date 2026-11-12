package feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Clock
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.prefs.HeaderTapSetting
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.SHEET_TITLE_ICON_TEST_TAG
import core.ui.composables.SettingsSwitchRow
import core.ui.composables.assertPaintsTopEdge
import core.ui.composables.assertRowsPaintColor
import core.ui.composables.assertRowsPaintSheetColor
import core.ui.composables.assertSheetRowsPaintSheetColor
import core.ui.composables.assertSheetTitleMatchesRowContent
import core.ui.composables.getString
import core.ui.composables.rowTestBackgroundColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FOLDER_NAME: String = "Work"
private const val APP_NAME: String = "Maps"
private const val APP_KEY: String = "Maps"

private val FOLDER_ACTIONS_LABELS: List<Int> = listOf(
    R.string.name,
    CoreUiR.string.change_icon,
    R.string.edit_contents,
    R.string.on_home,
    CoreUiR.string.delete_folder
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSheetRowsTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val inputFolder: PopupSettingsFolder = PopupSettingsFolder(
        folder = Folder(folderId = 1L, name = FOLDER_NAME),
        itemCount = 2,
        isOnHome = true
    )
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
                icon = Icon.RailIcon(resId = 0)
            )
        )
    )

    @Test
    fun `paints the Folder actions rows with the sheet container colour`() {
        composeRule.assertSheetRowsPaintSheetColor(
            content = { FolderActionsRows() },
            labels = FOLDER_ACTIONS_LABELS
        )
    }

    @Test
    fun `lines the Folder actions title up with the rows`() {
        setFolderActionsContent()

        assertSheetTitleMatchesRowContent(
            titleContent = composeRule.onNodeWithTag(SHEET_TITLE_ICON_TEST_TAG, useUnmergedTree = true),
            rowLeadingContent = composeRule.onNodeWithText(getString(R.string.name), useUnmergedTree = true)
        )
    }

    @Test
    fun `paints every tap action picker row with the sheet container colour`() {
        composeRule.assertRowsPaintSheetColor(
            content = { TapActionRows() },
            labels = {
                listOf(
                    composeRule.onNodeWithText(getString(R.string.tap_action_system_default)),
                    composeRule.onNodeWithText(getString(R.string.tap_action_nothing)),
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
                SettingsActionRow(title = getString(R.string.name), value = "", onClick = {})
                SettingsSwitchRow(title = getString(R.string.on_home), checked = true, onCheckedChange = {})
                SettingsDestructiveRow(title = getString(CoreUiR.string.delete_folder), onClick = {})
            },
            labels = {
                listOf(
                    composeRule.onNodeWithText(getString(R.string.name)),
                    composeRule.onNodeWithText(getString(R.string.on_home)),
                    composeRule.onNodeWithText(getString(CoreUiR.string.delete_folder))
                )
            }
        )
    }

    private fun setFolderActionsContent() {
        composeRule.setContent {
            RowTestContent {
                FolderActionsRows()
            }
        }
    }

    @Composable
    private fun FolderActionsRows() {
        FolderActionsContent(
            row = inputFolder,
            onRenameClick = {},
            onIconChange = {},
            onEditContents = {},
            onOnHomeChange = {},
            onDeleteClick = {}
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
