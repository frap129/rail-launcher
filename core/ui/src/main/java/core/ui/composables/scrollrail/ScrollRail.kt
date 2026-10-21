package core.ui.composables.scrollrail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.composables.OutlinedText
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val BEND_STRENGTH_RESTING = 0f
private const val BEND_STRENGTH_ACTIVE = 1f
private const val MIN_PEAK_DP = 150f
private const val SLOPE_SCALE = 0.15f
private val bendSpring: SpringSpec<Offset> = spring(stiffness = Spring.StiffnessMediumLow)
private val bendStrengthSpring: SpringSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)
private val bubbleSpring: SpringSpec<Offset> = spring()

private data class RailMetrics(val itemHeightPx: Float, val minPeakPx: Float, val highlightSizePx: Float)

private class RailPointerAnimation {
    private val bendAnimation = Animatable(Offset.Zero, Offset.VectorConverter)
    private val bendStrengthAnimation = Animatable(BEND_STRENGTH_RESTING, Float.VectorConverter)
    private val bubbleAnimation = Animatable(Offset.Zero, Offset.VectorConverter)
    val bendPosition: Offset get() = bendAnimation.value
    val bendStrength: Float get() = bendStrengthAnimation.value
    val bubblePosition: Offset get() = bubbleAnimation.value

    suspend fun start(at: Offset) {
        bendAnimation.snapTo(at)
        bubbleAnimation.snapTo(at)
        bendStrengthAnimation.animateTo(BEND_STRENGTH_ACTIVE, bendStrengthSpring)
    }

    suspend fun follow(at: Offset) {
        coroutineScope {
            launch { bendAnimation.animateTo(at, bendSpring) }
            launch { bubbleAnimation.animateTo(at, bubbleSpring) }
        }
    }

    suspend fun stop() {
        bendStrengthAnimation.animateTo(BEND_STRENGTH_RESTING, bendStrengthSpring)
    }
}

/**
 * A column of single character items along the screen edge that the
 * user can scrub through. It animates bending horizontally as the
 * user drags on it to ensure that it remains visible while being
 * touched.
 *
 * Pointer positions are only read during layout, so scrubbing does not
 * recompose the Rail. A single animated pointer position drives the bend
 * of every item, and an animated strength bends the Rail in and out when
 * a scrub starts and ends.
 *
 * @param modifier the modifier applied to the row
 * @param items the labels shown on the Rail, top to bottom
 * @param onScroll invoked when a scroll starts or the pointer crosses into another item
 * @param onScrollEnded invoked with the item under the pointer when the scroll ends
 */
@Composable
fun ScrollRail(
    modifier: Modifier = Modifier,
    items: List<String>,
    onScroll: (Int) -> Unit,
    onScrollEnded: (Int) -> Unit,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = context.resources.displayMetrics.density
    val itemHeight = 20.dp
    val highlightSize = 42.dp
    val railMetrics = remember(density) {
        RailMetrics(
            itemHeightPx = itemHeight.value * density,
            minPeakPx = MIN_PEAK_DP * density,
            highlightSizePx = highlightSize.value * density
        )
    }
    val railHeight = railMetrics.itemHeightPx * items.size
    var selectedItemIndex by remember { mutableIntStateOf(-1) }
    var railOffset by remember { mutableFloatStateOf(0f) }
    val railPointer = remember { RailPointerAnimation() }
    val theme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .testTag("scrollRail")
            .pointerInput(items.size, enabled) {
                if (!enabled) {
                    return@pointerInput
                }
                fun startScroll(pointer: Offset, itemIndex: Int) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScroll(itemIndex)
                    selectedItemIndex = itemIndex
                    scope.launch { railPointer.start(pointer) }
                }

                fun moveScroll(pointer: Offset, itemIndex: Int) {
                    scope.launch { railPointer.follow(pointer) }
                    if (itemIndex != selectedItemIndex) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onScroll(itemIndex)
                        selectedItemIndex = itemIndex
                    }
                }

                fun endScroll(itemIndex: Int) {
                    onScrollEnded(itemIndex)
                    selectedItemIndex = -1
                    scope.launch { railPointer.stop() }
                }

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.first().position
                        if (event.type == PointerEventType.Press) {
                            railOffset = 0f
                        }
                        railOffset = clampRailOffset(pointer.y, railOffset, railHeight)
                        val itemIndex = railItemIndex(pointer.y - railOffset, railMetrics.itemHeightPx, items.size)
                        when (event.type) {
                            PointerEventType.Press -> startScroll(pointer, itemIndex)
                            PointerEventType.Move -> moveScroll(pointer, itemIndex)
                            PointerEventType.Unknown, PointerEventType.Release -> endScroll(itemIndex)
                        }
                    }
                }
            }
    ) {
        if (selectedItemIndex >= 0) {
            Text(
                text = items[selectedItemIndex],
                fontSize = 28.sp,
                color = theme.onPrimaryContainer,
                lineHeight = highlightSize.value.sp,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                modifier = Modifier
                    .size(highlightSize)
                    .offset {
                        val bubble = railPointer.bubblePosition
                        IntOffset(
                            getXOffset(selectedItemIndex, railMetrics, Offset(bubble.x, bubble.y - railOffset)).toInt(),
                            bubble.y.toInt() - (railMetrics.highlightSizePx * 0.5f).toInt()
                        )
                    }
                    .background(color = theme.primaryContainer, shape = CircleShape)
            )
        }
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.End,
            modifier = Modifier.offset {
                IntOffset(0, (railOffset * railPointer.bendStrength).toInt())
            }
        ) {
            items.forEachIndexed { index, label ->
                OutlinedText(
                    text = label,
                    contentAlignment = Alignment.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .size(itemHeight)
                        .offset {
                            val bend = railPointer.bendPosition
                            val strength = railPointer.bendStrength
                            IntOffset(
                                (getXOffset(index, railMetrics, Offset(bend.x, bend.y - railOffset)) * strength).roundToInt(),
                                0
                            )
                        }
                )
            }
        }
    }
}

private fun clampRailOffset(pointerY: Float, railOffset: Float, railHeight: Float): Float = when {
    pointerY < railOffset -> pointerY
    pointerY > railOffset + railHeight -> pointerY - railHeight
    else -> railOffset
}

private fun railItemIndex(verticalOffset: Float, itemHeightPx: Float, itemCount: Int): Int = if (itemCount == 0) {
    0
} else {
    (verticalOffset / itemHeightPx).toInt().coerceIn(0, itemCount - 1)
}

private fun getXOffset(index: Int, metrics: RailMetrics, pointer: Offset): Float = -gaussianCurve(
    index * metrics.itemHeightPx,
    pointer.y,
    metrics.minPeakPx + abs(pointer.x * SLOPE_SCALE),
    metrics.minPeakPx - pointer.x
)

private fun gaussianCurve(pos: Float, peakPos: Float, slope: Float, peak: Float): Float =
    peak * exp(-((pos - peakPos).pow(2) / (slope.pow(2))))
