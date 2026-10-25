package core.popups

import org.junit.Assert.assertEquals
import org.junit.Test

private const val ONE_SECOND: Long = 1_000L
private const val ONE_MINUTE: Long = 60_000L
private const val ONE_HOUR: Long = 60L * ONE_MINUTE
private const val ONE_DAY: Long = 24L * ONE_HOUR
private const val ONE_WEEK: Long = 7L * ONE_DAY
private const val NOW: Long = 10_000_000L

class NotificationAgeTest {
    @Test
    fun `less than a minute is now`() {
        assertEquals(NotificationAge.Now, notificationAge(postTime = NOW - 59 * ONE_SECOND, now = NOW))
    }

    @Test
    fun `counts minutes under an hour`() {
        assertEquals(NotificationAge.Minutes(value = 6L), notificationAge(postTime = NOW - 6 * ONE_MINUTE, now = NOW))
    }

    @Test
    fun `counts hours under a day`() {
        assertEquals(NotificationAge.Hours(value = 2L), notificationAge(postTime = NOW - 2 * ONE_HOUR, now = NOW))
    }

    @Test
    fun `counts days under a week`() {
        assertEquals(NotificationAge.Days(value = 3L), notificationAge(postTime = NOW - 3 * ONE_DAY, now = NOW))
    }

    @Test
    fun `counts weeks after seven days`() {
        assertEquals(NotificationAge.Weeks(value = 2L), notificationAge(postTime = NOW - 2 * ONE_WEEK, now = NOW))
    }

    @Test
    fun `a future post time is now`() {
        assertEquals(NotificationAge.Now, notificationAge(postTime = NOW + ONE_HOUR, now = NOW))
    }
}
