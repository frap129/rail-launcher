package core.ui.composables.scrollrail

import android.view.MotionEvent
import androidx.compose.animation.core.animateIntOffsetAsState
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.launch

/**
 * A composable that creates a column of single character items that
 * behaves like a scrollbar. It animates bending horizontally as the
 * user scrolls on it to ensure that it remains visible while being
 * touched
 *
 * @param modifier the modifier applied to the column
 * @param scrollRailHelper an object of  [core.ui.composables.scrollrail.ScrollRailHelper]
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrollRail(modifier: Modifier = Modifier, scrollRailHelper: ScrollRailHelper) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val disallowIntercept = remember { RequestDisallowInterceptTouchEvent() }
    val hapticFeedback = LocalHapticFeedback.current
    val density = context.resources.displayMetrics.density
    val itemHeight = 20.dp
    val railHeight = itemHeight.value * density * scrollRailHelper.railItems.size
    val highlightSize = 42.dp
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer

    var selectedItemIndex by remember { mutableIntStateOf(-1) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    var railOffset by remember { mutableFloatStateOf(0f) }

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .pointerInteropFilter(disallowIntercept) { event ->
                verticalOffset = event.y
                horizontalOffset = event.x
                if (verticalOffset < railOffset) {
                    railOffset = verticalOffset
                } else if (verticalOffset > railOffset + railHeight) {
                    railOffset = verticalOffset - railHeight
                }

                val calculatedIndex = ((verticalOffset - railOffset) / (itemHeight.value * density)).toInt()
                val itemIndex = max(min(calculatedIndex, scrollRailHelper.railItems.size - 1), 0)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { scrollRailHelper.onScrollStarted(itemIndex) }
                        selectedItemIndex = itemIndex
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (itemIndex != selectedItemIndex) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { scrollRailHelper.onScroll(itemIndex, selectedItemIndex) }
                            selectedItemIndex = itemIndex
                        }
                    }

                    else -> {
                        scope.launch { scrollRailHelper.onScrollEnded(selectedItemIndex) }
                        selectedItemIndex = -1
                        verticalOffset = 0f
                        horizontalOffset = 0f
                        railOffset = 0f
                    }
                }

                disallowIntercept.invoke(true)
                true
            }
    ) {
        if (selectedItemIndex >= 0) {
            val offset by animateIntOffsetAsState(
                targetValue = if (selectedItemIndex < 0) {
                    IntOffset(0, 0)
                } else {
                    IntOffset(
                        getXOffset(selectedItemIndex, itemHeight, density, verticalOffset - railOffset, horizontalOffset).toInt(),
                        verticalOffset.toInt() - (highlightSize.value * density * 0.5f).toInt()
                    )
                }
            )

            Text(
                text = "${scrollRailHelper.railItems[selectedItemIndex]}",
                fontSize = 28.sp,
                lineHeight = highlightSize.value.sp,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                modifier = Modifier
                    .size(highlightSize)
                    .offset { offset }
                    .background(color = highlightColor, shape = CircleShape)
            )
        }

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.End,
            modifier = Modifier.offset {
                IntOffset(0, railOffset.toInt())
            }
        ) {
            scrollRailHelper.railItems.forEachIndexed { index, label ->
                val offset by animateIntOffsetAsState(
                    targetValue = if (selectedItemIndex < 0) {
                        IntOffset(0, 0)
                    } else {
                        IntOffset(
                            getXOffset(index, itemHeight, density, verticalOffset - railOffset, horizontalOffset).toInt(),
                            0
                        )
                    }
                )

                Text(
                    text = "$label",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                    modifier = Modifier
                        .size(itemHeight)
                        .offset { offset }
                )
            }
        }
    }
}

fun getXOffset(index: Int, itemHeight: Dp, density: Float, verticalOffset: Float, horizontalOffset: Float): Float {
    val slopeScale = 0.15f
    val minPeak = 150
    return -gaussianCurve(
        index * itemHeight.value * density,
        verticalOffset,
        (minPeak * density) + abs(horizontalOffset * slopeScale),
        (minPeak * density) - horizontalOffset
    )
}

fun gaussianCurve(pos: Float, peakPos: Float, slope: Float, peak: Float): Float = peak * exp(-((pos - peakPos).pow(2) / (slope.pow(2))))
