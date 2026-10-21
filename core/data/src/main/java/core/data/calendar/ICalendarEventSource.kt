package core.data.calendar

import core.data.prefs.EventSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class UpcomingEvent(
    val eventId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean,
    val calendarId: Long,
    val color: Int,
    val isWorkProfile: Boolean
) {
    val calendarKey: String get() = calendarKey(calendarId = calendarId, isWorkProfile = isWorkProfile)
}

data class CalendarInfo(val id: Long, val name: String, val accountName: String, val color: Int, val isWorkProfile: Boolean) {
    val calendarKey: String get() = calendarKey(calendarId = id, isWorkProfile = isWorkProfile)
}

fun calendarKey(calendarId: Long, isWorkProfile: Boolean): String =
    if (isWorkProfile) WORK_CALENDAR_PREFIX + calendarId else calendarId.toString()

private const val WORK_CALENDAR_PREFIX: String = "w:"

interface ICalendarEventSource {
    val hasPermission: StateFlow<Boolean>

    val calendars: Flow<List<CalendarInfo>>

    fun events(windowEndMillis: Long): Flow<List<UpcomingEvent>>

    fun refreshPermission()
}

/** Clamps a lookahead so callers can pass an untrusted stored value without opening a huge query window. */
fun EventSettings.effectiveLookaheadHours(): Int = lookaheadHours.coerceIn(1, MAX_EVENT_LOOKAHEAD_HOURS)

const val MAX_EVENT_LOOKAHEAD_HOURS: Int = 48
