package core.ui.composables.scrollrail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.data.prefs.RailSide
import core.ui.GroupMark
import core.ui.composables.GroupMarkContent
import core.ui.composables.groupMarkLargeShieldSize
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

private const val SLOPE_SCALE: Float = 0.15f

/** The width of the drawn Rail and its touch column. */
val railWidth: Dp = 64.dp

/** The test tag on the drawn Rail. */
const val SCROLL_RAIL_TEST_TAG: String = "scrollRail"

/**
 * A column of marks along the screen edge that the user can scrub through.
 * It animates bending horizontally as the user drags on it to ensure that
 * it remains visible while being touched.
 *
 * Pointer positions are supplied to [state] by [railScrubInput]; this
 * composable only draws the marks. A single animated pointer position drives
 * the bend of every mark, and an animated strength bends the Rail in and out
 * when a scrub starts and ends.
 *
 * @param state the scrub state that selects and bends the marks
 * @param items the marks shown on the Rail, top to bottom
 * @param railSide the screen side where the Rail is drawn
 */
@Composable
fun ScrollRail(state: RailScrubState, items: List<GroupMark>, modifier: Modifier = Modifier, railSide: RailSide = RailSide.DEFAULT) {
    val isOnLeft: Boolean = railSide.isOnLeft(isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl)
    Row(
        horizontalArrangement = if (railSide.isStart) Arrangement.Start else Arrangement.End,
        modifier = modifier.testTag(SCROLL_RAIL_TEST_TAG)
    ) {
        if (isOnLeft) {
            RailMarks(state = state, items = items, isOnLeft = isOnLeft, isAtLayoutStart = railSide.isStart)
            RailBubble(state = state, items = items, isOnLeft = isOnLeft)
        } else {
            RailBubble(state = state, items = items, isOnLeft = isOnLeft)
            RailMarks(state = state, items = items, isOnLeft = isOnLeft, isAtLayoutStart = railSide.isStart)
        }
    }
}

@Composable
private fun RailBubble(state: RailScrubState, items: List<GroupMark>, isOnLeft: Boolean) {
    if (state.selectedMarkIndex < 0) {
        return
    }
    val metrics: RailMetrics = state.metrics
    val theme: ColorScheme = MaterialTheme.colorScheme
    GroupMarkContent(
        mark = items[state.selectedMarkIndex],
        modifier = Modifier
            .size(metrics.highlightSize)
            .offset {
                val bubble: Offset = state.bubblePosition
                IntOffset(
                    getXOffset(state.selectedMarkIndex, metrics, Offset(bubble.x, bubble.y - state.railOffset), isOnLeft).toInt(),
                    bubble.y.toInt() - (metrics.highlightSizePx * 0.5f).toInt()
                )
            }
            .background(color = theme.primaryContainer, shape = CircleShape),
        contentAlignment = Alignment.Center,
        fontSize = 28.sp,
        lineHeight = metrics.highlightSize.value.sp,
        textAlign = TextAlign.Center,
        fillColor = theme.onPrimaryContainer,
        isOutlined = false,
        shieldSize = groupMarkLargeShieldSize
    )
}

@Composable
private fun RailMarks(state: RailScrubState, items: List<GroupMark>, isOnLeft: Boolean, isAtLayoutStart: Boolean) {
    val metrics: RailMetrics = state.metrics
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = if (isAtLayoutStart) Alignment.Start else Alignment.End,
        modifier = Modifier.offset {
            IntOffset(0, (state.railOffset * state.bendStrength).toInt())
        }
    ) {
        items.forEachIndexed { index, mark ->
            GroupMarkContent(
                mark = mark,
                contentAlignment = Alignment.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(metrics.markHeight)
                    .offset {
                        val bend: Offset = state.bendPosition
                        val strength: Float = state.bendStrength
                        IntOffset(
                            (getXOffset(index, metrics, Offset(bend.x, bend.y - state.railOffset), isOnLeft) * strength).roundToInt(),
                            0
                        )
                    }
            )
        }
    }
}

private fun getXOffset(index: Int, metrics: RailMetrics, pointer: Offset, isOnLeft: Boolean): Float {
    val mirroredPointerX: Float = if (isOnLeft) metrics.railWidthPx - pointer.x else pointer.x
    val offset: Float = -gaussianCurve(
        index * metrics.markHeightPx,
        pointer.y,
        metrics.minPeakPx + abs(mirroredPointerX * SLOPE_SCALE),
        metrics.minPeakPx - mirroredPointerX
    )
    return if (isOnLeft) -offset else offset
}

private fun gaussianCurve(pos: Float, peakPos: Float, slope: Float, peak: Float): Float =
    peak * exp(-((pos - peakPos).pow(2) / (slope.pow(2))))
