package core.ui.composables.scrollrail

import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
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
    var selectedItemIndex by remember { mutableIntStateOf(-1) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val disallowIntercept = remember { RequestDisallowInterceptTouchEvent() }
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .pointerInteropFilter(disallowIntercept) { event ->
                verticalOffset = event.y
                horizontalOffset = event.x

                scope.launch {
                    val calculatedIndex = (verticalOffset / (20 * context.resources.displayMetrics.density)).toInt()
                    val itemIndex = max(min(calculatedIndex, scrollRailHelper.railItems.size - 1), 0)

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scrollRailHelper.onScrollStarted(itemIndex)
                            selectedItemIndex = itemIndex
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (itemIndex != selectedItemIndex) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scrollRailHelper.onScroll(itemIndex, selectedItemIndex)
                                selectedItemIndex = itemIndex
                            }
                        }

                        else -> {
                            scrollRailHelper.onScrollEnded(selectedItemIndex)
                            selectedItemIndex = -1
                            verticalOffset = 0f
                            horizontalOffset = 0f
                        }
                    }
                }

                disallowIntercept.invoke(true)
                true
            }
    ) {
        val slopeScale = 0.25f
        val itemHeightDp = 20.dp
        val itemHeightPx = itemHeightDp.value * context.resources.displayMetrics.density
        val minPeak = 150.dp.value * context.resources.displayMetrics.density
        scrollRailHelper.railItems.forEachIndexed { index, label ->
            val position = index * itemHeightPx
            Text(
                text = "$label",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(textMotion = TextMotion.Animated),
                modifier = Modifier
                    .size(itemHeightDp)
                    .offset {
                        if (selectedItemIndex < 0) {
                            IntOffset(0, 0)
                        } else {
                            // Calculate offset for bending animation
                            IntOffset(
                                -gaussianCurve(
                                    position,
                                    verticalOffset,
                                    minPeak + abs(horizontalOffset * slopeScale),
                                    minPeak - horizontalOffset
                                ).toInt(),
                                0
                            )
                        }
                    }
            )
        }
    }
}

fun gaussianCurve(pos: Float, peakPos: Float, slope: Float, peak: Float): Float = peak * exp(-((pos - peakPos).pow(2) / (slope.pow(2))))
