package feature.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
        isCardOpen: () -> Boolean = { false },
        onRailTouched: () -> Unit = {},
        onRailPreview: (Int) -> Unit,
        onRailReleased: (Int) -> Unit
    ) {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                LauncherRail(
                    railItems = MARKS,
                    railBounds = remember { RailBounds() },
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
