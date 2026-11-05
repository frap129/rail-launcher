package feature.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import core.data.prefs.RailSide
import core.ui.composables.DragHandle
import core.ui.layout.dragHandlePadding

/** A drag handle on the [railSide] side of its content, clear of the Rail's touch column. */
@Composable
internal fun RailDragHandle(railSide: RailSide, contentDescription: String, modifier: Modifier = Modifier) {
    DragHandle(
        modifier = modifier.padding(railSide.dragHandlePadding()),
        contentDescription = contentDescription
    )
}
