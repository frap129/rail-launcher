package feature.launcher.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import core.data.widgets.ResizeBounds
import feature.launcher.R

/** Dimming applied to Home content that is inert while one place is resized. */
const val RESIZE_DIMMED_ALPHA: Float = 0.38f

/** The outlined place and bottom drag handle shown while one place is being resized. */
@Composable
fun ResizeOverlay(
    state: ResizeState,
    onDrag: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        content()
        ResizeHandle(
            state = state,
            onDrag = onDrag,
            onReset = onReset,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ResizeHandle(state: ResizeState, onDrag: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val handleDescription: String = stringResource(R.string.resize_handle)
    val heightDescription: String = stringResource(R.string.resize_height, state.draftHeightDp)
    val decreaseLabel: String = stringResource(R.string.resize_decrease_height)
    val increaseLabel: String = stringResource(R.string.resize_increase_height)
    val resetLabel: String = stringResource(R.string.resize_reset)
    val atBound: Boolean = state.draftHeightDp <= state.bounds.minDp ||
        state.bounds.maxDp?.let { maxDp -> state.draftHeightDp >= maxDp } == true
    var lastHeightDp: Int by remember { mutableIntStateOf(state.draftHeightDp) }
    var lastAtBound: Boolean by remember { mutableStateOf(atBound) }
    LaunchedEffect(state.draftHeightDp) {
        if (state.draftHeightDp != lastHeightDp) {
            val type: HapticFeedbackType = if (atBound && !lastAtBound) {
                HapticFeedbackType.Reject
            } else {
                HapticFeedbackType.SegmentTick
            }
            hapticFeedback.performHapticFeedback(type)
            lastHeightDp = state.draftHeightDp
            lastAtBound = atBound
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = handleDescription
                stateDescription = heightDescription
                customActions = listOf(
                    CustomAccessibilityAction(label = decreaseLabel) {
                        onDrag(-ResizeBounds.STEP_DP.toFloat())
                        true
                    },
                    CustomAccessibilityAction(label = increaseLabel) {
                        onDrag(ResizeBounds.STEP_DP.toFloat())
                        true
                    },
                    CustomAccessibilityAction(label = resetLabel) {
                        onReset()
                        true
                    }
                )
            }
            .pointerInput(state.placeId, onReset) {
                detectTapGestures(
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReset()
                    }
                )
            }
            .pointerInput(state.placeId) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(with(density) { dragAmount.y.toDp().value })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = 24.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(color = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(2.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}
