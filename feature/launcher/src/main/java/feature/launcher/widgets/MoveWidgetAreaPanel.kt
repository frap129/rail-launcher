package feature.launcher.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import core.ui.R as CoreUiR
import core.ui.util.performSegmentTickHaptic
import core.widgets.MoveWidgetAreaSlider
import core.widgets.R as CoreWidgetsR
import feature.launcher.R

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
    val valueLabel: String = stringResource(CoreWidgetsR.string.offset_dp, state.draftOffsetDp)
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
                onMoveUp = { onStep(MoveWidgetAreaStep.Up) },
                onMoveDown = { onStep(MoveWidgetAreaStep.Down) },
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = valueLabel, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) {
                    Text(text = stringResource(CoreUiR.string.reset))
                }
                FilledTonalButton(onClick = onDone) {
                    Text(text = stringResource(CoreUiR.string.done))
                }
            }
        }
    }
}
