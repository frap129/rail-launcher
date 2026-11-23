package core.data.testing

import core.data.calendar.CalendarInfo
import core.data.calendar.UpcomingEvent
import java.time.Instant

/** An [UpcomingEvent] at the ISO-8601 [begin] and [end] instants. */
fun createUpcomingEvent(
    title: String = "Event",
    begin: String = "2026-10-20T14:06:00Z",
    end: String = "2026-10-20T14:30:00Z",
    allDay: Boolean = false,
    eventId: Long = 1L,
    calendarId: Long = 1L,
    color: Int = 0,
    isWorkProfile: Boolean = false
): UpcomingEvent = UpcomingEvent(
    eventId = eventId,
    title = title,
    beginMillis = Instant.parse(begin).toEpochMilli(),
    endMillis = Instant.parse(end).toEpochMilli(),
    isAllDay = allDay,
    calendarId = calendarId,
    color = color,
    isWorkProfile = isWorkProfile
)

/** A personal Calendar with the first Calendar key. */
val personalCalendar: CalendarInfo = CalendarInfo(
    id = 1L,
    name = "Personal",
    accountName = "me@example.com",
    color = 0,
    isWorkProfile = false
)

/** A work Calendar with the first work Calendar key. */
val workCalendar: CalendarInfo = CalendarInfo(
    id = 5L,
    name = "Work",
    accountName = "work@example.com",
    color = 0,
    isWorkProfile = true
)
