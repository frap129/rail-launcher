package feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import core.data.folders.Folder
import core.data.icons.model.IconPack
import core.data.prefs.AppListOrder
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.SHEET_TITLE_ICON_TEST_TAG
import core.ui.composables.assertSheetRowsPaintSheetColor
import core.ui.composables.assertSheetTitleMatchesRowContent
import core.ui.composables.getString
import core.util.RECENT_INSTALL_WINDOW_DAYS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FOLDER_NAME: String = "Work"

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
    fun `shows the App List order choices and reports the chosen order`() {
        val expectedOrder: AppListOrder = AppListOrder.ALPHABETICAL
        var actualOrder: AppListOrder? = null
        setAppearanceContent(
            appListOrder = AppListOrder.MOST_USED,
            onAppListOrderSelected = { order -> actualOrder = order }
        )

        composeRule.onNodeWithText(getString(R.string.app_list_order_alphabetical)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.app_list_order_most_used)).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText(getString(R.string.app_list_order_recently_used)).assertIsDisplayed()

        composeRule.onNodeWithText(getString(R.string.app_list_order_alphabetical)).performClick()

        composeRule.runOnIdle { assertEquals(expectedOrder, actualOrder) }
    }

    @Test
    fun `hides Rank Folders by use while the App List order is Alphabetical`() {
        setAppearanceContent(appListOrder = AppListOrder.ALPHABETICAL)

        composeRule.onNodeWithText(getString(R.string.rank_folders_by_use)).assertDoesNotExist()
    }

    @Test
    fun `shows Rank Folders by use for a usage mode and reports its change`() {
        assertSwitchRow(
            title = getString(R.string.rank_folders_by_use),
            checked = true,
            setContent = { onCheckedChange ->
                setAppearanceContent(
                    appListOrder = AppListOrder.RECENTLY_USED,
                    rankFoldersByUse = true,
                    onRankFoldersByUseChange = onCheckedChange
                )
            }
        )
    }

    @Test
    fun `shows Show Recently installed with its derived window and reports its change`() {
        assertSwitchRow(
            title = getString(R.string.show_recently_installed),
            summary = getString(R.string.show_recently_installed_summary, RECENT_INSTALL_WINDOW_DAYS),
            checked = false,
            setContent = { onCheckedChange ->
                setAppearanceContent(showRecentlyInstalled = false, onShowRecentlyInstalledChange = onCheckedChange)
            }
        )
    }

    @Test
    fun `shows Rotate Home off by default on a phone`() {
        assertSwitchRow(
            title = getString(R.string.rotate_home),
            checked = false,
            setContent = { onCheckedChange ->
                setAppearanceContent(
                    smallestScreenWidthDp = 411,
                    onRotateHomeChange = onCheckedChange
                )
            }
        )
    }

    @Test
    fun `shows Rotate Home on by default at 600dp`() {
        assertSwitchRow(
            title = getString(R.string.rotate_home),
            checked = true,
            setContent = { onCheckedChange ->
                setAppearanceContent(
                    smallestScreenWidthDp = 600,
                    onRotateHomeChange = onCheckedChange
                )
            }
        )
    }

    @Test
    fun `shows a stored Rotate Home off choice on a large screen`() {
        assertSwitchRow(
            title = getString(R.string.rotate_home),
            checked = false,
            setContent = { onCheckedChange ->
                setAppearanceContent(
                    rotateHome = false,
                    smallestScreenWidthDp = 600,
                    onRotateHomeChange = onCheckedChange
                )
            }
        )
    }

    @Test
    fun `shows Rail haptics with its summary and reports its change`() {
        assertSwitchRow(
            title = getString(R.string.rail_haptics),
            summary = getString(R.string.rail_haptics_summary),
            checked = true,
            setContent = { onCheckedChange ->
                setAppearanceContent(railHaptics = true, onRailHapticsChange = onCheckedChange)
            }
        )
    }

    @Test
    fun `shows Show Rail with its summary and reports its change`() {
        assertSwitchRow(
            title = getString(R.string.show_rail),
            summary = getString(R.string.show_rail_summary),
            checked = true,
            setContent = { onCheckedChange ->
                setAppearanceContent(showRail = true, onShowRailChange = onCheckedChange)
            }
        )
    }

    @Test
    fun `shows Suggest often-used Apps with its summary and reports its change`() {
        assertSwitchRow(
            title = getString(R.string.suggest_often_used_apps),
            summary = getString(R.string.suggest_often_used_apps_summary),
            checked = true,
            setContent = { onCheckedChange ->
                setAppearanceContent(suggestOftenUsedApps = true, onSuggestOftenUsedAppsChange = onCheckedChange)
            }
        )
    }

    @Test
    fun `resets custom icons only after the user confirms`() {
        var actualResets = 0
        setAppearanceContent(onResetCustomIcons = { actualResets++ })

        assertResetRunsOnlyAfterConfirming(title = getString(R.string.reset_custom_icons), actualResets = { actualResets })
    }

    @Test
    fun `shows Reset stopped suggestions and resets only after the user confirms when a suggestion is stopped`() {
        var actualResets = 0
        setAppearanceContent(hasStoppedSuggestions = true, onResetStoppedSuggestions = { actualResets++ })

        assertResetRunsOnlyAfterConfirming(title = getString(R.string.reset_stopped_suggestions), actualResets = { actualResets })
    }

    @Test
    fun `hides Reset stopped suggestions when nothing is stopped`() {
        setAppearanceContent(hasStoppedSuggestions = false)

        composeRule.onNodeWithText(getString(R.string.reset_stopped_suggestions)).assertDoesNotExist()
    }

    /**
     * Asserts the destructive row [title] resets nothing when dismissed and resets once when confirmed,
     * reading resets with [actualResets].
     */
    private fun assertResetRunsOnlyAfterConfirming(title: String, actualResets: () -> Int) {
        val confirmButton: SemanticsMatcher = hasText(title) and hasAnyAncestor(isDialog())
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))

        composeRule.onNodeWithText(title).performClick()
        composeRule.onNodeWithText(getString(CoreUiR.string.cancel)).performClick()
        composeRule.runOnIdle { assertEquals(0, actualResets()) }
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNode(confirmButton).performClick()

        composeRule.runOnIdle { assertEquals(1, actualResets()) }
        composeRule.onNode(confirmButton).assertDoesNotExist()
    }

    /** Asserts the switch row [title] shows [summary] and reports the opposite of [checked] when clicked. */
    private fun assertSwitchRow(
        title: String,
        summary: String? = null,
        checked: Boolean,
        setContent: (onCheckedChange: (Boolean) -> Unit) -> Unit
    ) {
        var actualEnabled: Boolean? = null
        setContent { enabled -> actualEnabled = enabled }

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).assertIsDisplayed()
        summary?.let { text -> composeRule.onNodeWithText(text).assertIsDisplayed() }
        composeRule.onNodeWithText(title).performClick()

        composeRule.runOnIdle { assertEquals(!checked, actualEnabled) }
    }

    private fun setAppearanceContent(
        appListOrder: AppListOrder = AppListOrder.RECENTLY_USED,
        rankFoldersByUse: Boolean = true,
        showRecentlyInstalled: Boolean = false,
        railHaptics: Boolean = true,
        showRail: Boolean = true,
        suggestOftenUsedApps: Boolean = true,
        rotateHome: Boolean? = null,
        smallestScreenWidthDp: Int = 411,
        hasStoppedSuggestions: Boolean = false,
        onAppListOrderSelected: (AppListOrder) -> Unit = {},
        onRankFoldersByUseChange: (Boolean) -> Unit = {},
        onShowRecentlyInstalledChange: (Boolean) -> Unit = {},
        onRailHapticsChange: (Boolean) -> Unit = {},
        onShowRailChange: (Boolean) -> Unit = {},
        onSuggestOftenUsedAppsChange: (Boolean) -> Unit = {},
        onRotateHomeChange: (Boolean) -> Unit = {},
        onResetCustomIcons: () -> Unit = {},
        onResetStoppedSuggestions: () -> Unit = {}
    ) {
        composeRule.setContent {
            SettingsAppearanceScreen(
                iconPacks = setOf(IconPack.SystemIconPack),
                selectedIconPack = IconPack.SystemIconPack,
                railSide = RailSide.DEFAULT,
                scrubEdges = ScrubEdges.DEFAULT,
                appListOrder = appListOrder,
                rankFoldersByUse = rankFoldersByUse,
                showRecentlyInstalled = showRecentlyInstalled,
                railHaptics = railHaptics,
                showRail = showRail,
                suggestOftenUsedApps = suggestOftenUsedApps,
                rotateHome = rotateHome,
                smallestScreenWidthDp = smallestScreenWidthDp,
                hasStoppedSuggestions = hasStoppedSuggestions,
                actions = SettingsAppearanceActions(
                    onIconPackSelected = {},
                    onResetCustomIcons = onResetCustomIcons,
                    onAppListOrderSelected = onAppListOrderSelected,
                    onRankFoldersByUseChange = onRankFoldersByUseChange,
                    onShowRecentlyInstalledChange = onShowRecentlyInstalledChange,
                    onRailHapticsChange = onRailHapticsChange,
                    onShowRailChange = onShowRailChange,
                    onSuggestOftenUsedAppsChange = onSuggestOftenUsedAppsChange,
                    onRotateHomeChange = onRotateHomeChange,
                    onResetStoppedSuggestions = onResetStoppedSuggestions,
                    onRailSideSelected = {},
                    onScrubEdgesSelected = {}
                )
            )
        }
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
}
