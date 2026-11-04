package core.data.prefs

/** The screen side, relative to the layout direction, where the Rail is drawn. */
enum class RailSide {
    START,
    END;

    val isStart: Boolean get() = this == START

    /** Whether this side is the physical left of a layout with [isRtl]. */
    fun isOnLeft(isRtl: Boolean): Boolean = isStart != isRtl

    fun opposite(): RailSide = if (isStart) END else START

    companion object {
        val DEFAULT: RailSide = END

        /** The layout side that appears on the physical left of a layout with [isRtl]. */
        fun forLeft(isOnLeft: Boolean, isRtl: Boolean): RailSide = if (isOnLeft == isRtl) END else START
    }
}
