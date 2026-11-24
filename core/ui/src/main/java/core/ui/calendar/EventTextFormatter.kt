package core.ui.calendar

import android.content.res.Resources
import core.data.calendar.UpcomingEvent
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderSettings
import core.data.prefs.is24Hour
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

private const val DEFAULT_EVENT_UNTITLED: String = "(No title)"
private const val DEFAULT_EVENT_UNTIL: String = "until "
private const val DEFAULT_EVENT_TODAY: String = "Today"
private const val DEFAULT_EVENT_TOMORROW: String = "Tomorrow"
private const val DEFAULT_EVENT_TOMORROW_LOWER: String = "tomorrow"

/**
 * Formats the event titles, times and day labels the Header's event lines and the agenda share.
 * [resources] is optional so pure tests format without Android resources.
 */
class EventTextFormatter(datePatternSource: IDatePatternSource, private val resources: Resources? = null) {
    private val clockText: ClockTextFormatter = ClockTextFormatter(datePatternSource = datePatternSource)

    fun timeText(dateTime: ZonedDateTime, reading: ClockReading, headerSettings: HeaderSettings): String = clockText.format(
        dateTime = dateTime,
        pattern = clockText.displayPattern(
            reading = reading,
            is24Hour = headerSettings.timeFormat.is24Hour(reading.isSystem24Hour),
            showAmPm = headerSettings.showAmPm
        ),
        locale = reading.locale
    )

    /** The "until ..." text of an event that has already started: its end time, with a day when needed. */
    fun ongoingText(event: UpcomingEvent, reading: ClockReading, headerSettings: HeaderSettings): String {
        val endDateTime: ZonedDateTime = Instant.ofEpochMilli(event.endMillis).atZone(reading.zoneId)
        val endDate: LocalDate = endDateTime.toLocalDate()
        val today: LocalDate = reading.instant.atZone(reading.zoneId).toLocalDate()
        val endTime: String = timeText(dateTime = endDateTime, reading = reading, headerSettings = headerSettings)
        val until: String = when (endDate) {
            today -> endTime
            today.plusDays(1) -> tomorrowLower() + " " + endTime
            else -> dayLabel(date = endDate, today = today, reading = reading) + " " + endTime
        }
        return eventResource(resourceId = R.string.event_until, fallback = DEFAULT_EVENT_UNTIL, formatArg = until)
    }

    fun title(event: UpcomingEvent): String = event.title.ifBlank {
        eventResource(resourceId = R.string.event_untitled, fallback = DEFAULT_EVENT_UNTITLED)
    }

    fun tomorrowLower(): String = eventResource(resourceId = R.string.event_tomorrow_lower, fallback = DEFAULT_EVENT_TOMORROW_LOWER)

    fun dayLabel(date: LocalDate, today: LocalDate, reading: ClockReading): String =
        relativeDayLabel(date = date, today = today) ?: dateText(date = date, preset = DateFormatPreset.WEEKDAY, reading = reading)

    fun dayHeading(date: LocalDate, today: LocalDate, reading: ClockReading): String =
        relativeDayLabel(date = date, today = today) ?: dateText(date = date, preset = DateFormatPreset.FULL, reading = reading)

    private fun relativeDayLabel(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> eventResource(resourceId = R.string.event_today, fallback = DEFAULT_EVENT_TODAY)
        today.plusDays(1) -> eventResource(resourceId = R.string.event_tomorrow, fallback = DEFAULT_EVENT_TOMORROW)
        else -> null
    }

    private fun dateText(date: LocalDate, preset: DateFormatPreset, reading: ClockReading): String =
        clockText.formatDate(dateTime = date.atStartOfDay(reading.zoneId), preset = preset, reading = reading)

    private fun eventResource(resourceId: Int, fallback: String): String = resources?.getString(resourceId) ?: fallback

    private fun eventResource(resourceId: Int, fallback: String, formatArg: String): String =
        resources?.getString(resourceId, formatArg) ?: (fallback + formatArg)
}
