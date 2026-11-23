package core.ui.calendar

import androidx.compose.ui.graphics.Color
import core.data.calendar.UpcomingEvent

/** The Calendar event a row opens in the calendar App. */
data class OpenEventTarget(val eventId: Long, val beginMillis: Long, val endMillis: Long, val isWorkProfile: Boolean)

/** The target that opens this event in the calendar App. */
fun UpcomingEvent.openEventTarget(): OpenEventTarget = OpenEventTarget(
    eventId = eventId,
    beginMillis = beginMillis,
    endMillis = endMillis,
    isWorkProfile = isWorkProfile
)

/** The colour the Calendar assigned this event. */
fun UpcomingEvent.eventColor(): Color = Color(color)
