package feature.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import core.data.prefs.RailSide
import core.ui.composables.DragHandle
import core.ui.layout.DragHandlePlacement

/** A drag handle placed at [placement], clear of the Rail's touch column on [railSide]. */
@Composable
internal fun RailDragHandle(placement: DragHandlePlacement, railSide: RailSide, contentDescription: String, modifier: Modifier = Modifier) {
    DragHandle(
        modifier = modifier.padding(
            start = placement.startPadding(railSide = railSide),
            end = placement.endPadding(railSide = railSide)
        ),
        contentDescription = contentDescription
    )
}
