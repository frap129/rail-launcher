package feature.widgets

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import core.data.battery.BatteryStatus
import core.data.calendar.UpcomingEvent
import core.data.calendar.effectiveLookaheadHours
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.is24Hour
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val TIME_SKELETON_12_HOUR: String = "hm"
private const val TIME_SKELETON_24_HOUR: String = "Hm"
private const val HOUR_PATTERN_12_HOUR: String = "h"
private const val HOUR_PATTERN_24_HOUR: String = "H"
private const val MINUTE_PATTERN: String = "mm"
private const val DAY_PERIOD_PATTERN: String = "a"
private const val SPOKEN_TIME_PATTERN_12_HOUR: String = "h:mm a"
private const val SPOKEN_TIME_PATTERN_24_HOUR: String = "HH:mm"
private const val PREVIEW_ID: Long = 0L
private const val LOW_BATTERY_PERCENT: Int = 20
private const val WEEKDAY_SKELETON: String = "EEEE"
private const val EVENT_SEPARATOR: String = " · "
private const val DEFAULT_EVENT_UNTITLED: String = "(No title)"
private const val DEFAULT_EVENT_UNTIL: String = "until "
private const val DEFAULT_EVENT_TODAY: String = "Today"
private const val DEFAULT_EVENT_TOMORROW: String = "Tomorrow"
private const val DEFAULT_EVENT_TOMORROW_LOWER: String = "tomorrow"
private val DAY_PERIOD_REGEX: Regex = Regex("[\\s\\u00A0\\u202F]*[aBb]+[\\s\\u00A0\\u202F]*")
private val HOUR_REGEX: Regex = Regex("[hHK]+")

private data class AllDayEventCandidate(val day: LocalDate, val model: EventModel)

/** Pure mapping from Header settings and the current clock reading to the values the Header renders. */
class HeaderFormatter(private val datePatternSource: IDatePatternSource, private val resources: Resources? = null) {
    fun format(
        id: Long,
        reading: ClockReading,
        settings: HeaderSettings,
        battery: BatteryStatus? = null,
        events: List<UpcomingEvent> = emptyList(),
        eventSettings: EventSettings = EventSettings()
    ): HeaderModel {
        val dateTime: ZonedDateTime = reading.instant.atZone(reading.zoneId)
        val is24Hour: Boolean = settings.timeFormat.is24Hour(reading.isSystem24Hour)
        return HeaderModel(
            id = id,
            time = if (settings.clockVisible) {
                createTimeModel(
                    dateTime = dateTime,
                    reading = reading,
                    is24Hour = is24Hour,
                    showAmPm = !is24Hour && settings.showAmPm,
                    style = settings.clockStyle
                )
            } else {
                null
            },
            date = if (settings.dateVisible) createDateModel(dateTime, reading, settings.dateFormat) else null,
            battery = createBatteryModel(battery, settings.batteryVisibility),
            events = selectEvents(
                events = events,
                eventSettings = eventSettings,
                headerSettings = settings,
                reading = reading
            ),
            style = settings.clockStyle,
            alignment = settings.alignment,
            size = settings.size
        )
    }

    fun formatDate(reading: ClockReading, preset: DateFormatPreset): String {
        val dateTime: ZonedDateTime = reading.instant.atZone(reading.zoneId)
        return format(dateTime, datePatternSource.bestPattern(preset.skeleton, reading.locale), reading.locale)
    }

    fun previews(reading: ClockReading, settings: HeaderSettings): List<HeaderModel> = ClockStyle.entries.map { style ->
        format(id = PREVIEW_ID, reading = reading, settings = settings.copy(clockStyle = style))
    }

    /**
     * Picks and formats the event lines for the Header. Ongoing timed events come first by end time;
     * timed and all-day events are then ordered by day, with all-day after timed events on that day.
     */
    fun selectEvents(
        events: List<UpcomingEvent>,
        eventSettings: EventSettings,
        headerSettings: HeaderSettings,
        reading: ClockReading
    ): List<EventModel> {
        if (!eventSettings.enabled) {
            return emptyList()
        }
        val nowMillis: Long = reading.instant.toEpochMilli()
        val windowEndMillis: Long = nowMillis + Duration.ofHours(eventSettings.effectiveLookaheadHours().toLong()).toMillis()
        val today: LocalDate = reading.instant.atZone(reading.zoneId).toLocalDate()
        val excludedCalendarIds: Set<String> = eventSettings.excludedCalendarIds
        val timedEvents: List<UpcomingEvent> = events.filter { event ->
            !event.isAllDay &&
                event.calendarKey !in excludedCalendarIds &&
                event.endMillis > nowMillis &&
                event.beginMillis < windowEndMillis
        }
        val ongoing: List<EventModel> = timedEvents
            .filter { event -> event.beginMillis <= nowMillis }
            .sortedBy { event -> event.endMillis }
            .map { event -> createOngoingEvent(event = event, headerSettings = headerSettings, reading = reading, today = today) }
        val upcoming: List<UpcomingEvent> = timedEvents
            .filter { event -> event.beginMillis > nowMillis }
            .sortedBy { event -> event.beginMillis }
        val upcomingByDay: Map<LocalDate, List<UpcomingEvent>> = upcoming.groupBy { event ->
            Instant.ofEpochMilli(event.beginMillis).atZone(reading.zoneId).toLocalDate()
        }
        val allDayByDay: Map<LocalDate, List<AllDayEventCandidate>> = if (eventSettings.includeAllDay) {
            events.filter { event ->
                event.isAllDay && event.calendarKey !in excludedCalendarIds && allDayEndDate(event) > today
            }.flatMap { event -> allDayCandidates(event = event, today = today, windowEndMillis = windowEndMillis, reading = reading) }
                .groupBy { candidate -> candidate.day }
        } else {
            emptyMap()
        }
        val ordered: List<EventModel> = (upcomingByDay.keys + allDayByDay.keys).sorted().flatMap { day ->
            upcomingByDay[day].orEmpty().map { event ->
                createUpcomingEvent(event = event, headerSettings = headerSettings, reading = reading, today = today)
            } + allDayByDay[day].orEmpty().map { candidate -> candidate.model }
        }
        return (ongoing + ordered).take(eventSettings.resolvedMaxEvents)
    }

