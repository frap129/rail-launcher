package feature.launcher

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import core.data.testing.TEST_SDK
import core.ui.composables.LauncherItemActions
import core.ui.composables.getString
import core.ui.testing.createLauncherItemActions
import core.ui.widgets.WidgetAreaLayout
import core.ui.widgets.WidgetAreaState
import core.ui.widgets.WidgetPlaceModel
import core.ui.widgets.createHeaderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CAPPED_WIDGET_AREA_FRACTION: Float = 0.5f
private const val WIDGET_PLACE_COUNT: Int = 3
private const val WIDGET_PLACE_HEIGHT_DP: Int = 600
private const val WIDGET_AREA_LIST_START_INDEX: Int = 2
private const val SCROLL_SETTLE_MILLIS: Long = 1_000L
private val homeTestModifier: Modifier = Modifier.size(width = 360.dp, height = 200.dp)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class DefaultLauncherPromptTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `given the prompt is shown, when Home loads, then its message and labelled actions are visible`() {
        setHomeContent(isDefaultLauncherPromptShown = true)
        composeRule.onNodeWithText(getString(R.string.default_launcher_prompt_title)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.default_launcher_prompt_body)).assertIsDisplayed()
        showAction(getString(R.string.set_rail_as_home))
        showAction(getString(R.string.dismiss_default_launcher_prompt))
        showAction(getString(R.string.turn_off_default_launcher_prompt))
    }

    @Test
    fun `given a capped Widget Area, when Home loads, then the default launcher prompt is visible`() {
        setHomeContent(
            isDefaultLauncherPromptShown = true,
            layout = WidgetAreaLayout.Capped(fraction = CAPPED_WIDGET_AREA_FRACTION)
        )
        composeRule.onNodeWithText(getString(R.string.default_launcher_prompt_title)).assertIsDisplayed()
        showAction(getString(R.string.set_rail_as_home))
    }

    @Test
    fun `given the prompt is shown, when Set Rail as Home is tapped, then the Home role is requested`() {
        var actualRequests: Int = 0
        setHomeContent(
            isDefaultLauncherPromptShown = true,
            mockActions = createDefaultLauncherPromptActions(onRequestHomeRole = { actualRequests++ })
        )
        showAction(getString(R.string.set_rail_as_home)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualRequests) }
    }

    @Test
    fun `given the prompt is shown, when Dismiss for now is tapped, then the prompt is dismissed`() {
        var actualDismissals: Int = 0
        setHomeContent(
            isDefaultLauncherPromptShown = true,
            mockActions = createDefaultLauncherPromptActions(onDismiss = { actualDismissals++ })
        )
        showAction(getString(R.string.dismiss_default_launcher_prompt)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualDismissals) }
    }

    @Test
    fun `given the prompt is shown, when Turn off is tapped, then the prompt is turned off`() {
        var actualTurnOffs: Int = 0
        setHomeContent(
            isDefaultLauncherPromptShown = true,
            mockActions = createDefaultLauncherPromptActions(onTurnOff = { actualTurnOffs++ })
        )
        showAction(getString(R.string.turn_off_default_launcher_prompt)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualTurnOffs) }
    }

    @Test
    fun `given the prompt is visible, when a widget is added below it, then Home scrolls to that widget`() {
        val inputListState: LazyListState = LazyListState()
        val inputPlaces: List<WidgetPlaceModel> = (1..WIDGET_PLACE_COUNT).map { id ->
            WidgetPlaceModel(
                id = id.toLong(),
                heightDp = WIDGET_PLACE_HEIGHT_DP,
                widgets = listOf(
                    createHeaderModel(id = id.toLong(), timeText = "$id:00"),
                    createHeaderModel(id = id + WIDGET_PLACE_COUNT.toLong(), timeText = "${id + WIDGET_PLACE_COUNT}:00")
                )
            )
        }
        var inputWidgetAreaState: WidgetAreaState by mutableStateOf(
            WidgetAreaState(isLoaded = true, layout = WidgetAreaLayout.Uncapped, listState = inputListState)
        )
        var inputScrollToPlaceId: Long? by mutableStateOf(null)
        val mockItemActions: LauncherItemActions = createLauncherItemActions()
        var actualScrolls: Int = 0
        composeRule.setContent {
            LauncherHomeTestContent(
                modifier = homeTestModifier,
                home = createHomeState(
                    favorites = emptyList(),
                    lazyListState = inputListState,
                    isDefaultLauncherPromptShown = true
                ),
                itemActions = mockItemActions,
                isPopupSwipeEnabled = false,
                widgetAreaState = inputWidgetAreaState,
                scrollToPlaceId = inputScrollToPlaceId,
                onScrolledToPlace = { actualScrolls++ }
            )
        }
        composeRule.runOnIdle {
            inputWidgetAreaState = inputWidgetAreaState.copy(places = inputPlaces, hasWidgets = true)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(inputListState.layoutInfo.totalItemsCount > WIDGET_AREA_LIST_START_INDEX + inputPlaces.lastIndex)
        }
        composeRule.runOnIdle {
            inputScrollToPlaceId = inputPlaces.last().id
        }
        composeRule.mainClock.advanceTimeBy(SCROLL_SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertEquals(WIDGET_AREA_LIST_START_INDEX + inputPlaces.lastIndex, inputListState.firstVisibleItemIndex)
        assertEquals(1, actualScrolls)
    }

    @Test
    fun `given the prompt is hidden, when Home loads, then its message and actions are absent`() {
        setHomeContent(isDefaultLauncherPromptShown = false)
        composeRule.onNodeWithText(getString(R.string.default_launcher_prompt_title)).assertDoesNotExist()
        composeRule.onNodeWithText(getString(R.string.set_rail_as_home)).assertDoesNotExist()
        composeRule.onNodeWithText(getString(R.string.dismiss_default_launcher_prompt)).assertDoesNotExist()
        composeRule.onNodeWithText(getString(R.string.turn_off_default_launcher_prompt)).assertDoesNotExist()
    }

    private fun showAction(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()

    private fun setHomeContent(
        isDefaultLauncherPromptShown: Boolean,
        mockActions: DefaultLauncherPromptActions = createDefaultLauncherPromptActions(),
        layout: WidgetAreaLayout = WidgetAreaLayout.Uncapped
    ) {
        val mockItemActions: LauncherItemActions = createLauncherItemActions()
        composeRule.setContent {
            LauncherHomeTestContent(
                home = createHomeState(
                    favorites = emptyList(),
                    isDefaultLauncherPromptShown = isDefaultLauncherPromptShown
                ),
                itemActions = mockItemActions,
                isPopupSwipeEnabled = false,
                defaultLauncherPromptActions = mockActions,
                layout = layout
            )
        }
    }
}
