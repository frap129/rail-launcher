package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import core.ui.composables.scrollrail.SCROLL_RAIL_TEST_TAG
import org.junit.Assert.assertEquals
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

        assertRailOnSide(railBounds = railBounds, isStart = false)
        assertMirrorStripOnSide(isStart = true)
    }

    @Test
    fun `keeps the Rail at the layout end in a right-to-left layout`() {
        val railBounds: RailBounds = setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            layoutDirection = LayoutDirection.Rtl,
            onRailPreview = {},
            onRailReleased = {}
        )

        assertRailOnSide(railBounds = railBounds, isStart = true)
        assertMirrorStripOnSide(isStart = false)
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

        assertRailOnSide(railBounds = railBounds, isStart = true)
        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
    }

    @Test
    fun `Start Rail side scrubs from the drawn Rail and the mirror strip on the end edge`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            railSide = RailSide.START,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )
        assertMirrorStripOnSide(isStart = false)

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
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
    fun `scrubbing the drawn Rail does nothing while disabled`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            enabled = false,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)

        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    @Test
    fun `Both scrub edges preview and release from the drawn Rail and the mirror strip`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.BOTH,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = SCROLL_RAIL_TEST_TAG)
        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(listOf(0, 1, 2, 0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2, 2), actualReleaseIndices)
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
    fun `scrubbing the mirror strip does nothing while disabled`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            scrubEdges = ScrubEdges.START,
            enabled = false,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragScrubEdge(tag = RAIL_MIRROR_STRIP_TEST_TAG)

        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    private fun setRailContent(
        scrubEdges: ScrubEdges,
        enabled: Boolean = true,
        railSide: RailSide = RailSide.DEFAULT,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        isCardOpen: () -> Boolean = { false },
        onRailTouched: () -> Unit = {},
        onRailPreview: (Int) -> Unit,
        onRailReleased: (Int) -> Unit
    ): RailBounds {
        val railBounds: RailBounds = RailBounds()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
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
                        enabled = enabled
                    )
                }
            }
        }
        return railBounds
    }

    private fun assertRailOnSide(railBounds: RailBounds, isStart: Boolean) {
        composeRule.waitForIdle()
        val actualBounds: Rect = requireNotNull(railBounds.current)
        val actualRootWidth: Float = with(composeRule.density) {
            val rootBounds: DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
            (rootBounds.right - rootBounds.left).toPx()
        }
        assertEquals(isStart, actualBounds.center.x < actualRootWidth / 2f)
    }

    private fun assertMirrorStripOnSide(isStart: Boolean) {
        composeRule.waitForIdle()
        val actualMirrorBounds: DpRect = composeRule.onNodeWithTag(RAIL_MIRROR_STRIP_TEST_TAG).getUnclippedBoundsInRoot()
        val actualRootBounds: DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
        val actualMirrorCenterX: Float = (actualMirrorBounds.left.value + actualMirrorBounds.right.value) / 2f
        val actualRootCenterX: Float = (actualRootBounds.left.value + actualRootBounds.right.value) / 2f
        assertEquals(isStart, actualMirrorCenterX < actualRootCenterX)
    }

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
