package feature.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Minus
import compose.icons.fontawesomeicons.solid.Plus

/** Dimming applied to Home content that is inert while one place is resized. */
const val RESIZE_DIMMED_ALPHA: Float = 0.38f

/** The outlined place, bottom-edge handle and toolbar shown while one place is being resized. */
@Composable
fun ResizeOverlay(
    state: ResizeState,
    onDrag: (Float) -> Unit,
    onStep: (ResizeStep) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    toolbarAbove: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (toolbarAbove) {
            ResizeToolbar(
                state = state,
                onStep = onStep,
                onReset = onReset,
                onDone = onDone,
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                content()
            }
            ResizeHandle(
                state = state,
                onDrag = onDrag,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 24.dp)
            )
        }
        if (!toolbarAbove) {
            ResizeToolbar(
                state = state,
                onStep = onStep,
                onReset = onReset,
                onDone = onDone,
                modifier = Modifier.padding(top = 28.dp)
            )
        }
    }
}

@Composable
private fun ResizeHandle(state: ResizeState, onDrag: (Float) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val handleDescription: String = stringResource(R.string.resize_handle)
    val atBound: Boolean = !state.canDecrease || !state.canIncrease
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
            .semantics { contentDescription = handleDescription }
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

@Composable
private fun ResizeToolbar(
    state: ResizeState,
    onStep: (ResizeStep) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onStep(ResizeStep.Decrease) },
                enabled = state.canDecrease,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Minus,
                    contentDescription = stringResource(R.string.resize_decrease_height)
                )
            }
            Text(
                text = stringResource(R.string.resize_height, state.draftHeightDp),
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(
                onClick = { onStep(ResizeStep.Increase) },
                enabled = state.canIncrease,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Plus,
                    contentDescription = stringResource(R.string.resize_increase_height)
                )
            }
            TextButton(
                onClick = onReset,
                modifier = Modifier.width(50.dp).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(text = stringResource(R.string.resize_reset), style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = onDone,
                modifier = Modifier.width(54.dp).heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(text = stringResource(R.string.resize_done), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
