package core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide

/** The gap between the Rail's touch column and a drag handle. */
val dragHandleRailGap: Dp = 16.dp

/** Where a drag handle sits relative to the content it reorders, and how it stays clear of the Rail. */
enum class DragHandlePlacement(val isBeforeContent: Boolean) {
    BEFORE(isBeforeContent = true),
    AFTER(isBeforeContent = false);

    val centerAlignment: Alignment get() = if (isBeforeContent) Alignment.CenterStart else Alignment.CenterEnd

    val topAlignment: Alignment get() = if (isBeforeContent) Alignment.TopStart else Alignment.TopEnd

    /** The start padding that keeps this placement clear of the Rail on [railSide]. */
    fun startPadding(railSide: RailSide): Dp =
        if (isBeforeContent) sidePadding(railSide = railSide, gap = dragHandleRailGap, clearsEndRail = true) else 0.dp

    /**
     * The padding that places this handle [gap] from its content edge, adding the Rail's clearance
     * on the Start side and, when [clearsEndRail], on the End side.
     */
    fun padding(railSide: RailSide, gap: Dp = dragHandleRailGap, clearsEndRail: Boolean = true): PaddingValues {
        val handlePadding: Dp = sidePadding(railSide = railSide, gap = gap, clearsEndRail = clearsEndRail)
        return if (isBeforeContent) PaddingValues(start = handlePadding) else PaddingValues(end = handlePadding)
    }

    private fun sidePadding(railSide: RailSide, gap: Dp, clearsEndRail: Boolean): Dp {
        val railClearance: Dp = when {
            railSide.isStart -> homeContentStartInset(railSide = railSide)
            clearsEndRail -> homeContentEndInset(railSide = railSide)
            else -> 0.dp
        }
        return railClearance + gap
    }
}

/** The drag handle placement on [this] Rail side. */
fun RailSide.dragHandlePlacement(): DragHandlePlacement = if (isStart) DragHandlePlacement.BEFORE else DragHandlePlacement.AFTER