    private fun createOngoingEvent(
        event: UpcomingEvent,
        headerSettings: HeaderSettings,
        reading: ClockReading,
        today: LocalDate
    ): EventModel {
        val title: String = eventTitle(event)
        val endDate: LocalDate = Instant.ofEpochMilli(event.endMillis).atZone(reading.zoneId).toLocalDate()
        val endTime: String = timeText(
            dateTime = Instant.ofEpochMilli(event.endMillis).atZone(reading.zoneId),
            headerSettings = headerSettings,
            reading = reading
        )
        val until: String = when (endDate) {
            today -> endTime
            today.plusDays(1) -> eventTomorrowLower() + " " + endTime
            else -> weekday(date = endDate, reading = reading) + " " + endTime
        }
        val suffix: String = EVENT_SEPARATOR + untilText(until)
        return createEventModel(event = event, text = title + suffix, suffix = suffix)
    }

    private fun createUpcomingEvent(
        event: UpcomingEvent,
        headerSettings: HeaderSettings,
        reading: ClockReading,
        today: LocalDate
    ): EventModel {
        val title: String = eventTitle(event)
        val beginDateTime: ZonedDateTime = Instant.ofEpochMilli(event.beginMillis).atZone(reading.zoneId)
        val beginDate: LocalDate = beginDateTime.toLocalDate()
        val time: String = timeText(dateTime = beginDateTime, headerSettings = headerSettings, reading = reading)
        val prefix: String = when (beginDate) {
            today -> ""
            today.plusDays(1) -> eventTomorrow() + EVENT_SEPARATOR
            else -> weekday(date = beginDate, reading = reading) + EVENT_SEPARATOR
        }
        val suffix: String = EVENT_SEPARATOR + time
        return createEventModel(event = event, text = prefix + title + suffix, suffix = suffix)
    }

    private fun allDayCandidates(
        event: UpcomingEvent,
        today: LocalDate,
        windowEndMillis: Long,
        reading: ClockReading
    ): List<AllDayEventCandidate> {
        val beginDate: LocalDate = allDayBeginDate(event)
        val endDate: LocalDate = allDayEndDate(event)
        val firstDay: LocalDate = maxOf(beginDate, today)
        val lastDay: LocalDate = endDate.minusDays(1)
        if (firstDay > lastDay) {
            return emptyList()
        }
        return generateSequence(firstDay) { day -> day.plusDays(1) }
            .takeWhile { day -> !day.isAfter(lastDay) }
            .filter { day -> day.atStartOfDay(reading.zoneId).toInstant().toEpochMilli() < windowEndMillis }
            .map { day ->
                val title: String = eventTitle(event)
                AllDayEventCandidate(
                    day = day,
                    model = createEventModel(
                        event = event,
                        text = dayLabel(date = day, today = today, reading = reading) + EVENT_SEPARATOR + title
                    )
                )
            }
            .toList()
    }

    private fun createEventModel(event: UpcomingEvent, text: String, suffix: String = ""): EventModel = EventModel(
        text = text,
        suffix = suffix,
        spokenText = text.replace(EVENT_SEPARATOR, ", "),
        color = Color(event.color),
        tapTarget = HeaderTapTarget.OpenEvent(
            eventId = event.eventId,
            beginMillis = event.beginMillis,
            endMillis = event.endMillis,
            isWorkProfile = event.isWorkProfile
        ),
        clickLabel = R.string.open_event,
        clickLabelArg = eventTitle(event)
    )

    private fun eventTitle(event: UpcomingEvent): String = event.title.ifBlank {
        eventResource(R.string.event_untitled, DEFAULT_EVENT_UNTITLED)
    }

    private fun untilText(until: String): String = resources?.getString(R.string.event_until, until) ?: (DEFAULT_EVENT_UNTIL + until)

