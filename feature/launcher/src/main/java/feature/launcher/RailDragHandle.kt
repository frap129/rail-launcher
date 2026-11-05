package feature.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import core.data.prefs.RailSide
import core.ui.composables.DragHandle
import core.ui.layout.DragHandlePlacement
import core.ui.layout.dragHandleRailGap

/**
 * A drag handle placed at [placement], padded [gap] from its content edge and clear of the Rail
 * on the Start side and, when [clearsEndRail], on the End side.
 */
@Composable
internal fun RailDragHandle(
    placement: DragHandlePlacement,
    railSide: RailSide,
    contentDescription: String,
    modifier: Modifier = Modifier,
    gap: Dp = dragHandleRailGap,
    clearsEndRail: Boolean = true
) {
    DragHandle(
        modifier = modifier.padding(placement.padding(railSide = railSide, gap = gap, clearsEndRail = clearsEndRail)),
        contentDescription = contentDescription
    )
}
