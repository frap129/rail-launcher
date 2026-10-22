package core.data.widgets

/** The visual gap between Widget Area places. */
enum class WidgetSpacing(val dp: Int) {
    NONE(0),
    SMALL(8),
    DEFAULT(16),
    LARGE(24),
    EXTRA_LARGE(32);

    companion object {
        fun fromDp(dp: Int): WidgetSpacing = entries.firstOrNull { spacing -> spacing.dp == dp } ?: DEFAULT
    }
}
