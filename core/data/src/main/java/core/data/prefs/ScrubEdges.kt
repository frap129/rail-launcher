package core.data.prefs

/** The screen edges, relative to the layout direction, whose touches scrub the Rail. */
enum class ScrubEdges(val isStartIncluded: Boolean, val isEndIncluded: Boolean) {
    START(isStartIncluded = true, isEndIncluded = false),
    END(isStartIncluded = false, isEndIncluded = true),
    BOTH(isStartIncluded = true, isEndIncluded = true);

    /** Whether touches on [side] scrub the Rail. */
    fun includes(side: RailSide): Boolean = if (side.isStart) isStartIncluded else isEndIncluded

    companion object {
        val DEFAULT: ScrubEdges = BOTH
    }
}
