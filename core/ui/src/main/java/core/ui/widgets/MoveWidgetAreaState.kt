package core.ui.widgets

/** One step of the Move Widget Area adjuster. */
enum class MoveWidgetAreaStep {
    Up,
    Down
}

/** The live Move Widget Area adjuster state. */
data class MoveWidgetAreaState(val draftOffsetDp: Int, val maxOffsetDp: Int, val canMoveUp: Boolean, val canMoveDown: Boolean)
