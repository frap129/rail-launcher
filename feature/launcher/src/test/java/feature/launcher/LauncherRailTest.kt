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
    fun `scrubbing the drawn Rail previews and releases the mark under the pointer`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            enabled = true,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragRail()

        assertEquals(listOf(0, 1, 2), actualPreviewIndices)
        assertEquals(listOf(2), actualReleaseIndices)
    }

    @Test
    fun `scrubbing the drawn Rail does nothing while disabled`() {
        val actualPreviewIndices: MutableList<Int> = mutableListOf()
        val actualReleaseIndices: MutableList<Int> = mutableListOf()
        setRailContent(
            enabled = false,
            onRailPreview = { index -> actualPreviewIndices += index },
            onRailReleased = { index -> actualReleaseIndices += index }
        )

        dragRail()

        assertEquals(emptyList<Int>(), actualPreviewIndices)
        assertEquals(emptyList<Int>(), actualReleaseIndices)
    }

    private fun setRailContent(enabled: Boolean, onRailPreview: (Int) -> Unit, onRailReleased: (Int) -> Unit) {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                LauncherRail(
                    railItems = MARKS,
                    railBounds = remember { RailBounds() },
                    isCardOpen = { false },
                    onRailTouched = {},
                    onRailPreview = onRailPreview,
                    onRailReleased = onRailReleased,
                    enabled = enabled
                )
            }
        }
    }

    private fun dragRail() {
        composeRule.onNodeWithTag(SCROLL_RAIL_TEST_TAG).performTouchInput {
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
