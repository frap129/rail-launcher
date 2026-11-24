package feature.launcher

import kotlin.math.abs
import kotlin.math.sign

/** What a Rail callback does with its index while a Pop-up card takes the gesture. */
internal enum class RailIndexAction {
    /** Preview the index while scrolling or release it when the gesture ends. */
    Act,

    /** Ignore the index: the gesture closes or has closed a Pop-up card. */
    Ignore
}

/** The direction a wheel notch over the Rail moves it. */
internal enum class RailWheelDirection {
    /** Toward the last mark, as a downward notch does. */
    Next,

    /** Toward the first mark, as an upward notch does. */
    Previous;

    companion object {
        /** The direction of wheel scroll [deltaY], positive for a downward notch and negative for an upward one. */
        fun fromScrollDelta(deltaY: Float): RailWheelDirection = if (deltaY > 0f) Next else Previous
    }
}

/** The Pop-up card that takes a Rail gesture: [isOpen] says whether one is open, and [close] closes it. */
internal class RailPopupCard(val isOpen: Boolean, val close: () -> Unit)

/**
 * Applies the open-Pop-up-card policy to one Rail gesture: the first callback
 * that finds a card open closes it and latches, so the rest of the gesture
 * neither previews nor releases a Rail position. It also tracks the mark the
 * Rail last showed, so a wheel notch steps from there, and counts wheel scroll
 * deltas into whole notches.
 */
internal class RailGesture {
    private var isConsumedByCardClose: Boolean = false
    private var lastMarkIndex: Int = 0
    private var pendingNotches: Float = 0f

    /** Decides a scroll callback, closing an open card and latching on the gesture's first callback. */
    fun onScroll(index: Int, card: RailPopupCard): RailIndexAction {
        val action: RailIndexAction = decide(card = card)
        if (action == RailIndexAction.Act) {
            lastMarkIndex = index
        }
        return action
    }

    /** Decides a scroll-ended callback and re-arms the next gesture. */
    fun onScrollEnded(index: Int, card: RailPopupCard): RailIndexAction {
        val action: RailIndexAction = onScroll(index = index, card = card)
        reset()
        return action
    }

    /**
     * The marks the wheel scroll [deltaY] moves the Rail to, one mark per whole notch and stopping
     * at the first and last of [markCount]. A notch is 1f of vertical scroll, so a high-resolution
     * wheel's fractions add up and a fast wheel's whole notches each step; a direction change drops
     * the fraction, as leaving the Rail does. Only wheel notches step the Rail: a trackpad's
     * pixel-based pan events are not converted to notches.
     */
    fun onWheelScroll(deltaY: Float, markCount: Int, card: RailPopupCard): List<Int> =
        accumulateNotches(deltaY = deltaY).mapNotNull { direction ->
            onWheelStep(direction = direction, markCount = markCount, card = card)
        }

    /** Drops a partial wheel notch when the pointer leaves the Rail. */
    fun resetWheelScroll() {
        pendingNotches = 0f
    }

    /**
     * The mark one wheel notch moves the Rail to, one mark per notch from the mark it last showed
     * and stopping at the first and last of [markCount]. Returns null when the notch does not move
     * the Rail, because an open card takes it, the Rail has no marks, or it is already at that edge.
     * Each notch is its own gesture, so a notch that closes a card re-arms the next notch.
     */
    fun onWheelStep(direction: RailWheelDirection, markCount: Int, card: RailPopupCard): Int? {
        val action: RailIndexAction = decide(card = card)
        reset()
        if (action != RailIndexAction.Act || markCount <= 0) {
            return null
        }
        val nextMarkIndex: Int = (lastMarkIndex + direction.step).coerceIn(0, markCount - 1)
        if (nextMarkIndex == lastMarkIndex) {
            return null
        }
        lastMarkIndex = nextMarkIndex
        return nextMarkIndex
    }

    /** Re-arms the gesture after the Rail's pointer input restarts mid-touch. */
    fun reset() {
        isConsumedByCardClose = false
    }

    private fun decide(card: RailPopupCard): RailIndexAction {
        if (isConsumedByCardClose) {
            return RailIndexAction.Ignore
        }
        if (!card.isOpen) {
            return RailIndexAction.Act
        }
        isConsumedByCardClose = true
        card.close()
        return RailIndexAction.Ignore
    }

    /** The whole notches [deltaY] adds, keeping the fraction for the next scroll. */
    private fun accumulateNotches(deltaY: Float): List<RailWheelDirection> {
        if (deltaY == 0f) {
            return emptyList()
        }
        if (sign(deltaY) != sign(pendingNotches)) {
            pendingNotches = 0f
        }
        pendingNotches += deltaY
        val wholeNotches: Int = pendingNotches.toInt()
        pendingNotches -= wholeNotches
        if (wholeNotches == 0) {
            return emptyList()
        }
        val direction: RailWheelDirection = RailWheelDirection.fromScrollDelta(deltaY = wholeNotches.toFloat())
        return List(abs(wholeNotches)) { direction }
    }
}

/** The mark step one wheel notch takes in [this] direction. */
private val RailWheelDirection.step: Int
    get() = when (this) {
        RailWheelDirection.Next -> 1
        RailWheelDirection.Previous -> -1
    }
