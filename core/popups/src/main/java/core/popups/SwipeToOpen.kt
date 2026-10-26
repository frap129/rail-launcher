package core.popups

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.min

private const val VELOCITY_THRESHOLD_DP_PER_SECOND: Float = 1000f

/** How the current touch relates to the Pop-up swipe gesture. */
enum class SwipeClaim {
    /** The drag has not passed touch slop yet. */
    Pending,

    /** The drag is a Pop-up swipe and owns the gesture. */
    Claimed,

    /** The drag is not a Pop-up swipe, so the row keeps its normal gestures. */
    Rejected
}

/** The pure thresholds, offsets and Rail bounds behind the Pop-up row swipe. */
object SwipeToOpen {
    const val OPEN_THRESHOLD_DP: Float = 48f
    const val MAX_OFFSET_DP: Float = 72f

    /** The offset the row content shows while [dxPx] toward the Rail has been dragged. */
    fun offsetFor(dxPx: Float, density: Float): Float {
        val dxDp: Float = abs(dxPx) / density
        val offsetDp: Float = if (dxDp <= OPEN_THRESHOLD_DP) {
            dxDp
        } else {
            OPEN_THRESHOLD_DP + (dxDp - OPEN_THRESHOLD_DP) / 2f
        }
        return min(offsetDp, MAX_OFFSET_DP) * density
    }

    /**
     * Classifies a cumulative drag. Only a horizontal-dominant drag toward the
     * Rail side is claimable.
     */
    fun onDrag(dx: Float, dy: Float, isRtl: Boolean, slopPx: Float): SwipeClaim {
        val directionalDistance: Float = directionalDx(dx = dx, isRtl = isRtl)
        if (abs(dx) <= slopPx) {
            return SwipeClaim.Pending
        }
        if (directionalDistance <= 0f) {
            return SwipeClaim.Rejected
        }
        return if (directionalDistance > abs(dy)) SwipeClaim.Claimed else SwipeClaim.Rejected
    }

    /** Whether releasing a claimed drag opens the Pop-up. */
    fun onRelease(dx: Float, velocityX: Float, isRtl: Boolean, thresholdPx: Float, velocityThresholdPx: Float): Boolean {
        val directionalDistance: Float = directionalDx(dx = dx, isRtl = isRtl)
        val directionalVelocity: Float = directionalDx(dx = velocityX, isRtl = isRtl)
        return directionalDistance >= thresholdPx ||
            (directionalDistance > 0f && directionalVelocity >= velocityThresholdPx)
    }

    /** Whether a row gesture has entered the Rail's [railBounds], staying reached once it has. */
    fun hasReachedRail(wasReached: Boolean, fingerInRoot: Offset, railBounds: Rect?): Boolean =
        wasReached || (railBounds?.contains(fingerInRoot) == true)

    /** The release distance threshold in pixels. */
    fun thresholdPx(density: Float): Float = OPEN_THRESHOLD_DP * density

    /** The release velocity threshold in pixels per second. */
    fun velocityThresholdPx(density: Float): Float = VELOCITY_THRESHOLD_DP_PER_SECOND * density
}

internal fun directionalDx(dx: Float, isRtl: Boolean): Float = if (isRtl) -dx else dx
