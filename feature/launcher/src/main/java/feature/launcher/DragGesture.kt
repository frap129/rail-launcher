package feature.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** How one observed drag ended. */
internal sealed interface DragEnd {
    /** The tracked pointer lifted, after travelling the cumulative deltas at the release velocity. */
    data class Released(
        val change: PointerInputChange,
        val totalDx: Float,
        val totalDy: Float,
        val velocityX: Float,
        val velocityY: Float
    ) : DragEnd

    /** The drag was blocked or another gesture consumed it. */
    data object Canceled : DragEnd

    /** The tracked pointer disappeared from the events before it lifted. */
    data object Lost : DragEnd
}

/**
 * Observes the drag that began with [down], reporting cumulative deltas to [onEvent] for every
 * event including the release, then the outcome to [onEnd]. The observer never consumes a change,
 * so a scrolling list underneath keeps moving.
 */
internal suspend fun AwaitPointerEventScope.trackDrag(
    down: PointerInputChange,
    pass: PointerEventPass = PointerEventPass.Main,
    isBlocked: () -> Boolean = { false },
    onEvent: (change: PointerInputChange, totalDx: Float, totalDy: Float) -> Unit = { _, _, _ -> },
    onEnd: (DragEnd) -> Unit
) {
    val velocityTracker: VelocityTracker = VelocityTracker()
    velocityTracker.addPosition(down.uptimeMillis, down.position)
    var totalDx: Float = 0f
    var totalDy: Float = 0f
    while (true) {
        val event: PointerEvent = awaitPointerEvent(pass = pass)
        val change: PointerInputChange =
            event.changes.firstOrNull { pointerChange -> pointerChange.id == down.id } ?: break
        if (isBlocked() || change.isConsumed) {
            onEnd(DragEnd.Canceled)
            return
        }
        velocityTracker.addPosition(change.uptimeMillis, change.position)
        if (change.pressed) {
            val delta: Offset = change.position - change.previousPosition
            totalDx += delta.x
            totalDy += delta.y
        }
        onEvent(change, totalDx, totalDy)
        if (!change.pressed) {
            val velocity: Velocity = velocityTracker.calculateVelocity()
            onEnd(
                DragEnd.Released(
                    change = change,
                    totalDx = totalDx,
                    totalDy = totalDy,
                    velocityX = velocity.x,
                    velocityY = velocity.y
                )
            )
            return
        }
    }
    onEnd(DragEnd.Lost)
}

/** Whether [dx] dominates [dy], so the drag is a sideways gesture. */
internal fun isHorizontalDominant(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy)

/** Whether [dy] dominates [dx], so the drag is a vertical gesture. */
internal fun isVerticalDominant(dx: Float, dy: Float): Boolean = abs(dy) > abs(dx)
