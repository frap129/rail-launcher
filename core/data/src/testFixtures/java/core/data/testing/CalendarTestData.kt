package core.data.testing

import core.data.calendar.CalendarInfo

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
