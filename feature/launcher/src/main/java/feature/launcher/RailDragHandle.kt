package feature.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import core.data.prefs.RailSide
import core.ui.composables.DragHandle
import core.ui.layout.dragHandlePadding

/** A drag handle kept clear of the Rail's touch column on [railSide]. */
@Composable
internal fun RailDragHandle(railSide: RailSide, contentDescription: String, modifier: Modifier = Modifier) {
    DragHandle(
        modifier = modifier.dragHandlePadding(railSide = railSide),
        contentDescription = contentDescription
    )
}
