package core.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import core.ui.layout.minTouchTargetSize

private val dragHandleDotRadius = 3.dp
private val dragHandleDotSpacing = 12.dp
private val dragHandleOutlineWidth = 2.dp

/** A four-dot grip outlined like Rail text so it reads on any wallpaper. */
@Composable
fun DragHandle(modifier: Modifier = Modifier, contentDescription: String) {
    val fillColor: Color = MaterialTheme.colorScheme.onSurface
    val outlineColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(
        modifier = modifier
            .size(minTouchTargetSize)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val dotRadius: Float = dragHandleDotRadius.toPx()
        val dotSpacing: Float = dragHandleDotSpacing.toPx()
        val outline: Stroke = Stroke(width = dragHandleOutlineWidth.toPx())
        listOf(-1f, 1f).forEach { column ->
            listOf(-1f, 1f).forEach { row ->
                val dotCenter: Offset = center + Offset(column * dotSpacing / 2, row * dotSpacing / 2)
                drawCircle(color = outlineColor, radius = dotRadius, center = dotCenter, style = outline)
                drawCircle(color = fillColor, radius = dotRadius, center = dotCenter)
            }
        }
    }
}
