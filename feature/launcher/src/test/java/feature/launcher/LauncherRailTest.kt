package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.testing.TEST_SDK
import core.ui.GroupMark
import core.ui.composables.scrollrail.SCROLL_RAIL_BUBBLE_TEST_TAG
import core.ui.composables.scrollrail.SCROLL_RAIL_TEST_TAG
import core.ui.layout.HomeEditMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            scrubEdges = ScrubEdges.BOTH,
            onRailPreview = {},
            onRailReleased = {}
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = false)
        assertMirrorStripOnSide(isOnLeft = true)
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
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = {}
        )

        assertRailInsideScreenOnSide(railBounds = railBounds, isOnLeft = true)
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
    }

    @Test
    fun `Start Rail side scrubs from the drawn Rail and the mirror strip on the end edge`() {
        assertBothEdgesScrub(railSide = RailSide.START, layoutDirection = LayoutDirection.Ltr, isRailOnLeft = true)
    }

    @Test
    fun `Start Rail side ignores its drawn Rail when Scrub edges exclude it`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.END,
            railSide = RailSide.START,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = {}
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

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
    fun `Both scrub edges preview and release from the drawn Rail and the mirror strip`() {
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

        composeRule.onNodeWithTag(RAIL_MIRROR_STRIP_TEST_TAG).assertDoesNotExist()
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2), actualReleaseIndices)
    }

    @Test
    fun `Start scrub edges preview and release only from the mirror strip`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.START,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2), actualReleaseIndices)
    }

    @Test
    fun `scrubbing the mirror strip closes an open Pop-up instead of the Rail`() {
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

        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(1, actualCloseCount)
        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    @Test
    fun `scrubbing the mirror strip does nothing while editing Widgets`() {
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

        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

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
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

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
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)
        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)

        composeRule.runOnIdle { inputHomeEditMode = HomeEditMode.None }
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
    }

    private fun setRailContent(
        scrubEdges: ScrubEdges,
        homeEditMode: () -> HomeEditMode = { HomeEditMode.None },
        railSide: RailSide = RailSide.DEFAULT,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        isCardOpen: () -> Boolean = { false },
        onRailTouched: () -> Unit = {},
        onRailPreview: (Int) -> Unit,
        onRailReleased: (Int) -> Unit,
        onHapticTick: () -> Unit = {}
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
                    LauncherRail(
                        railItems = MARKS,
                        railBounds = remember { railBounds },
                        railSide = railSide,
                        scrubEdges = scrubEdges,
                        isCardOpen = isCardOpen,
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
        assertMirrorStripOnSide(isOnLeft = !isRailOnLeft)

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
    }

    private fun assertScrubBubbleOnInnerSide(railSide: RailSide, layoutDirection: LayoutDirection, isRailOnLeft: Boolean) {
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            railSide = railSide,
            layoutDirection = layoutDirection,
            onRailPreview = {},
            onRailReleased = {}
        )

        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()

        val actualRailBounds: Rect = requireNotNull(railBounds.current)
        val actualBubbleBounds: DpRect = composeRule.onNodeWithTag(SCROLL_RAIL_BUBBLE_TEST_TAG).getUnclippedBoundsInRoot()
        val actualRootBounds: DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
        with(composeRule.density) {
            val actualBubbleCenterX: Float = (actualBubbleBounds.left.toPx() + actualBubbleBounds.right.toPx()) / 2f
            val isBubbleOnRight: Boolean = actualBubbleCenterX > actualRailBounds.center.x
            assertEquals(isRailOnLeft, isBubbleOnRight)
            assertTrue(
                "Bubble must be inside the screen",
                actualBubbleBounds.left.toPx() >= actualRootBounds.left.toPx() &&
                    actualBubbleBounds.right.toPx() <= actualRootBounds.right.toPx()
            )
        }
    }

    private fun assertRailInsideScreenOnSide(railBounds: RailBounds, isOnLeft: Boolean) {
        composeRule.waitForIdle()
        val actualBounds: Rect = requireNotNull(railBounds.current)
        val actualRootBounds: DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
        with(composeRule.density) {
            val actualRootLeftPx: Float = actualRootBounds.left.toPx()
            val actualRootRightPx: Float = actualRootBounds.right.toPx()
            assertEquals(isOnLeft, actualBounds.center.x < (actualRootLeftPx + actualRootRightPx) / 2f)
            assertTrue("Rail must be inside the screen", actualBounds.left >= actualRootLeftPx && actualBounds.right <= actualRootRightPx)
        }
    }

    private fun assertMirrorStripOnSide(isOnLeft: Boolean) {
        composeRule.waitForIdle()
        val actualMirrorBounds: DpRect = composeRule.onNodeWithTag(RAIL_MIRROR_STRIP_TEST_TAG).getUnclippedBoundsInRoot()
        val actualRootBounds: DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(isOnLeft, actualMirrorBounds.centerX() < actualRootBounds.centerX())
    }

    private fun DpRect.centerX(): Float = (left.value + right.value) / 2f

    private fun dragScrubEdge(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput {
            fun markCenterY(index: Int): Float = height / MARKS.size * (index + 0.5f)
            down(position = Offset(x = center.x, y = markCenterY(index = 0)))
            moveTo(position = Offset(x = center.x, y = markCenterY(index = 1)))
            moveTo(position = Offset(x = center.x, y = markCenterY(index = 2)))
            up()
        }
    }

    private companion object {
        val MARKS: List<GroupMark> = listOf(
            GroupMark.Label(text = "A"),
            GroupMark.Label(text = "B"),
            GroupMark.Label(text = "C")
        )
    }
}
