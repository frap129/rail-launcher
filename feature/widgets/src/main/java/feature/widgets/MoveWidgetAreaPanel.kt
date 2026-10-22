package feature.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowDown
import compose.icons.fontawesomeicons.solid.ArrowUp
import core.data.widgets.WidgetAreaSettings
import core.ui.util.performSegmentTickHaptic
import kotlin.math.roundToInt

/** The non-modal bottom panel that moves the Widget Area down while Home stays visible. */
@Composable
fun MoveWidgetAreaPanel(
    state: MoveWidgetAreaState,
    onDraftChange: (Int) -> Unit,
    onStep: (MoveWidgetAreaStep) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback: HapticFeedback = LocalHapticFeedback.current
    val valueLabel: String = stringResource(R.string.offset_dp, state.draftOffsetDp)
    var lastOffsetDp: Int by remember { mutableIntStateOf(state.draftOffsetDp) }
    LaunchedEffect(state.draftOffsetDp) {
        if (state.draftOffsetDp != lastOffsetDp) {
            hapticFeedback.performSegmentTickHaptic()
            lastOffsetDp = state.draftOffsetDp
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.move_widget_area),
                style = MaterialTheme.typography.titleLarge
            )
            MoveWidgetAreaSlider(
                offsetDp = state.draftOffsetDp,
                maxOffsetDp = state.maxOffsetDp,
                canMoveUp = state.canMoveUp,
                canMoveDown = state.canMoveDown,
                onOffsetChange = onDraftChange,
                onStep = onStep,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = valueLabel, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) {
                    Text(text = stringResource(R.string.reset))
                }
                FilledTonalButton(onClick = onDone) {
                    Text(text = stringResource(R.string.done))
                }
            }
        }
    }
}

/** The offset slider and step arrows shared by the Home panel and the Settings dialog. */
@Composable
fun MoveWidgetAreaSlider(
    offsetDp: Int,
    maxOffsetDp: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOffsetChange: (Int) -> Unit,
    onStep: (MoveWidgetAreaStep) -> Unit,
    modifier: Modifier = Modifier
) {
    val valueLabel: String = stringResource(R.string.offset_dp, offsetDp)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onStep(MoveWidgetAreaStep.Up) },
            enabled = canMoveUp,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.ArrowUp,
                contentDescription = stringResource(R.string.move_up)
            )
        }
        Slider(
            value = offsetDp.toFloat(),
            onValueChange = { value -> onOffsetChange(value.roundToInt()) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics { stateDescription = valueLabel },
            valueRange = 0f..maxOffsetDp.coerceAtLeast(1).toFloat(),
            steps = ((maxOffsetDp / WidgetAreaSettings.OFFSET_STEP_DP) - 1).coerceAtLeast(0),
            enabled = maxOffsetDp > 0
        )
        IconButton(
            onClick = { onStep(MoveWidgetAreaStep.Down) },
            enabled = canMoveDown,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.ArrowDown,
                contentDescription = stringResource(R.string.move_down)
            )
        }
    }
}
