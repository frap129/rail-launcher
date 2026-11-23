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

    /** The end label of an event that has already started: its end time, with a day when needed. */
    fun untilLabel(event: UpcomingEvent, reading: ClockReading, headerSettings: HeaderSettings): String {
        val endDate: LocalDate = Instant.ofEpochMilli(event.endMillis).atZone(reading.zoneId).toLocalDate()
        val today: LocalDate = reading.instant.atZone(reading.zoneId).toLocalDate()
        val endTime: String = timeText(
            dateTime = Instant.ofEpochMilli(event.endMillis).atZone(reading.zoneId),
            reading = reading,
            headerSettings = headerSettings
        )
        return when (endDate) {
            today -> endTime
            today.plusDays(1) -> tomorrowLower() + " " + endTime
            else -> dayLabel(date = endDate, today = today, reading = reading) + " " + endTime
        }
    }

    fun title(event: UpcomingEvent): String = event.title.ifBlank {
        eventResource(resourceId = R.string.event_untitled, fallback = DEFAULT_EVENT_UNTITLED)
    }

    fun untilText(until: String): String = resources?.getString(R.string.event_until, until) ?: (DEFAULT_EVENT_UNTIL + until)

    fun tomorrowLower(): String = eventResource(resourceId = R.string.event_tomorrow_lower, fallback = DEFAULT_EVENT_TOMORROW_LOWER)

    fun dayLabel(date: LocalDate, today: LocalDate, reading: ClockReading): String =
        relativeDayLabel(date = date, today = today) ?: weekday(date = date, reading = reading)

    fun dayHeading(date: LocalDate, today: LocalDate, reading: ClockReading): String =
        relativeDayLabel(date = date, today = today) ?: dateLabel(date = date, reading = reading)

    private fun relativeDayLabel(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> eventResource(resourceId = R.string.event_today, fallback = DEFAULT_EVENT_TODAY)
        today.plusDays(1) -> eventResource(resourceId = R.string.event_tomorrow, fallback = DEFAULT_EVENT_TOMORROW)
        else -> null
    }

    private fun dateLabel(date: LocalDate, reading: ClockReading): String = clockText.format(
        dateTime = date.atStartOfDay(reading.zoneId),
        pattern = clockText.datePattern(skeleton = DateFormatPreset.FULL.skeleton, reading = reading),
        locale = reading.locale
    )

    private fun weekday(date: LocalDate, reading: ClockReading): String = clockText.format(
        dateTime = date.atStartOfDay(reading.zoneId),
        pattern = clockText.datePattern(skeleton = DateFormatPreset.WEEKDAY.skeleton, reading = reading),
        locale = reading.locale
    )

    private fun eventResource(resourceId: Int, fallback: String): String = resources?.getString(resourceId) ?: fallback
}
