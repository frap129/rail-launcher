package feature.launcher.widgets

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide
import core.data.testing.TEST_SDK
import core.ui.layout.homeEdgePadding
import core.ui.widgets.createHeaderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class CappedWidgetAreaTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `shows a short Header inside the capped Widget Area`() {
        val inputUi: WidgetAreaUi = widgetAreaUi(
            listState = LazyListState(),
            places = listOf(headerPlace(id = PLACE_ID, timeText = HEADER_TIME_TEXT))
        )
        composeRule.setContent {
            CappedWidgetArea(ui = inputUi, maxHeight = MAX_HEIGHT, railSide = RailSide.DEFAULT)
        }

        val header = composeRule.onNodeWithContentDescription(HEADER_TIME_TEXT)
        header.assertIsDisplayed()
        val actualAreaBounds: DpRect = composeRule.onNodeWithTag(CAPPED_WIDGET_AREA_TEST_TAG).getBoundsInRoot()
        val actualHeaderBounds: DpRect = header.getBoundsInRoot()

        assertTrue(actualAreaBounds.bottom - actualAreaBounds.top < MAX_HEIGHT)
        assertEquals(homeEdgePadding.value, actualHeaderBounds.top.value - actualAreaBounds.top.value, POSITION_TOLERANCE_DP)
        assertEquals(actualAreaBounds.bottom.value, actualHeaderBounds.bottom.value, POSITION_TOLERANCE_DP)
    }

    @Test
    fun `keeps the Widget Area capped when content grows past the cap`() {
        val listState: LazyListState = LazyListState()
        var ui: WidgetAreaUi by mutableStateOf(
            widgetAreaUi(listState = listState, places = listOf(headerPlace(id = 1L, timeText = "1:00")))
        )
        composeRule.setContent {
            CappedWidgetArea(ui = ui, maxHeight = MAX_HEIGHT, railSide = RailSide.DEFAULT)
        }

        composeRule.runOnIdle {
            ui = widgetAreaUi(
                listState = listState,
                places = (1..HEADER_PLACE_COUNT).map { index -> headerPlace(id = index.toLong(), timeText = "$index:00") }
            )
        }
        composeRule.mainClock.advanceTimeBy(ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()
        val actualAreaBounds: DpRect = composeRule.onNodeWithTag(CAPPED_WIDGET_AREA_TEST_TAG).getBoundsInRoot()

        assertEquals(MAX_HEIGHT.value, (actualAreaBounds.bottom - actualAreaBounds.top).value, POSITION_TOLERANCE_DP)
        composeRule.onNodeWithContentDescription("1:00").assertIsDisplayed()
        composeRule.runOnIdle { listState.requestScrollToItem(index = HEADER_PLACE_COUNT - 1) }
        composeRule.onNodeWithContentDescription("$HEADER_PLACE_COUNT:00").assertIsDisplayed()
    }

    private fun widgetAreaUi(listState: LazyListState, places: List<WidgetPlaceModel>): WidgetAreaUi = WidgetAreaUi(
        state = WidgetAreaState(places = places, listState = listState),
        resize = null,
        editDraft = null,
        mediaPlayer = MediaPlayerUiState.Hidden,
        scrollToPlaceId = null,
        actions = widgetAreaActions()
    )

    private fun headerPlace(id: Long, timeText: String): WidgetPlaceModel = WidgetPlaceModel(
        id = id,
        heightDp = null,
        widgets = listOf(createHeaderModel(id = id, timeText = timeText))
    )

    private fun widgetAreaActions(): WidgetAreaActions = WidgetAreaActions(
        menuEnabled = true,
        onHomeHeight = {},
        onWidgetAreaWidth = {},
        onTapTarget = {},
        onHeaderLongPress = {},
        onHeaderHeight = {},
        onAppWidgetLongPress = {},
        onAppWidgetUnpause = {},
        onPageSettled = { _, _ -> },
        onEmptyAreaLongPress = {},
        onFinishResize = {},
        onDragResize = {},
        onResetResize = {},
        onMovePlace = { _, _ -> },
        onRemovePlace = {},
        onToggleExpand = {},
        onMovePage = { _, _, _ -> },
        onRemovePage = { _, _ -> },
        onSplitPage = { _, _, _ -> },
        onMergePlace = { _, _ -> },
        onMediaPlayerOpen = {},
        onMediaPlayerLongPress = {},
        onMediaCommand = {},
        onScrolledToPlace = {}
    )

    private companion object {
        const val PLACE_ID: Long = 1L
        const val HEADER_TIME_TEXT: String = "12:34"
        const val HEADER_PLACE_COUNT: Int = 10
        const val ANIMATION_SETTLE_MILLIS: Long = 1_000L
        const val POSITION_TOLERANCE_DP: Float = 1f
        val MAX_HEIGHT: Dp = 300.dp
    }
}
