package core.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.prefs.RailSide

/** The gap between the Rail's touch column and a drag handle. */
private val dragHandleRailGap: Dp = 16.dp

/** Where a drag handle sits relative to the content it reorders. */
enum class DragHandlePlacement(val isBeforeContent: Boolean) {
    BEFORE(isBeforeContent = true),
    AFTER(isBeforeContent = false);

    val centerAlignment: Alignment get() = if (isBeforeContent) Alignment.CenterStart else Alignment.CenterEnd

    val topAlignment: Alignment get() = if (isBeforeContent) Alignment.TopStart else Alignment.TopEnd
}

/** The drag handle placement on [this] Rail side. */
fun RailSide.dragHandlePlacement(): DragHandlePlacement = if (isStart) DragHandlePlacement.BEFORE else DragHandlePlacement.AFTER

/** The inset between a drag handle and its content edge that keeps it clear of the Rail's touch column on [this] side. */
fun RailSide.dragHandleInset(): Dp = railContentInset() + dragHandleRailGap

/** The padding that insets a drag handle by [inset] on [this] Rail side. */
fun RailSide.dragHandlePadding(inset: Dp = dragHandleInset()): PaddingValues = if (dragHandlePlacement().isBeforeContent) {
    PaddingValues(start = inset)
} else {
    PaddingValues(end = inset)
}

private fun RailSide.railContentInset(): Dp = if (isStart) homeContentStartInset(railSide = this) else homeContentEndInset(railSide = this)
