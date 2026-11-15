package feature.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import core.data.prefs.RailSide
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

/** The pure thresholds, offsets and gestures behind the Pop-up row swipe and the Home swipes. */
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
    fun onDrag(dx: Float, dy: Float, isRtl: Boolean, railSide: RailSide, slopPx: Float): SwipeClaim {
        val towardRailDistance: Float = towardRailDx(dx = dx, isRtl = isRtl, railSide = railSide)
        if (abs(dx) <= slopPx) {
            return SwipeClaim.Pending
        }
        if (towardRailDistance <= 0f) {
            return SwipeClaim.Rejected
        }
        return if (towardRailDistance > abs(dy)) SwipeClaim.Claimed else SwipeClaim.Rejected
    }

    /** Whether releasing a claimed drag opens the Pop-up. */
    fun onRelease(
        dx: Float,
        velocityX: Float,
        isRtl: Boolean,
        railSide: RailSide,
        thresholdPx: Float,
        velocityThresholdPx: Float
    ): Boolean {
        val towardRailDistance: Float = towardRailDx(dx = dx, isRtl = isRtl, railSide = railSide)
        val towardRailVelocity: Float = towardRailDx(dx = velocityX, isRtl = isRtl, railSide = railSide)
        return towardRailDistance >= thresholdPx ||
            (towardRailDistance > 0f && towardRailVelocity >= velocityThresholdPx)
    }

    /** Whether a row gesture has entered the Rail's [railBounds], staying reached once it has. */
    fun hasReachedRail(wasReached: Boolean, fingerInRoot: Offset, railBounds: Rect?): Boolean =
        wasReached || (railBounds?.contains(fingerInRoot) == true)

    /** How far Home moves at the end of its rubber band. */
    const val HOME_PULL_MAX_OFFSET_DP: Float = 64f

    /** How far the finger must pull Home past the edge of its content to reach the end of the rubber band and arm the swipe. */
    const val HOME_PULL_ARM_DP: Float = 180f

    /**
     * The offset Home shows for a finger [pullPx] past the edge of its content: it moves less
     * than the finger, resists more the further it is pulled, and stops at
     * [HOME_PULL_MAX_OFFSET_DP] exactly when the pull arms the swipe.
     */
    fun rubberBandOffset(pullPx: Float, density: Float): Float {
        val progress: Float = (pullPx / (HOME_PULL_ARM_DP * density)).coerceIn(0f, 1f)
        val remaining: Float = 1f - progress
        return HOME_PULL_MAX_OFFSET_DP * density * (1f - remaining * remaining)
    }

    /** Whether a pull of [pullPx] past the edge of Home arms the swipe on release. */
    fun isHomePullArmed(pullPx: Float, density: Float): Boolean = pullPx >= HOME_PULL_ARM_DP * density

    /** The release distance threshold in pixels. */
    fun thresholdPx(density: Float): Float = OPEN_THRESHOLD_DP * density

    /** The release velocity threshold in pixels per second. */
    fun velocityThresholdPx(density: Float): Float = VELOCITY_THRESHOLD_DP_PER_SECOND * density
}

/** The drag distance toward the Rail on [railSide], after accounting for [isRtl]. */
internal fun towardRailDx(dx: Float, isRtl: Boolean, railSide: RailSide): Float = if (railSide.isOnLeft(isRtl = isRtl)) -dx else dx

/** The physical offset for a positive [distance] toward the Rail on [railSide]. */
internal fun railDirectionOffset(distance: Float, isRtl: Boolean, railSide: RailSide): Float =
    if (railSide.isOnLeft(isRtl = isRtl)) -distance else distance
