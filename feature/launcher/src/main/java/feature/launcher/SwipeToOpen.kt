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

/** The pure thresholds, offsets and gestures behind the Pop-up row swipe and the swipe up to Search. */
object SwipeToOpen {
    const val OPEN_THRESHOLD_DP: Float = 48f
    const val MAX_OFFSET_DP: Float = 72f

    /** How far up Home must be dragged to open Search. */
    const val SWIPE_UP_OPEN_THRESHOLD_DP: Float = 72f

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

    /**
     * Whether releasing a vertical drag of [dy] pixels at [velocityY] pixels per second opens
     * Search. A Favorites list that can still scroll forward, or an App Widget that took the drag,
     * never opens it.
     */
    fun onSwipeUpRelease(
        dx: Float,
        dy: Float,
        velocityY: Float,
        density: Float,
        canScrollForward: Boolean,
        isWidgetTakenSwipe: Boolean
    ): Boolean {
        if (canScrollForward || isWidgetTakenSwipe) {
            return false
        }
        val distanceUp: Float = -dy
        return distanceUp > 0f &&
            isVerticalDominant(dx = dx, dy = dy) &&
            (distanceUp >= swipeUpThresholdPx(density = density) || -velocityY >= velocityThresholdPx(density = density))
    }

    /** The release distance threshold in pixels. */
    fun thresholdPx(density: Float): Float = OPEN_THRESHOLD_DP * density

    /** The swipe-up distance threshold in pixels. */
    fun swipeUpThresholdPx(density: Float): Float = SWIPE_UP_OPEN_THRESHOLD_DP * density

    /** The release velocity threshold in pixels per second. */
    fun velocityThresholdPx(density: Float): Float = VELOCITY_THRESHOLD_DP_PER_SECOND * density
}

/** The drag distance toward the Rail on [railSide], after accounting for [isRtl]. */
internal fun towardRailDx(dx: Float, isRtl: Boolean, railSide: RailSide): Float = if (railSide.isOnLeft(isRtl = isRtl)) -dx else dx

/** The physical offset for a positive [distance] toward the Rail on [railSide]. */
internal fun railDirectionOffset(distance: Float, isRtl: Boolean, railSide: RailSide): Float =
    if (railSide.isOnLeft(isRtl = isRtl)) -distance else distance
