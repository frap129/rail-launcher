package core.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val eventDotSize = 8.dp

/** The coloured dot that marks an event in the Header and the agenda. */
@Composable
fun EventDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(eventDotSize)
            .background(color = color, shape = CircleShape)
    )
}
