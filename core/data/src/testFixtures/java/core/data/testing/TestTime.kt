package core.data.testing

import core.util.MILLIS_PER_DAY

/** The fixed wall-clock time tests build their times from. */
const val TEST_NOW_EPOCH_MS: Long = 1_791_720_000_000L

/** The update time tests give an App's package. */
const val TEST_APP_UPDATE_TIME: Long = TEST_NOW_EPOCH_MS

/** Two Recent use half-lives, the span that quarters a stored score. */
const val TEST_FOURTEEN_DAYS_MS: Long = 14L * MILLIS_PER_DAY
