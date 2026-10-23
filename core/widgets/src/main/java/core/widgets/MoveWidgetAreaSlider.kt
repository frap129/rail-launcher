package core.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowDown
import compose.icons.fontawesomeicons.solid.ArrowUp
import core.data.widgets.WidgetAreaSettings
import kotlin.math.roundToInt

/** The offset slider and step arrows shared by the Home panel and the Settings dialog. */
@Composable
fun MoveWidgetAreaSlider(
    offsetDp: Int,
    maxOffsetDp: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOffsetChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val valueLabel: String = stringResource(R.string.offset_dp, offsetDp)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp
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
            onClick = onMoveDown,
            enabled = canMoveDown
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.ArrowDown,
                contentDescription = stringResource(R.string.move_down)
            )
        }
    }
}
