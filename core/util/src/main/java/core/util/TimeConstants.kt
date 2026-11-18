package core.util

/** Milliseconds in one minute, hour, day and week for wall-clock arithmetic. */
const val MILLIS_PER_MINUTE: Long = 60_000L
const val MILLIS_PER_HOUR: Long = 60L * MILLIS_PER_MINUTE
const val MILLIS_PER_DAY: Long = 24L * MILLIS_PER_HOUR
const val MILLIS_PER_WEEK: Long = 7L * MILLIS_PER_DAY

/** How recently an App must have been first installed to count as recently installed. */
const val RECENT_INSTALL_WINDOW_DAYS: Int = 3

/** [RECENT_INSTALL_WINDOW_DAYS] in milliseconds, the form install times compare against. */
const val RECENT_INSTALL_WINDOW_MS: Long = RECENT_INSTALL_WINDOW_DAYS * MILLIS_PER_DAY
