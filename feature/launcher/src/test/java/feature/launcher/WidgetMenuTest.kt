package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.icons.model.Icon
import core.data.prefs.ClockStyle
import core.data.testing.TEST_SDK
import core.data.widgets.AppWidgetId
import core.ui.R as CoreUiR
import core.ui.composables.RowTestContent
import core.ui.composables.SHEET_TITLE_ICON_TEST_TAG
import core.ui.composables.assertSheetTitleMatchesFirstRow
import core.ui.composables.assertSheetTitleMatchesRowContent
import core.ui.widgets.createHeaderModel
import core.ui.widgets.labelResource
import feature.launcher.widgets.WidgetMenu
import feature.launcher.widgets.WidgetMenuAction
import feature.launcher.widgets.WidgetMenuChoiceKey
import feature.launcher.widgets.WidgetMenuChoiceOption
import feature.launcher.widgets.WidgetMenuItem
import feature.launcher.widgets.WidgetMenuState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val WIDGET_LABEL: String = "Weather"
private const val MEDIA_APP_LABEL: String = "Music"
private val NEVER_KEY: WidgetMenuChoiceKey = WidgetMenuChoiceKey("NEVER")
private val ALWAYS_KEY: WidgetMenuChoiceKey = WidgetMenuChoiceKey("ALWAYS")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetMenuTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val actualToggles: MutableList<Pair<WidgetMenuAction, Boolean>> = mutableListOf()
    private val actualActions: MutableList<WidgetMenuAction> = mutableListOf()
    private val actualChoiceClicks: MutableList<WidgetMenuAction> = mutableListOf()
    private val actualSelections: MutableList<Pair<WidgetMenuAction, WidgetMenuChoiceKey>> = mutableListOf()
    private val actualClockStyles: MutableList<ClockStyle> = mutableListOf()

    @Test
    fun `paints the background menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = WidgetMenuState.EmptyAreaMenu(
                items = listOf(
                    WidgetMenuItem.Action(id = WidgetMenuAction.EditFavorites),
                    WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget)
                )
            ),
            labels = listOf(R.string.edit_favorites, CoreUiR.string.add_widget)
        )
    }

    @Test
    fun `paints the header menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = WidgetMenuState.HeaderMenu(items = inputHeaderItems(expanded = false)),
            labels = listOf(R.string.widget_clock, CoreUiR.string.battery, CoreUiR.string.add_widget)
        )
    }

    @Test
    fun `paints the expanded choice option rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = WidgetMenuState.HeaderMenu(items = inputHeaderItems(expanded = true)),
            labels = listOf(R.string.widget_clock, R.string.widget_date)
        )
    }

    @Test
    fun `paints the app widget menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = appWidgetState(
                actions = listOf(
                    WidgetMenuItem.Action(id = WidgetMenuAction.MoveWidgetArea),
                    WidgetMenuItem.Action(id = WidgetMenuAction.EditWidgets)
                )
            ),
            labels = listOf(CoreUiR.string.extend_under_rail, R.string.move_widget_area, R.string.edit_widgets)
        )
    }

    @Test
    fun `paints the media player menu rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = mediaPlayerState(),
            labels = listOf(R.string.media_player_settings)
        )
    }

    @Test
    fun `paints the clock style chooser rows with the sheet container colour`() {
        assertRowsPaintSheetColor(
            state = clockStyleState(),
            labels = listOf(ClockStyle.CLASSIC.labelResource(), ClockStyle.BOLD.labelResource())
        )
    }

    @Test
    fun `lines the clock style title up with the row content`() {
        setMenuContent(state = clockStyleState())

        assertSheetTitleMatchesRowContent(
            titleContent = composeRule.onNodeWithText(getString(CoreUiR.string.clock_style_title), useUnmergedTree = true),
            rowLeadingContent = composeRule.onNodeWithText(getString(ClockStyle.CLASSIC.labelResource()), useUnmergedTree = true)
        )
    }

    @Test
    fun `selects a clock style from its row`() {
        setMenuContent(state = clockStyleState())

        composeRule.onNodeWithText(getString(ClockStyle.BOLD.labelResource())).performClick()

        assertEquals(listOf(ClockStyle.BOLD), actualClockStyles)
    }

    @Test
    fun `lines the header title up with the row content`() {
        setMenuContent(state = WidgetMenuState.HeaderMenu(items = inputHeaderItems(expanded = false)))

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithText(getString(R.string.header_title), useUnmergedTree = true)
        )
    }

    @Test
    fun `lines the app widget title up with the row content`() {
        setMenuContent(state = appWidgetState())

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithTag(SHEET_TITLE_ICON_TEST_TAG, useUnmergedTree = true)
        )
    }

    @Test
    fun `lines the media player title up with the row content`() {
        setMenuContent(state = mediaPlayerState())

        composeRule.assertSheetTitleMatchesFirstRow(
            titleContent = composeRule.onNodeWithTag(SHEET_TITLE_ICON_TEST_TAG, useUnmergedTree = true)
        )
    }

    @Test
    fun `toggles from the whole row when the row has no action`() {
        setMenuContent(
            state = WidgetMenuState.HeaderMenu(
                items = listOf(WidgetMenuItem.Toggle(id = WidgetMenuAction.Clock, checked = false, enabled = true))
            )
        )

        composeRule.onNodeWithText(getString(R.string.widget_clock)).performClick()

        assertEquals(listOf(WidgetMenuAction.Clock to true), actualToggles)
        assertEquals(emptyList<WidgetMenuAction>(), actualActions)
    }

    @Test
    fun `opens the action from the row while the switch toggles when the row has an action`() {
        setMenuContent(
            state = WidgetMenuState.HeaderMenu(
                items = listOf(
                    WidgetMenuItem.Toggle(
                        id = WidgetMenuAction.MediaPlayer,
                        checked = false,
                        enabled = true,
                        rowOpensAction = true
                    )
                )
            )
        )

        composeRule.onNodeWithText(getString(CoreUiR.string.media_player)).performClick()

        assertEquals(listOf(WidgetMenuAction.MediaPlayer), actualActions)
        assertEquals(emptyList<Pair<WidgetMenuAction, Boolean>>(), actualToggles)

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(WidgetMenuAction.MediaPlayer to true), actualToggles)
        assertEquals(listOf(WidgetMenuAction.MediaPlayer), actualActions)
    }

    @Test
    fun `expands the choice inline and selects the tapped option`() {
        var expanded: Boolean by mutableStateOf(false)
        val inputChoice: WidgetMenuItem.Choice = WidgetMenuItem.Choice(
            id = WidgetMenuAction.Battery,
            valueLabel = CoreUiR.string.battery,
            options = inputBatteryOptions(),
            selectedKey = NEVER_KEY,
            expanded = false
        )
        composeRule.setContent {
            RowTestContent {
                MenuContent(
                    state = WidgetMenuState.HeaderMenu(items = listOf(inputChoice.copy(expanded = expanded))),
                    onChoiceClick = { action ->
                        actualChoiceClicks.add(action)
                        expanded = true
                    }
                )
            }
        }
        composeRule.onNodeWithText(getString(R.string.widget_date)).assertDoesNotExist()

        composeRule.onNodeWithText(getString(CoreUiR.string.battery)).performClick()

        composeRule.onNodeWithText(getString(R.string.widget_date)).assertIsDisplayed()
        assertEquals(listOf(WidgetMenuAction.Battery), actualChoiceClicks)

        composeRule.onNodeWithText(getString(R.string.widget_date)).performClick()

        assertEquals(listOf(WidgetMenuAction.Battery to ALWAYS_KEY), actualSelections)
    }

    private fun assertRowsPaintSheetColor(state: WidgetMenuState, labels: List<Int>) {
        composeRule.assertSheetRowsPaintSheetColor(content = { MenuContent(state = state) }, labels = labels)
    }

    private fun inputHeaderItems(expanded: Boolean): List<WidgetMenuItem> = listOf(
        WidgetMenuItem.Toggle(id = WidgetMenuAction.Clock, checked = true, enabled = true),
        WidgetMenuItem.Choice(
            id = WidgetMenuAction.Battery,
            valueLabel = CoreUiR.string.battery,
            options = inputBatteryOptions(),
            selectedKey = NEVER_KEY,
            expanded = expanded
        ),
        WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget)
    )

    private fun inputBatteryOptions(): List<WidgetMenuChoiceOption> = listOf(
        WidgetMenuChoiceOption(key = NEVER_KEY, label = R.string.widget_clock),
        WidgetMenuChoiceOption(key = ALWAYS_KEY, label = R.string.widget_date)
    )

    private fun clockStyleState(): WidgetMenuState = WidgetMenuState.ClockStyleChooser(
        selected = ClockStyle.CLASSIC,
        previews = listOf(
            createHeaderModel(id = 1L, timeText = "10:00", style = ClockStyle.CLASSIC),
            createHeaderModel(id = 2L, timeText = "10:00", style = ClockStyle.BOLD)
        )
    )

    private fun appWidgetState(
        actions: List<WidgetMenuItem.Action> = listOf(WidgetMenuItem.Action(id = WidgetMenuAction.MoveWidgetArea))
    ): WidgetMenuState = WidgetMenuState.AppWidgetMenu(
        widgetId = 1L,
        appWidgetId = AppWidgetId(2),
        icon = Icon.RailIcon(resId = CoreUiR.drawable.gallery_icon),
        label = WIDGET_LABEL,
        items = listOf(
            WidgetMenuItem.Toggle(
                id = WidgetMenuAction.ExtendUnderRail,
                checked = false,
                enabled = true,
                supportingText = CoreUiR.string.extend_under_rail_supporting_text
            )
        ) + actions
    )

    private fun mediaPlayerState(
        items: List<WidgetMenuItem> = listOf(WidgetMenuItem.Action(id = WidgetMenuAction.MediaPlayerSettings))
    ): WidgetMenuState = WidgetMenuState.MediaPlayerMenu(
        icon = Icon.RailIcon(resId = CoreUiR.drawable.gallery_icon),
        label = MEDIA_APP_LABEL,
        items = items
    )

    private fun setMenuContent(state: WidgetMenuState, onChoiceClick: (WidgetMenuAction) -> Unit = actualChoiceClicks::add) {
        composeRule.setContent {
            RowTestContent {
                MenuContent(state = state, onChoiceClick = onChoiceClick)
            }
        }
    }

    @Composable
    private fun MenuContent(state: WidgetMenuState, onChoiceClick: (WidgetMenuAction) -> Unit = actualChoiceClicks::add) {
        WidgetMenu(
            state = state,
            onToggle = { action, checked -> actualToggles.add(action to checked) },
            onAction = actualActions::add,
            onChoiceClick = onChoiceClick,
            onChoiceSelected = { action, key -> actualSelections.add(action to key) },
            onClockStyleSelected = actualClockStyles::add
        )
    }
}
