package core.data.widgets

/** The maximum fraction of Home the Widget Area may occupy when its height is limited. */
enum class WidgetAreaHeightLimit(val fraction: Float) {
    HALF(0.5f),
    TWO_THIRDS(2f / 3f),
    THREE_QUARTERS(0.75f);

    companion object {
        val DEFAULT: WidgetAreaHeightLimit = TWO_THIRDS

        fun fromName(name: String?): WidgetAreaHeightLimit = entries.firstOrNull { limit -> limit.name == name } ?: DEFAULT
    }
}
