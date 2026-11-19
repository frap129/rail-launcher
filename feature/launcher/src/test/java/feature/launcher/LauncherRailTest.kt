package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
import core.ui.composables.scrollrail.SCROLL_RAIL_BUBBLE_TEST_TAG
import core.ui.composables.scrollrail.SCROLL_RAIL_TEST_TAG
import core.ui.layout.HomeEditMode
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
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.START,
            railSide = RailSide.START,
            onRailPreview = { index -> actualPreviewIndices += index }
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = true)
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
    }

    @Test
    fun `Start Rail side scrubs from the drawn Rail and the scrub strip on the end edge`() {
        assertBothEdgesScrub(railSide = RailSide.START, layoutDirection = LayoutDirection.Ltr, isRailOnLeft = true)
    }

    @Test
    fun `Start Rail side ignores its drawn Rail when Scrub edges exclude it`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.END,
            railSide = RailSide.START,
            onRailPreview = { index -> actualPreviewIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.END)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
    }

    @Test
    fun `scrubbing the drawn Rail does nothing while editing Widgets`() {
        var actualCloseCount: Int = 0
        var actualHapticTickCount: Int = 0
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            homeEditMode = { HomeEditMode.Widgets },
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ },
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index },
            onHapticTick = { actualHapticTickCount++ }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(0, actualCloseCount)
        assertEquals(0, actualHapticTickCount)
        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    @Test
    fun `Both scrub edges preview and release from the drawn Rail and the scrub strip`() {
        assertBothEdgesScrub(railSide = RailSide.DEFAULT, layoutDirection = LayoutDirection.Ltr, isRailOnLeft = false)
    }

    @Test
    fun `End scrub edges preview and release only from the drawn Rail`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.END,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        composeRule.onNodeWithTag(railScrubStripTestTag(side = RailSide.START)).assertDoesNotExist()
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2), actualReleaseIndices)
    }

    @Test
    fun `Start scrub edges preview and release only from the scrub strip`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.START,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2), actualReleaseIndices)
    }

    @Test
    fun `scrubbing a scrub strip closes an open Pop-up instead of the Rail`() {
        var actualCloseCount: Int = 0
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.START,
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ },
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubStrip(side = RailSide.START)

        assertEquals(1, actualCloseCount)
        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    @Test
    fun `scrubbing a scrub strip does nothing while editing Widgets`() {
        var actualCloseCount: Int = 0
        var actualHapticTickCount: Int = 0
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.START,
            homeEditMode = { HomeEditMode.Widgets },
            isCardOpen = { true },
            onRailTouched = { actualCloseCount++ },
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index },
            onHapticTick = { actualHapticTickCount++ }
        )

        dragScrubStrip(side = RailSide.START)

        assertEquals(0, actualCloseCount)
        assertEquals(0, actualHapticTickCount)
        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    @Test
    fun `scrubbing both edges previews and releases while editing Favorites`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            homeEditMode = { HomeEditMode.Favorites },
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
    }

    @Test
    fun `scrubbing both edges works again when editing Widgets ends`() {
        var inputHomeEditMode: HomeEditMode by mutableStateOf(HomeEditMode.Widgets)
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            homeEditMode = { inputHomeEditMode },
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)
        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)

        composeRule.runOnIdle { inputHomeEditMode = HomeEditMode.None }
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
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
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            showRail = { false },
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubStrip(side = RailSide.END)
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
    }

    @Test
    fun `scrubs only the enabled edge when Show Rail is off`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.START,
            showRail = { false },
            onRailPreview = { index -> actualPreviewIndices += index }
        )

        composeRule.onNodeWithTag(railScrubStripTestTag(side = RailSide.END)).assertDoesNotExist()
        dragScrubStrip(side = RailSide.START)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
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

    private fun assertBothEdgesScrub(railSide: RailSide, layoutDirection: LayoutDirection, isRailOnLeft: Boolean) {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            railSide = railSide,
            layoutDirection = layoutDirection,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = isRailOnLeft)
        assertScrubStripOnSide(side = railSide.opposite(), isOnLeft = !isRailOnLeft)

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubStrip(side = railSide.opposite())

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
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
    private fun dragScrubEdge(tag: String): Int {
        composeRule.onNodeWithTag(tag).performTouchInput {
            fun markCenterY(index: Int): Float = height / MARKS.size * (index + 0.5f)
            down(position = Offset(x = center.x, y = markCenterY(index = 0)))
            MARKS.indices.drop(1).forEach { index ->
                moveTo(position = Offset(x = center.x, y = markCenterY(index = index)))
            }
            up()
        }
        return MARKS.size
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
        val MARKS: List<GroupMark> = listOf(
            GroupMark.Label(text = "A"),
            GroupMark.Label(text = "B"),
            GroupMark.Label(text = "C")
        )
    }
}
