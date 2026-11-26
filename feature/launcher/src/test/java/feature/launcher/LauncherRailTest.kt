package feature.launcher

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import core.data.launchables.model.LaunchableGroup
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.testing.TEST_SDK
import core.data.testing.createAppListGroups
import core.data.testing.createBuiltinGroup
import core.data.testing.localeGroupingCases
import core.ui.GroupMark
import core.ui.composables.scrollrail.RailBounds
import core.ui.composables.scrollrail.SCROLL_RAIL_BUBBLE_TEST_TAG
import core.ui.composables.scrollrail.SCROLL_RAIL_TEST_TAG
import core.ui.layout.HomeEditMode
import core.ui.model.data.KeyboardShortcut
import core.ui.model.data.LauncherSignal
import core.ui.popups.popupSwipe
import core.ui.toGroupMark
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class LauncherRailTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `draws the Rail on the layout end by default`() {
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.BOTH
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = false)
        assertScrubStripOnSide(side = RailSide.START, isOnLeft = true)
    }

    @Test
    fun `draws and scrubs the Rail on the layout end in a right-to-left layout for the default Rail side`() {
        assertBothEdgesScrub(railSide = RailSide.DEFAULT, layoutDirection = LayoutDirection.Rtl, isRailOnLeft = true)
    }

    @Test
    fun `draws and scrubs the Rail on the layout start in a right-to-left layout for the Start Rail side`() {
        assertBothEdgesScrub(railSide = RailSide.START, layoutDirection = LayoutDirection.Rtl, isRailOnLeft = false)
    }

    @Test
    fun `scrub bubble sits inside on the inner side in a right-to-left layout for the default Rail side`() {
        assertScrubBubbleOnInnerSide(railSide = RailSide.DEFAULT, layoutDirection = LayoutDirection.Rtl, isRailOnLeft = true)
    }

    @Test
    fun `scrub bubble sits inside on the inner side in a right-to-left layout for the Start Rail side`() {
        assertScrubBubbleOnInnerSide(railSide = RailSide.START, layoutDirection = LayoutDirection.Rtl, isRailOnLeft = false)
    }

    @Test
    fun `draws and scrubs the Rail on the layout start for the Start Rail side`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        val railBounds: RailBounds = setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.START,
            railSide = RailSide.START
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = true)
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualRail.previewIndices)
    }

    @Test
    fun `Start Rail side scrubs from the drawn Rail and the scrub strip on the end edge`() {
        assertBothEdgesScrub(railSide = RailSide.START, layoutDirection = LayoutDirection.Ltr, isRailOnLeft = true)
    }

    @Test
    fun `Start Rail side ignores its drawn Rail when Scrub edges exclude it`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.END,
            railSide = RailSide.START
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.END)

        assertEquals(listOf(0, 1, 2), actualRail.previewIndices)
    }

    @Test
    fun `scrubbing the drawn Rail does nothing while editing Widgets`() {
        var actualCloseCount: Int = 0
        var actualHapticTickCount: Int = 0
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            homeEditMode = { HomeEditMode.Widgets },
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ },
            onHapticTick = { actualHapticTickCount++ }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(0, actualCloseCount)
        assertEquals(0, actualHapticTickCount)
        assertEquals(emptyList<Int>(), actualRail.previewIndices)
        assertEquals(emptyList<Int>(), actualRail.releaseIndices)
    }

    @Test
    fun `Both scrub edges preview and release from the drawn Rail and the scrub strip`() {
        assertBothEdgesScrub(railSide = RailSide.DEFAULT, layoutDirection = LayoutDirection.Ltr, isRailOnLeft = false)
    }

    @Test
    fun `a hovering mouse over the Rail does not scrub it`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performMouseInput {
            moveTo(center)
            moveBy(Offset(x = 0f, y = -20f))
        }

        assertEquals(emptyList<Int>(), actualRail.previewIndices)
        assertEquals(emptyList<Int>(), actualRail.releaseIndices)
    }

    @Test
    fun `a downward wheel notch over the Rail steps one group per notch and stops at the edges`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performMouseInput {
            moveTo(center)
            scroll(delta = 1f)
            scroll(delta = 1f)
            scroll(delta = 1f)
            scroll(delta = -1f)
        }

        assertEquals(listOf(1, 2, 1), actualRail.releaseIndices)
        assertEquals(emptyList<Int>(), actualRail.previewIndices)
    }

    @Test
    fun `a wheel notch after Home steps from the Star`() {
        assertWheelStepAfterStateChange(
            changeShownState = { inputShownUiState -> inputShownUiState.value = HOME_UI_STATE },
            expectedReleaseIndices = listOf(4, 1)
        )
    }

    @Test
    fun `a wheel notch after Tab steps from the first App List group`() {
        assertWheelStepAfterStateChange(
            changeShownState = { inputShownUiState ->
                inputShownUiState.value = createAppListUiState(firstVisibleItemIndex = 1)
            },
            expectedReleaseIndices = listOf(4, 2)
        )
    }

    @Test
    fun `a wheel notch after an App List scroll steps from the shown group`() {
        assertWheelStepAfterStateChange(
            changeShownState = { inputShownUiState ->
                inputShownUiState.value = createAppListUiState(firstVisibleItemIndex = 2)
            },
            expectedReleaseIndices = listOf(4, 3)
        )
    }

    @Test
    fun `a wheel step from a Scrolling group uses its Rail index when marks repeat`() {
        val inputBuiltinGroup: LaunchableGroup.Builtin = createBuiltinGroup()
        val inputDuplicateGroup: LaunchableGroup.InitialGroup = LaunchableGroup.InitialGroup(
            label = inputBuiltinGroup.label,
            items = emptyList()
        )
        val inputGroups: List<LaunchableGroup.Listed> = listOf(inputDuplicateGroup, inputBuiltinGroup)
        val inputRailItems: List<GroupMark> = listOf(LaunchableGroup.Star.toGroupMark()) +
            inputGroups.map { group -> group.toGroupMark() }
        assertWheelStepAfterStateChange(
            changeShownState = { inputShownUiState ->
                inputShownUiState.value = LauncherUiState.AppList.Scrolling(
                    group = inputBuiltinGroup,
                    railIndex = 2,
                    isEditingFavorites = false
                )
            },
            expectedReleaseIndices = listOf(2, 1),
            railItems = inputRailItems,
            inputDeltaY = -1f
        )
    }

    @Test
    fun `a wheel notch after scrolling the App List to its top steps from the first group`() {
        assertWheelStepAfterStateChange(
            changeShownState = { inputShownUiState ->
                inputShownUiState.value = createAppListUiState(firstVisibleItemIndex = 0)
            },
            expectedReleaseIndices = listOf(4, 2)
        )
    }

    @Test
    fun `a wheel notch after Ctrl or Meta plus a number steps from the opened row's group`() {
        assertWheelStepAfterStateChange(
            changeShownState = { inputShownUiState ->
                val inputPostShortcutStates: List<LauncherUiState.AppList.Browsing> = listOf(
                    KeyEvent.META_CTRL_ON,
                    KeyEvent.META_META_ON
                ).map { inputMetaState: Int -> createAppListAfterOpeningRow(inputMetaState = inputMetaState) }
                inputPostShortcutStates.forEach { inputState: LauncherUiState.AppList.Browsing ->
                    assertEquals(2, inputState.lazyListState.firstVisibleItemIndex)
                }
                inputShownUiState.value = inputPostShortcutStates.first()
            },
            expectedReleaseIndices = listOf(4, 3)
        )
    }

    @Test
    fun `a fast downward wheel delta emits consecutive Rail wheel steps`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performMouseInput {
            moveTo(center)
            scroll(delta = 5f)
        }

        assertEquals(listOf(1, 2), actualRail.releaseIndices)
    }

    @Test
    fun `a partial downward wheel notch is dropped when the pointer leaves the Rail`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performMouseInput {
            moveTo(center)
            scroll(delta = 0.9f)
            exit()
            moveTo(center)
            scroll(delta = 0.9f)
        }

        assertEquals(emptyList<Int>(), actualRail.releaseIndices)
    }

    @Test
    fun `a downward wheel notch over the Rail closes an open Pop-up instead of stepping`() {
        var actualCloseCount: Int = 0
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ }
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performMouseInput {
            moveTo(center)
            scroll(delta = 1f)
        }

        assertEquals(1, actualCloseCount)
        assertEquals(emptyList<Int>(), actualRail.releaseIndices)
    }

    @Test
    fun `End scrub edges preview and release only from the drawn Rail`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.END
        )

        composeRule.onNodeWithTag(railScrubStripTestTag(side = RailSide.START)).assertDoesNotExist()
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualRail.previewIndices)
        assertEquals(listOf(2), actualRail.releaseIndices)
    }

    @Test
    fun `Start scrub edges preview and release only from the scrub strip`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.START
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2), actualRail.previewIndices)
        assertEquals(listOf(2), actualRail.releaseIndices)
    }

    @Test
    fun `scrubbing a scrub strip closes an open Pop-up instead of the Rail`() {
        var actualCloseCount: Int = 0
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.START,
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ }
        )

        dragScrubStrip(side = RailSide.START)

        assertEquals(1, actualCloseCount)
        assertEquals(emptyList<Int>(), actualRail.previewIndices)
        assertEquals(emptyList<Int>(), actualRail.releaseIndices)
    }

    @Test
    fun `scrubbing a scrub strip does nothing while editing Widgets`() {
        var actualCloseCount: Int = 0
        var actualHapticTickCount: Int = 0
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.START,
            homeEditMode = { HomeEditMode.Widgets },
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ },
            onHapticTick = { actualHapticTickCount++ }
        )

        dragScrubStrip(side = RailSide.START)

        assertEquals(0, actualCloseCount)
        assertEquals(0, actualHapticTickCount)
        assertEquals(emptyList<Int>(), actualRail.previewIndices)
        assertEquals(emptyList<Int>(), actualRail.releaseIndices)
    }

    @Test
    fun `scrubbing both edges previews and releases while editing Favorites`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            homeEditMode = { HomeEditMode.Favorites }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualRail.previewIndices)
        assertEquals(listOf(2, 2), actualRail.releaseIndices)
    }

    @Test
    fun `scrubbing both edges works again when editing Widgets ends`() {
        var inputHomeEditMode: HomeEditMode by mutableStateOf(HomeEditMode.Widgets)
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            homeEditMode = { inputHomeEditMode }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)
        assertEquals(emptyList<Int>(), actualRail.previewIndices)
        assertEquals(emptyList<Int>(), actualRail.releaseIndices)

        composeRule.runOnIdle { inputHomeEditMode = HomeEditMode.None }
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualRail.previewIndices)
        assertEquals(listOf(2, 2), actualRail.releaseIndices)
    }

    private fun assertWheelStepAfterStateChange(
        changeShownState: (MutableState<LauncherUiState>) -> Unit,
        expectedReleaseIndices: List<Int>,
        railItems: List<GroupMark> = WHEEL_MARKS,
        inputDeltaY: Float = 1f
    ) {
        val inputShownUiState: MutableState<LauncherUiState> = mutableStateOf(HOME_UI_STATE)
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            railItems = { railItems },
            uiState = { inputShownUiState.value }
        )
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG, marks = railItems)
        composeRule.runOnIdle { changeShownState(inputShownUiState) }
        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performMouseInput {
            moveTo(center)
            scroll(delta = inputDeltaY)
        }
        assertEquals(expectedReleaseIndices, actualRail.releaseIndices)
    }

    private fun createAppListUiState(firstVisibleItemIndex: Int): LauncherUiState.AppList.Browsing = LauncherUiState.AppList.Browsing(
        groups = WHEEL_GROUPS,
        recentlyInstalled = null,
        lazyListState = LazyListState(firstVisibleItemIndex = firstVisibleItemIndex),
        isEditingFavorites = false
    )

    private fun createAppListAfterOpeningRow(inputMetaState: Int): LauncherUiState.AppList.Browsing {
        val inputKeyCode: Int = KeyEvent.KEYCODE_2
        val inputEvent: KeyEvent = KeyEvent(
            0L,
            0L,
            KeyEvent.ACTION_DOWN,
            inputKeyCode,
            0,
            inputMetaState,
            0,
            0,
            0
        )
        val inputShortcut: KeyboardShortcut = requireNotNull(KeyboardShortcut.forEvent(inputKeyCode, inputEvent))
        val inputSignal: LauncherSignal.OpenIndex = inputShortcut.signal as? LauncherSignal.OpenIndex
            ?: error("Expected an OpenIndex signal")
        return createAppListUiState(firstVisibleItemIndex = inputSignal.index + 1)
    }

    @Test
    fun `scrubbing with Rail haptics off performs no tick haptics`() {
        assertHapticTickCount(railHaptics = false)
    }

    @Test
    fun `scrubbing with Rail haptics on ticks once per scrubbed mark`() {
        assertHapticTickCount(railHaptics = true)
    }

    private fun assertHapticTickCount(railHaptics: Boolean) {
        var actualHapticTickCount: Int = 0
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            railHaptics = railHaptics,
            onHapticTick = { actualHapticTickCount++ }
        )

        val scrubbedMarkCount: Int = dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG) +
            dragScrubStrip(side = RailSide.START)

        val expectedHapticTickCount: Int = if (railHaptics) scrubbedMarkCount else 0
        assertEquals(expectedHapticTickCount, actualHapticTickCount)
    }

    @Test
    fun `draws the Rail's marks as the App List groups of every covered locale`() {
        val railItems: MutableState<List<GroupMark>> = mutableStateOf(emptyList())
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            railItems = { railItems.value }
        )

        localeGroupingCases.forEach { inputCase ->
            val inputGroups: List<LaunchableGroup> = createAppListGroups(
                locale = Locale.forLanguageTag(inputCase.localeTag),
                names = inputCase.names
            )
            composeRule.runOnIdle {
                railItems.value = inputGroups.map { group -> group.toGroupMark() }
            }
            composeRule.waitForIdle()

            val expectedMarkTexts: List<String> =
                listOf(LaunchableGroup.Star.label) + inputCase.expectedLabels + createBuiltinGroup().label
            assertEquals("${inputCase.localeTag} Rail marks", expectedMarkTexts, drawnMarkTexts())
        }
    }

    @Test
    fun `hides the drawn Rail when Show Rail is off`() {
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            showRail = { false }
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `puts a scrub strip on both enabled edges when Show Rail is off`() {
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            showRail = { false }
        )

        assertScrubStripOnSide(side = RailSide.START, isOnLeft = true)
        assertScrubStripOnSide(side = RailSide.END, isOnLeft = false)
    }

    @Test
    fun `scrubs both edges including the hidden Rail's own edge`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            showRail = { false }
        )

        dragScrubStrip(side = RailSide.END)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualRail.previewIndices)
        assertEquals(listOf(2, 2), actualRail.releaseIndices)
    }

    @Test
    fun `scrubs only the enabled edge when Show Rail is off`() {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.START,
            showRail = { false }
        )

        composeRule.onNodeWithTag(railScrubStripTestTag(side = RailSide.END)).assertDoesNotExist()
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2), actualRail.previewIndices)
    }

    @Test
    fun `publishes the Rail bounds from the hidden Rail's own strip`() {
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            showRail = { false }
        )

        composeRule.waitForIdle()

        assertNotNull(railBounds.current)
    }

    @Test
    fun `does not stop a row swipe at the old Rail column when the Rail is hidden with no strip on its side`() {
        val inputShowRail: MutableState<Boolean> = mutableStateOf(true)
        var actualOpenCount: Int = 0
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.START,
            showRail = { inputShowRail.value },
            rowContent = { bounds ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ROW_TEST_TAG)
                        .popupSwipe(
                            enabled = true,
                            railSide = RailSide.DEFAULT,
                            railBounds = { bounds.current },
                            onSwipeOpen = { actualOpenCount++ }
                        )
                )
            }
        )
        composeRule.waitForIdle()

        changeShowRail(inputShowRail, isShown = false)
        composeRule.onNodeWithTag(ROW_TEST_TAG).performTouchInput {
            down(position = Offset(x = width * ROW_SWIPE_START_FRACTION, y = center.y))
            moveTo(position = Offset(x = width - 1f, y = center.y))
            up()
        }
        composeRule.waitForIdle()

        assertNull(railBounds.current)
        assertEquals(1, actualOpenCount)
    }

    @Test
    fun `stores the drawn Rail bounds again when Show Rail turns back on`() {
        val inputShowRail: MutableState<Boolean> = mutableStateOf(false)
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.START,
            showRail = { inputShowRail.value }
        )
        composeRule.waitForIdle()

        changeShowRail(inputShowRail, isShown = true)

        assertNotNull(railBounds.current)
    }

    @Test
    fun `keeps the strip bounds when the Rail is hidden with a strip on its side`() {
        val inputShowRail: MutableState<Boolean> = mutableStateOf(true)
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.END,
            showRail = { inputShowRail.value }
        )
        composeRule.waitForIdle()

        changeShowRail(inputShowRail, isShown = false)

        assertNotNull(railBounds.current)
    }

    private fun changeShowRail(showRail: MutableState<Boolean>, isShown: Boolean) {
        showRail.value = isShown
        composeRule.waitForIdle()
    }

    private fun setRailContent(
        scrubEdges: ScrubEdges,
        railItems: () -> List<GroupMark> = { MARKS },
        uiState: () -> LauncherUiState = { HOME_UI_STATE },
        homeEditMode: () -> HomeEditMode = { HomeEditMode.None },
        railSide: RailSide = RailSide.DEFAULT,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        showRail: () -> Boolean = { true },
        isCardOpen: () -> Boolean = { false },
        railHaptics: Boolean = true,
        onRailTouched: () -> Unit = {},
        onRailPreview: (Int) -> Unit = {},
        onRailReleased: (Int) -> Unit = {},
        onHapticTick: () -> Unit = {},
        rowContent: @Composable BoxScope.(RailBounds) -> Unit = {}
    ): RailBounds {
        val railBounds: RailBounds = RailBounds()
        composeRule.setContent {
            val hapticFeedback: HapticFeedback = remember(onHapticTick) {
                object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        onHapticTick()
                    }
                }
            }
            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalHapticFeedback provides hapticFeedback
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    rowContent(railBounds)
                    LauncherRail(
                        railItems = railItems(),
                        uiState = uiState,
                        railBounds = remember { railBounds },
                        railSide = railSide,
                        scrubEdges = scrubEdges,
                        showRail = showRail(),
                        isCardOpen = isCardOpen,
                        railHaptics = railHaptics,
                        onRailTouched = onRailTouched,
                        onRailPreview = onRailPreview,
                        onRailReleased = onRailReleased,
                        homeEditMode = homeEditMode()
                    )
                }
            }
        }
        return railBounds
    }

    /** The Rail preview and release indices one test records. */
    private class RailCallbackRecorder {
        val previewIndices: MutableList<Int> = mutableListOf()
        val releaseIndices: MutableList<Int> = mutableListOf()
    }

    /** Sets the Rail content, recording its preview and release indices into [recorder]. */
    private fun setRecordedRailContent(
        recorder: RailCallbackRecorder,
        scrubEdges: ScrubEdges,
        railSide: RailSide = RailSide.DEFAULT,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        showRail: () -> Boolean = { true },
        isCardOpen: () -> Boolean = { false },
        railHaptics: Boolean = true,
        homeEditMode: () -> HomeEditMode = { HomeEditMode.None },
        onRailTouched: () -> Unit = {},
        onHapticTick: () -> Unit = {},
        railItems: () -> List<GroupMark> = { MARKS },
        uiState: () -> LauncherUiState = {
            recorder.releaseIndices.lastOrNull()?.let { index -> createAppListUiState(firstVisibleItemIndex = index) }
                ?: HOME_UI_STATE
        }
    ): RailBounds = setRailContent(
        scrubEdges = scrubEdges,
        railSide = railSide,
        layoutDirection = layoutDirection,
        showRail = showRail,
        isCardOpen = isCardOpen,
        railHaptics = railHaptics,
        homeEditMode = homeEditMode,
        onRailTouched = onRailTouched,
        onRailPreview = { index -> recorder.previewIndices += index },
        onRailReleased = { index -> recorder.releaseIndices += index },
        onHapticTick = onHapticTick,
        railItems = railItems,
        uiState = uiState
    )

    private fun assertBothEdgesScrub(railSide: RailSide, layoutDirection: LayoutDirection, isRailOnLeft: Boolean) {
        val actualRail: RailCallbackRecorder = RailCallbackRecorder()
        val railBounds: RailBounds = setRecordedRailContent(
            recorder = actualRail,
            scrubEdges = ScrubEdges.BOTH,
            railSide = railSide,
            layoutDirection = layoutDirection
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = isRailOnLeft)
        assertScrubStripOnSide(side = railSide.opposite(), isOnLeft = !isRailOnLeft)

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = railSide.opposite())

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualRail.previewIndices)
        assertEquals(listOf(2, 2), actualRail.releaseIndices)
    }

    private fun assertScrubBubbleOnInnerSide(railSide: RailSide, layoutDirection: LayoutDirection, isRailOnLeft: Boolean) {
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            railSide = railSide,
            layoutDirection = layoutDirection
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()

        val actualRailBounds: Rect = requireNotNull(railBounds.current)
        val actualBubbleBounds: DpRect = composeRule.onNodeWithTag(SCROLL_RAIL_BUBBLE_TEST_TAG).getUnclippedBoundsInRoot()
        val actualRootBounds: DpRect = composeRule.screenBounds()
        with(composeRule.density) {
            val actualBubbleCenterX: Float = (actualBubbleBounds.left.toPx() + actualBubbleBounds.right.toPx()) / 2f
            val isBubbleOnRight: Boolean = actualBubbleCenterX > actualRailBounds.center.x
            assertEquals(isRailOnLeft, isBubbleOnRight)
        }
        assertHorizontallyInsideScreen(actualBubbleBounds, actualRootBounds, "Bubble must be inside the screen")
    }

    private fun assertRailInsideScreenOnSide(railBounds: RailBounds, isOnLeft: Boolean) {
        composeRule.waitForIdle()
        val actualBounds: Rect = requireNotNull(railBounds.current)
        val actualRootBounds: DpRect = composeRule.screenBounds()
        val actualBoundsDp: DpRect = with(composeRule.density) {
            DpRect(
                left = actualBounds.left.toDp(),
                top = actualBounds.top.toDp(),
                right = actualBounds.right.toDp(),
                bottom = actualBounds.bottom.toDp()
            )
        }
        with(composeRule.density) {
            val actualRootLeftPx: Float = actualRootBounds.left.toPx()
            val actualRootRightPx: Float = actualRootBounds.right.toPx()
            assertEquals(isOnLeft, actualBounds.center.x < (actualRootLeftPx + actualRootRightPx) / 2f)
        }
        assertHorizontallyInsideScreen(actualBoundsDp, actualRootBounds, "Rail must be inside the screen")
    }

    private fun assertScrubStripOnSide(side: RailSide, isOnLeft: Boolean) {
        composeRule.waitForIdle()
        val actualStripBounds: DpRect = composeRule.onNodeWithTag(railScrubStripTestTag(side = side)).getUnclippedBoundsInRoot()
        val actualRootBounds: DpRect = composeRule.screenBounds()
        assertEquals(isOnLeft, actualStripBounds.centerX() < actualRootBounds.centerX())
    }

    private fun DpRect.centerX(): Float = (left.value + right.value) / 2f

    /** Drags the Rail scrub strip on [side] and returns how many marks it scrubbed. */
    private fun dragScrubStrip(side: RailSide): Int = dragScrubEdge(tag = railScrubStripTestTag(side = side))

    /** Drags across every mark on the edge with [tag] and returns how many marks it scrubbed. */
    private fun dragScrubEdge(tag: String, marks: List<GroupMark> = MARKS): Int {
        composeRule.onNodeWithTag(tag).performTouchInput {
            fun markCenterY(index: Int): Float = height / marks.size * (index + 0.5f)
            down(position = Offset(x = center.x, y = markCenterY(index = 0)))
            marks.indices.drop(1).forEach { index ->
                moveTo(position = Offset(x = center.x, y = markCenterY(index = index)))
            }
            up()
        }
        return marks.size
    }

    /** The texts the Rail draws, top to bottom, one per mark. */
    private fun drawnMarkTexts(): List<String> = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .sortedBy { node -> node.boundsInRoot.top }
        .map { node -> node.config[SemanticsProperties.Text].joinToString(separator = "") { text -> text.text } }
        .distinct()

    private companion object {
        const val ROW_TEST_TAG: String = "row"
        const val ROW_SWIPE_START_FRACTION: Float = 0.3f
        val HOME_UI_STATE: LauncherUiState.Home = LauncherUiState.Home(
            lazyListState = LazyListState(),
            favorites = emptyList(),
            isFavoritesLoaded = true,
            suggestions = emptyList(),
            isSwipeUpEnabled = false,
            isSwipeDownEnabled = false
        )
        val WHEEL_GROUPS: List<LaunchableGroup.Listed> = createAppListGroups(
            locale = Locale.US,
            names = listOf("Alpha", "Beta", "Gamma")
        ).filterIsInstance<LaunchableGroup.Listed>()
        val WHEEL_MARKS: List<GroupMark> = listOf(LaunchableGroup.Star.toGroupMark()) +
            WHEEL_GROUPS.map { group -> group.toGroupMark() }
        val MARKS: List<GroupMark> = listOf(
            GroupMark.Label(text = "A"),
            GroupMark.Label(text = "B"),
            GroupMark.Label(text = "C")
        )
    }
}
