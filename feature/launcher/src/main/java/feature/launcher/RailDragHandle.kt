package feature.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import core.data.prefs.RailSide
import core.ui.composables.DragHandle
import core.ui.layout.dragHandleInset
import core.ui.layout.dragHandlePadding

/** A drag handle on the [railSide] side of its content, [inset] from its content edge. */
@Composable
internal fun RailDragHandle(
    railSide: RailSide,
    contentDescription: String,
    modifier: Modifier = Modifier,
    inset: Dp = railSide.dragHandleInset()
) {
    DragHandle(
        modifier = modifier.padding(railSide.dragHandlePadding(inset = inset)),
        contentDescription = contentDescription
    )
}
