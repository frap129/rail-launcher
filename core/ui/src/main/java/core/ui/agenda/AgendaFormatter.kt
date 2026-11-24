package core.ui.agenda

import android.content.res.Resources
import core.data.calendar.AgendaDay
import core.data.calendar.UpcomingEvent
import core.data.prefs.HeaderSettings
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import core.ui.calendar.EventTextFormatter
import core.ui.calendar.eventColor
import core.ui.calendar.openEventTarget
import java.time.Instant
import java.time.LocalDate

/** Formats the agenda's day headings and event rows from the grouped events. */
class AgendaFormatter(datePatternSource: IDatePatternSource, resources: Resources? = null) {
    private val eventText: EventTextFormatter = EventTextFormatter(datePatternSource = datePatternSource, resources = resources)

    fun format(days: List<AgendaDay>, reading: ClockReading, headerSettings: HeaderSettings): List<AgendaDayModel> {
        val today: LocalDate = reading.instant.atZone(reading.zoneId).toLocalDate()
        return days.map { day ->
            AgendaDayModel(
                label = eventText.dayHeading(date = day.date, today = today, reading = reading),
                events = day.events.map { event -> toEventModel(event = event, reading = reading, headerSettings = headerSettings) }
            )
        }
    }

    private fun toEventModel(event: UpcomingEvent, reading: ClockReading, headerSettings: HeaderSettings): AgendaEventModel {
        val timeText: String? = when {
            event.isAllDay -> null

            event.beginMillis <= reading.instant.toEpochMilli() -> eventText.ongoingText(
                event = event,
                reading = reading,
                headerSettings = headerSettings
            )

            else -> eventText.timeText(
                dateTime = Instant.ofEpochMilli(event.beginMillis).atZone(reading.zoneId),
                reading = reading,
                headerSettings = headerSettings
            )
        }
        return AgendaEventModel(
            title = eventText.title(event),
            timeText = timeText,
            color = event.eventColor(),
            tapTarget = event.openEventTarget()
        )
    }
}
