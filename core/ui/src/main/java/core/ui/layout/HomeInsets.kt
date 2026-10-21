package core.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The shared start, top and bottom inset for Home content. */
val homeEdgePadding: Dp = 32.dp

/** The end inset that keeps Home content clear of the Rail's touch column (64dp Rail plus the -16dp offset). */
val homeRailTouchInset: Dp = 80.dp

/** The gap between the Rail's touch column and a drag handle. */
val dragHandleRailGap: Dp = 16.dp

/** The end inset that places a 48dp drag handle inside content padded by [homeEdgePadding] clear of the Rail. */
val dragHandleEndPadding: Dp = homeRailTouchInset - homeEdgePadding + dragHandleRailGap
