package feature.launcher

/** What a Rail callback does with its index while a Pop-up card takes the gesture. */
internal enum class RailIndexAction {
    /** Preview the index while scrolling or release it when the gesture ends. */
    Act,

    /** Ignore the index: the gesture closes or has closed a Pop-up card. */
    Ignore
}

/**
 * Applies the open-Pop-up-card policy to one Rail gesture: the first callback
 * that finds a card open closes it and latches, so the rest of the gesture
 * neither previews nor releases a Rail position.
 */
internal class RailGesture {
    private var isConsumedByCardClose: Boolean = false

    /** Decides a scroll callback, closing an open card and latching on the gesture's first callback. */
    fun onScroll(isCardOpen: Boolean, closeCard: () -> Unit): RailIndexAction = decide(isCardOpen = isCardOpen, closeCard = closeCard)

    /** Decides a scroll-ended callback and re-arms the next gesture. */
    fun onScrollEnded(isCardOpen: Boolean, closeCard: () -> Unit): RailIndexAction {
        val action: RailIndexAction = decide(isCardOpen = isCardOpen, closeCard = closeCard)
        reset()
        return action
    }

    /** Re-arms the gesture after the Rail's pointer input restarts mid-touch. */
    fun reset() {
        isConsumedByCardClose = false
    }

    private fun decide(isCardOpen: Boolean, closeCard: () -> Unit): RailIndexAction {
        if (isConsumedByCardClose) {
            return RailIndexAction.Ignore
        }
        if (!isCardOpen) {
            return RailIndexAction.Act
        }
        isConsumedByCardClose = true
        closeCard()
        return RailIndexAction.Ignore
    }
}
