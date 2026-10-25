package core.popups

import core.util.MILLIS_PER_DAY
import core.util.MILLIS_PER_HOUR
import core.util.MILLIS_PER_MINUTE
import core.util.MILLIS_PER_WEEK

/** How long ago a notification was posted, at the granularity a row shows. */
sealed interface NotificationAge {
    data object Now : NotificationAge

    data class Minutes(val value: Long) : NotificationAge

    data class Hours(val value: Long) : NotificationAge

    data class Days(val value: Long) : NotificationAge

    data class Weeks(val value: Long) : NotificationAge
}

fun notificationAge(postTime: Long, now: Long): NotificationAge {
    val elapsed: Long = (now - postTime).coerceAtLeast(0L)
    return when {
        elapsed < MILLIS_PER_MINUTE -> NotificationAge.Now
        elapsed < MILLIS_PER_HOUR -> NotificationAge.Minutes(value = elapsed / MILLIS_PER_MINUTE)
        elapsed < MILLIS_PER_DAY -> NotificationAge.Hours(value = elapsed / MILLIS_PER_HOUR)
        elapsed < MILLIS_PER_WEEK -> NotificationAge.Days(value = elapsed / MILLIS_PER_DAY)
        else -> NotificationAge.Weeks(value = elapsed / MILLIS_PER_WEEK)
    }
}