    private fun eventToday(): String = eventResource(R.string.event_today, DEFAULT_EVENT_TODAY)

    private fun eventTomorrow(): String = eventResource(R.string.event_tomorrow, DEFAULT_EVENT_TOMORROW)

    private fun eventTomorrowLower(): String = eventResource(R.string.event_tomorrow_lower, DEFAULT_EVENT_TOMORROW_LOWER)

    private fun eventResource(resourceId: Int, fallback: String): String = resources?.getString(resourceId) ?: fallback

    private fun timeText(dateTime: ZonedDateTime, headerSettings: HeaderSettings, reading: ClockReading): String = format(
        dateTime = dateTime,
        pattern = displayPattern(
            reading = reading,
            is24Hour = headerSettings.timeFormat.is24Hour(reading.isSystem24Hour),
            showAmPm = headerSettings.showAmPm
        ),
        locale = reading.locale
    )

    private fun dayLabel(date: LocalDate, today: LocalDate, reading: ClockReading): String = when (date) {
        today -> eventToday()
        today.plusDays(1) -> eventTomorrow()
        else -> weekday(date = date, reading = reading)
    }

    private fun weekday(date: LocalDate, reading: ClockReading): String = format(
        dateTime = date.atStartOfDay(reading.zoneId),
        pattern = datePatternSource.bestPattern(WEEKDAY_SKELETON, reading.locale),
        locale = reading.locale
    )

    private fun allDayBeginDate(event: UpcomingEvent): LocalDate =
        Instant.ofEpochMilli(event.beginMillis).atZone(ZoneOffset.UTC).toLocalDate()

    private fun allDayEndDate(event: UpcomingEvent): LocalDate = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC).toLocalDate()

    private fun createTimeModel(
        dateTime: ZonedDateTime,
        reading: ClockReading,
        is24Hour: Boolean,
        showAmPm: Boolean,
        style: ClockStyle
    ): HeaderTimeModel {
        val spokenText: String = format(dateTime, spokenPattern(is24Hour), reading.locale)
        val common: HeaderTimeModel = HeaderTimeModel(
            text = null,
            hourText = null,
            minuteText = null,
            amPmText = null,
            spokenText = spokenText,
            tapTarget = HeaderTapTarget.SystemAlarms,
            clickLabel = R.string.open_alarms
        )
        return if (style == ClockStyle.STACKED) {
            common.copy(
                hourText = format(dateTime, if (is24Hour) HOUR_PATTERN_24_HOUR else HOUR_PATTERN_12_HOUR, reading.locale),
                minuteText = format(dateTime, MINUTE_PATTERN, reading.locale),
                amPmText = if (showAmPm) format(dateTime, DAY_PERIOD_PATTERN, reading.locale) else null
            )
        } else {
            common.copy(text = format(dateTime, displayPattern(reading, is24Hour, showAmPm), reading.locale))
        }
    }

    private fun createDateModel(dateTime: ZonedDateTime, reading: ClockReading, preset: DateFormatPreset): HeaderLineModel =
        HeaderLineModel(
            text = format(dateTime, datePatternSource.bestPattern(preset.skeleton, reading.locale), reading.locale),
            spokenText = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(reading.locale).format(dateTime),
            tapTarget = HeaderTapTarget.SystemCalendar(atEpochMillis = reading.instant.toEpochMilli()),
            clickLabel = R.string.open_calendar
        )

    private fun createBatteryModel(status: BatteryStatus?, visibility: BatteryVisibility): BatteryModel? {
        if (status == null) {
            return null
        }
        val isLow: Boolean = status.percent <= LOW_BATTERY_PERCENT
        val isVisible: Boolean = when (visibility) {
            BatteryVisibility.NEVER -> false
            BatteryVisibility.ALWAYS -> true
            BatteryVisibility.CHARGING_OR_LOW -> status.isCharging || isLow
        }
        if (!isVisible) {
            return null
        }
        return BatteryModel(
            text = "${status.percent}%",
            percent = status.percent,
            isCharging = status.isCharging,
            isLow = isLow && !status.isCharging
        )
    }

    private fun displayPattern(reading: ClockReading, is24Hour: Boolean, showAmPm: Boolean): String {
        val skeleton: String = if (is24Hour) TIME_SKELETON_24_HOUR else TIME_SKELETON_12_HOUR
        val pattern: String = datePatternSource.bestPattern(skeleton, reading.locale)
        if (is24Hour) {
            return pattern
        }
        val twelveHour: String = pattern.replace(HOUR_REGEX, HOUR_PATTERN_12_HOUR)
        return if (showAmPm) twelveHour else twelveHour.replace(DAY_PERIOD_REGEX, "").trim()
    }

    private fun spokenPattern(is24Hour: Boolean): String = if (is24Hour) SPOKEN_TIME_PATTERN_24_HOUR else SPOKEN_TIME_PATTERN_12_HOUR

    private fun format(dateTime: ZonedDateTime, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern, locale).format(dateTime)
}
