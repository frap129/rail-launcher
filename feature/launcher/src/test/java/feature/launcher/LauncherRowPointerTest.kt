package feature.launcher

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import core.data.folders.Folder
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.testing.TEST_SDK
import core.ui.composables.ROW_HOVER_BACKGROUND_ALPHA
import core.ui.composables.RowTestContent
import core.ui.composables.assertPaintsTopEdge
import core.ui.composables.captureThemeColor
import core.ui.composables.rowTestBackgroundColor
import core.ui.composables.topEdgeColor
import core.ui.util.consumeTouches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SCROLLABLE_APP_COUNT: Int = 40
private const val MOUSE_WHEEL_DELTA: Float = 500f

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherRowPointerTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `a secondary click opens the long-press menu on a Home row`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setHomeSuggestionRows(itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses))

        composeRule.onNodeWithText("Alpha").performMouseInput { rightClick() }

        assertEquals(listOf(testFirstApp to MenuSuggestion.Home), actualLongPresses)
    }

    @Test
    fun `a secondary click does not open the row`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeSuggestionRows(
            itemActions = createLauncherItemActions().copy(onItemTap = { item, _ -> actualOpens += item })
        )

        composeRule.onNodeWithText("Alpha").performMouseInput { rightClick() }

        assertTrue(actualOpens.isEmpty())
    }

    @Test
    fun `a secondary click opens the long-press menu on an App List row`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setAppListRows(itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses))

        composeRule.onAllNodesWithText("Beta").onFirst().performMouseInput { rightClick() }

        assertEquals(listOf(testSecondApp to MenuSuggestion.None), actualLongPresses)
    }

    @Test
    fun `a secondary click opens the long-press menu on a Search row`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = testFirstApp,
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses),
            popupRow = PopupRow.Search
        )

        row().performMouseInput { rightClick() }

        assertEquals(listOf(testFirstApp to MenuSuggestion.None), actualLongPresses)
    }

    @Test
    fun `a secondary click opens the long-press menu on a Favourite row`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp),
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses)
        )

        composeRule.onAllNodesWithText("Alpha").onFirst().performMouseInput { rightClick() }

        assertEquals(listOf(testFirstApp to MenuSuggestion.None), actualLongPresses)
    }

    @Test
    fun `a secondary click opens the long-press menu on a Folder row`() {
        val inputFolder: Launchable = Folder(folderId = 1L, name = "Games")
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = inputFolder,
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses)
        )

        row().performMouseInput { rightClick() }

        assertEquals(listOf(inputFolder to MenuSuggestion.None), actualLongPresses)
    }

    @Test
    fun `a secondary click does not open a menu while widgets are edited`() {
        assertHomeFavouriteIgnoresSecondaryClick(isEditingWidgets = true)
    }

    @Test
    fun `a secondary click does not open a menu while widgets are resized`() {
        assertHomeFavouriteIgnoresSecondaryClick(isResizing = true)
    }

    @Test
    fun `a secondary click does not open a menu under a scrim`() {
        val actualLongPresses: MutableList<Launchable> = mutableListOf()
        composeRule.setContent {
            RowTestContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeTouches()
                ) {
                    LauncherItemTestContent(
                        item = testFirstApp,
                        itemActions = createLauncherItemActions().copy(onItemLongPress = { item, _ -> actualLongPresses += item })
                    )
                }
            }
        }

        row().performMouseInput { rightClick() }
        composeRule.waitForIdle()

        assertTrue(actualLongPresses.isEmpty())
    }

    @Test
    fun `a secondary-click drag opens only the menu of the row where the press began`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setHomeSuggestionRows(itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses))
        val actualAlphaBounds = composeRule.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        val actualBetaBounds = composeRule.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot
        val inputMove = actualBetaBounds.center - actualAlphaBounds.center

        composeRule.onNodeWithText("Alpha").performMouseInput {
            press(MouseButton.Secondary)
            moveBy(inputMove)
            release(MouseButton.Secondary)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(testFirstApp to MenuSuggestion.Home), actualLongPresses)
    }

    @Test
    fun `a held secondary button opens no menu when the pointer moves over a row`() {
        val actualLongPresses: MutableList<Launchable> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = testFirstApp,
            itemActions = createLauncherItemActions().copy(onItemLongPress = { item, _ -> actualLongPresses += item })
        )

        row().dispatchMouseHover(action = MotionEvent.ACTION_HOVER_ENTER, buttonState = 0)
        assertNotEquals(rowTestBackgroundColor, row().topEdgeColor())
        row().dispatchMouseHover(action = MotionEvent.ACTION_HOVER_MOVE, buttonState = MotionEvent.BUTTON_SECONDARY)

        assertTrue(actualLongPresses.isEmpty())
    }

    @Test
    fun `a stylus button press opens the long-press menu`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = testFirstApp,
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses)
                .copy(onItemTap = { item, _ -> actualOpens += item })
        )

        row().performStylusPress(buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertEquals(listOf(testFirstApp to MenuSuggestion.None), actualLongPresses)
        assertTrue(actualOpens.isEmpty())
    }

    @Test
    fun `a stylus tip tap opens the row and keeps its long-press menu closed`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = testFirstApp,
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses)
                .copy(onItemTap = { item, _ -> actualOpens += item })
        )

        row().performStylusPress(buttonState = 0)

        assertEquals(listOf(testFirstApp), actualOpens)
        assertTrue(actualLongPresses.isEmpty())
    }

    @Test
    fun `a touch tap and a left click still open the row`() {
        val actualOpens: MutableList<Launchable> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = testFirstApp,
            itemActions = createLauncherItemActions().copy(onItemTap = { item, _ -> actualOpens += item })
        )

        row().performTouchInput { click() }
        row().performMouseInput { click() }

        assertEquals(listOf(testFirstApp, testFirstApp), actualOpens)
    }

    @Test
    fun `a touch long-press still opens the long-press menu`() {
        val actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>> = mutableListOf()
        composeRule.setLauncherItemRow(
            item = testFirstApp,
            itemActions = createLongPressRecordingActions(actualLongPresses = actualLongPresses)
        )

        row().performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals(listOf(testFirstApp to MenuSuggestion.None), actualLongPresses)
    }

    @Test
    fun `hovering a row highlights it and leaving removes the highlight`() {
        val expectedHoverColor: Color = captureHoverColor { LauncherItemTestContent(item = testFirstApp) }

        assertHoverHighlights(actualRow = row(), expectedHoverColor = expectedHoverColor)
    }

    @Test
    fun `hovering an action row highlights it`() {
        val expectedHoverColor: Color = captureHoverColor { LauncherActionRowTestContent() }

        assertHoverHighlights(actualRow = composeRule.onNodeWithTag(LAUNCHER_ACTION_ROW_TEST_TAG), expectedHoverColor = expectedHoverColor)
    }

    @Test
    fun `hovering a Private Space row highlights it`() {
        val expectedHoverColor: Color = captureHoverColor { PrivateSpaceRowTestContent() }

        assertHoverHighlights(actualRow = composeRule.onNodeWithTag(PRIVATE_SPACE_ROW_TEST_TAG), expectedHoverColor = expectedHoverColor)
    }

    @Test
    fun `hovering an Unlocked Private Space row highlights it`() {
        val expectedHoverColor: Color = captureHoverColor { PrivateSpaceRowTestContent(isLocked = false) }
        assertHoverHighlights(actualRow = composeRule.onNodeWithTag(PRIVATE_SPACE_ROW_TEST_TAG), expectedHoverColor = expectedHoverColor)
    }

    @Test
    fun `the mouse wheel scrolls Home`() {
        val inputFavorites: List<Launchable> = createScrollableApps()
        val inputLazyListState: LazyListState = LazyListState()
        composeRule.setHomeFavoriteRows(
            favorites = inputFavorites,
            itemActions = createLauncherItemActions(),
            lazyListState = inputLazyListState
        )
        assertMouseWheelScrolls(lazyListState = inputLazyListState, firstFavoriteLabel = inputFavorites.first().name)
    }

    @Test
    fun `the mouse wheel scrolls the App List`() {
        val inputApps: List<Launchable> = createScrollableApps()
        val inputLazyListState: LazyListState = LazyListState()
        composeRule.setAppListRows(
            itemActions = createLauncherItemActions(),
            appList = listOf(LaunchableGroup.InitialGroup(label = "A", items = inputApps)),
            lazyListState = inputLazyListState
        )
        assertMouseWheelScrolls(lazyListState = inputLazyListState, firstFavoriteLabel = inputApps.first().name)
    }

    private fun assertMouseWheelScrolls(lazyListState: LazyListState, firstFavoriteLabel: String) {
        composeRule.waitForIdle()
        val inputInitialItemIndex: Int = lazyListState.firstVisibleItemIndex
        val inputInitialItemOffset: Int = lazyListState.firstVisibleItemScrollOffset
        composeRule.onAllNodesWithText(firstFavoriteLabel).onFirst().performMouseInput {
            moveTo(center)
            scroll(delta = MOUSE_WHEEL_DELTA)
        }
        composeRule.waitForIdle()
        assertTrue(
            lazyListState.firstVisibleItemIndex > inputInitialItemIndex ||
                lazyListState.firstVisibleItemScrollOffset > inputInitialItemOffset
        )
    }

    private fun createScrollableApps(): List<Launchable> = (1..SCROLLABLE_APP_COUNT).map { index: Int ->
        createFavorite(index = index)
    }

    private fun createLongPressRecordingActions(actualLongPresses: MutableList<Pair<Launchable, MenuSuggestion>>): LauncherItemActions =
        createLauncherItemActions().copy(
            onItemLongPress = { item, suggestion -> actualLongPresses += item to suggestion }
        )

    private fun captureHoverColor(content: @Composable () -> Unit): Color = composeRule.captureThemeColor(
        color = { MaterialTheme.colorScheme.primary.copy(alpha = ROW_HOVER_BACKGROUND_ALPHA).compositeOver(rowTestBackgroundColor) },
        content = { CompositionLocalProvider(LocalIndication provides NoIndication) { content() } }
    )

    private fun assertHoverHighlights(actualRow: SemanticsNodeInteraction, expectedHoverColor: Color) {
        actualRow.assertPaintsTopEdge(expectedColor = rowTestBackgroundColor)

        actualRow.performMouseInput { moveTo(center) }
        composeRule.waitForIdle()
        assertNotEquals(rowTestBackgroundColor, actualRow.topEdgeColor())
        actualRow.assertPaintsTopEdge(expectedColor = expectedHoverColor)

        actualRow.performMouseInput { exit() }
        composeRule.waitForIdle()
        actualRow.assertPaintsTopEdge(expectedColor = rowTestBackgroundColor)
    }

    private fun assertHomeFavouriteIgnoresSecondaryClick(isResizing: Boolean = false, isEditingWidgets: Boolean = false) {
        val actualLongPresses: MutableList<Launchable> = mutableListOf()
        composeRule.setHomeFavoriteRows(
            favorites = listOf(testFirstApp),
            itemActions = createLauncherItemActions().copy(onItemLongPress = { item, _ -> actualLongPresses += item }),
            isResizing = isResizing,
            isEditingWidgets = isEditingWidgets
        )

        composeRule.onAllNodesWithText("Alpha").onFirst().performMouseInput { rightClick() }
        composeRule.waitForIdle()

        assertTrue(actualLongPresses.isEmpty())
    }

    private fun row(): SemanticsNodeInteraction = composeRule.onNodeWithTag(LAUNCHER_ROW_TEST_TAG)

    private fun SemanticsNodeInteraction.performStylusPress(buttonState: Int) {
        val actualNode: SemanticsNode = fetchSemanticsNode()
        val actualView: View = (requireNotNull(actualNode.root) as ViewRootForTest).view
        val inputCenter: Offset = actualNode.boundsInRoot.center
        composeRule.runOnIdle {
            actualView.dispatchTouchEvent(
                inputCenter.toMotionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    buttonState = buttonState,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS
                )
            )
            actualView.dispatchTouchEvent(
                inputCenter.toMotionEvent(
                    action = MotionEvent.ACTION_UP,
                    buttonState = 0,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS
                )
            )
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.dispatchMouseHover(action: Int, buttonState: Int) {
        val actualNode: SemanticsNode = fetchSemanticsNode()
        val actualView: View = (requireNotNull(actualNode.root) as ViewRootForTest).view
        val inputCenter: Offset = actualNode.boundsInRoot.center
        composeRule.runOnIdle {
            actualView.dispatchHoverEventForTest(
                inputCenter.toMotionEvent(
                    action = action,
                    buttonState = buttonState,
                    toolType = MotionEvent.TOOL_TYPE_MOUSE,
                    source = InputDevice.SOURCE_MOUSE
                )
            )
        }
        composeRule.waitForIdle()
    }

    private fun View.dispatchHoverEventForTest(motionEvent: MotionEvent) {
        View::class.java.getDeclaredMethod("dispatchHoverEvent", MotionEvent::class.java).apply {
            isAccessible = true
        }.invoke(this, motionEvent)
    }

    private fun Offset.toMotionEvent(action: Int, buttonState: Int, toolType: Int, source: Int): MotionEvent {
        val inputProperties: MotionEvent.PointerProperties = MotionEvent.PointerProperties().apply {
            id = 0
            this.toolType = toolType
        }
        val inputCoordinates: MotionEvent.PointerCoords = MotionEvent.PointerCoords().apply {
            x = this@toMotionEvent.x
            y = this@toMotionEvent.y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            0L,
            0L,
            action,
            1,
            arrayOf(inputProperties),
            arrayOf(inputCoordinates),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            source,
            0
        )
    }
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class NoIndicationNode :
    Modifier.Node(),
    DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
    }
}
