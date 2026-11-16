package core.data.room.dao

/** The Launch count after counting one more launch of a stored count, or 1 when none was stored. */
internal fun Int?.nextLaunchCount(): Int = (this ?: 0) + 1
