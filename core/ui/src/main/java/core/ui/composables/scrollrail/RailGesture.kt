package core.ui.composables.scrollrail

import kotlin.math.abs
import kotlin.math.sign

private const val PREVIOUS_RAIL_WHEEL_STEP: Int = -1
private const val NEXT_RAIL_WHEEL_STEP: Int = 1

/** What a Rail callback does with its index while a Pop-up card takes the gesture. */
enum class RailIndexAction {
    /** Preview the index while scrolling or release it when the gesture ends. */
    Act,

    /** Ignore the index: the gesture closes or has closed a Pop-up card. */
    Ignore
}

/** The Pop-up card that takes a Rail gesture: [isOpen] says whether one is open, and [close] closes it. */
class RailPopupCard(val isOpen: Boolean, val close: () -> Unit)

/** The Rail position, mark count and Pop-up card one Rail wheel step uses. */
data class RailWheelStep(val currentMarkIndex: Int, val markCount: Int, val card: RailPopupCard)

/**
 * Applies the open-Pop-up-card policy to one Rail gesture: the first callback
 * that finds a card open closes it and latches, so the rest of the gesture
 * neither previews nor releases a Rail position. It also counts wheel deltas
 * into whole notches.
 */
class RailGesture {
    private var isConsumedByCardClose: Boolean = false
    private var pendingNotches: Float = 0f

    /** Decides a scroll callback, closing an open card and latching on the gesture's first callback. */
    fun onScroll(card: RailPopupCard): RailIndexAction {
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

    /** Decides a scroll-ended callback and re-arms the next gesture. */
    fun onScrollEnded(card: RailPopupCard): RailIndexAction {
        val action: RailIndexAction = onScroll(card = card)
        reset()
        return action
    }

    /**
     * The marks the wheel delta [deltaY] moves the Rail to from [wheelStep], one mark per whole notch
     * and stopping at the first and last marks. A notch is 1f of vertical scroll, so a high-resolution
     * wheel's fractions add up and a fast wheel's whole notches each step; a direction change drops
     * the fraction, as leaving the Rail does. Only wheel notches step the Rail: a trackpad's
     * pixel-based pan events are not converted to notches.
     */
    fun onWheelScroll(deltaY: Float, wheelStep: RailWheelStep): List<Int> {
        val wholeNotches: Int = accumulateNotches(deltaY = deltaY)
        if (wholeNotches == 0) {
            return emptyList()
        }
        val step: Int = wholeNotches.sign
        var currentMarkIndex: Int = wheelStep.currentMarkIndex
        return List(abs(wholeNotches)) {
            val nextMarkIndex: Int? = onWheelStep(
                step = step,
                wheelStep = wheelStep.copy(currentMarkIndex = currentMarkIndex)
            )
            if (nextMarkIndex != null) {
                currentMarkIndex = nextMarkIndex
            }
            nextMarkIndex
        }.mapNotNull { markIndex -> markIndex }
    }

    /** Drops a partial wheel notch when the pointer leaves the Rail. */
    fun resetWheelScroll() {
        pendingNotches = 0f
    }

    /**
     * The mark one Rail wheel step moves the Rail to from [wheelStep], stopping at the first and last
     * marks. Returns null when the step does not move the Rail, because an open card takes it, the
     * Rail has no marks, or it is already at that edge. Each notch is its own gesture, so a notch that
     * closes a card re-arms the next notch.
     */
    fun onWheelStep(step: Int, wheelStep: RailWheelStep): Int? {
        require(step == PREVIOUS_RAIL_WHEEL_STEP || step == NEXT_RAIL_WHEEL_STEP)
        val action: RailIndexAction = onScroll(card = wheelStep.card)
        reset()
        if (action != RailIndexAction.Act || wheelStep.markCount <= 0) {
            return null
        }
        val nextMarkIndex: Int = (wheelStep.currentMarkIndex + step).coerceIn(0, wheelStep.markCount - 1)
        return nextMarkIndex.takeUnless { markIndex -> markIndex == wheelStep.currentMarkIndex }
    }

    /** Re-arms the gesture after the Rail's pointer input restarts mid-touch. */
    fun reset() {
        isConsumedByCardClose = false
    }

    /** The signed whole notches [deltaY] adds, keeping the fraction for the next scroll. */
    private fun accumulateNotches(deltaY: Float): Int {
        if (deltaY == 0f) {
            return 0
        }
        if (sign(deltaY) != sign(pendingNotches)) {
            pendingNotches = 0f
        }
        pendingNotches += deltaY
        val wholeNotches: Int = pendingNotches.toInt()
        pendingNotches -= wholeNotches
        return wholeNotches
    }
}
