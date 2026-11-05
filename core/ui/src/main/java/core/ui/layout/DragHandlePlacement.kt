package core.ui.layout

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide

/** The gap between the Rail's touch column and a drag handle. */
private val dragHandleRailGap: Dp = 16.dp

/** Where a drag handle sits relative to the content it reorders, and how it stays clear of the Rail. */
enum class DragHandlePlacement(val isBeforeContent: Boolean) {
    BEFORE(isBeforeContent = true),
    AFTER(isBeforeContent = false);

    val centerAlignment: Alignment get() = if (isBeforeContent) Alignment.CenterStart else Alignment.CenterEnd

    val topAlignment: Alignment get() = if (isBeforeContent) Alignment.TopStart else Alignment.TopEnd

    /** The start padding that keeps this placement clear of the Rail on [railSide]. */
    fun startPadding(railSide: RailSide): Dp = if (isBeforeContent) homeContentStartInset(railSide = railSide) + dragHandleRailGap else 0.dp

    /** The end padding that keeps this placement clear of the Rail on [railSide]. */
    fun endPadding(railSide: RailSide): Dp = if (isBeforeContent) 0.dp else homeContentEndInset(railSide = railSide) + dragHandleRailGap
}

/** The drag handle placement on [this] Rail side. */
fun RailSide.dragHandlePlacement(): DragHandlePlacement = if (isStart) DragHandlePlacement.BEFORE else DragHandlePlacement.AFTER
