package core.ui.widgets

import android.content.res.Resources
import core.data.battery.BatteryStatus
import core.data.calendar.GroupedEvents
import core.data.calendar.UpcomingEvent
import core.data.calendar.groupUpcomingEvents
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.is24Hour
import core.data.prefs.isWeekday
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import core.ui.R
import core.ui.calendar.ClockTextFormatter
import core.ui.calendar.EventTextFormatter
import core.ui.calendar.HOUR_PATTERN_12_HOUR
import core.ui.calendar.HOUR_PATTERN_24_HOUR
import core.ui.calendar.eventColor
import core.ui.calendar.openEventTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MINUTE_PATTERN: String = "mm"
private const val DAY_PERIOD_PATTERN: String = "a"
private const val PREVIEW_ID: Long = 0L
private const val LOW_BATTERY_PERCENT: Int = 20

/** Pure mapping from Header settings and the current clock reading to the values the Header renders. */
class HeaderFormatter(datePatternSource: IDatePatternSource, resources: Resources? = null) {
    private val clockText: ClockTextFormatter = ClockTextFormatter(datePatternSource = datePatternSource)
    private val eventText: EventTextFormatter = EventTextFormatter(datePatternSource = datePatternSource, resources = resources)

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
            time = createTimeModel(
                dateTime = dateTime,
                reading = reading,
                is24Hour = is24Hour,
                settings = settings
            ),
            date = if (settings.dateVisible) {
                createDateModel(
                    dateTime = dateTime,
                    reading = reading,
                    preset = settings.dateFormat,
                    style = settings.clockStyle
                )
            } else {
                null
            },
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

    fun formatDate(preset: DateFormatPreset, reading: ClockReading): String =
        clockText.formatDate(dateTime = reading.instant.atZone(reading.zoneId), preset = preset, reading = reading)

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
        val grouped: GroupedEvents = groupUpcomingEvents(
            events = events,
            eventSettings = eventSettings,
            now = reading.instant,
            zoneId = reading.zoneId
        )
        val today: LocalDate = reading.instant.atZone(reading.zoneId).toLocalDate()
        val ongoing: List<EventModel> = grouped.ongoing.map { event ->
            createOngoingEvent(event = event, headerSettings = headerSettings, reading = reading)
        }
        val ordered: List<EventModel> = grouped.days.flatMap { day ->
            grouped.timedByDay[day].orEmpty().map { event ->
                createUpcomingEvent(event = event, headerSettings = headerSettings, reading = reading, today = today)
            } + grouped.allDayByDay[day].orEmpty().map { event ->
                createAllDayEvent(event = event, day = day, today = today, reading = reading)
            }
        }
        return (ongoing + ordered).take(eventSettings.resolvedMaxEvents)
    }

    private fun createOngoingEvent(event: UpcomingEvent, headerSettings: HeaderSettings, reading: ClockReading): EventModel {
        val untilText: String = eventText.ongoingText(event = event, reading = reading, headerSettings = headerSettings)
        val suffix: String = EVENT_SEPARATOR + untilText
        return createEventModel(event = event, text = eventText.title(event) + suffix, suffix = suffix)
    }

    private fun createUpcomingEvent(
        event: UpcomingEvent,
        headerSettings: HeaderSettings,
        reading: ClockReading,
        today: LocalDate
    ): EventModel {
        val beginDateTime: ZonedDateTime = Instant.ofEpochMilli(event.beginMillis).atZone(reading.zoneId)
        val beginDate: LocalDate = beginDateTime.toLocalDate()
        val time: String = eventText.timeText(dateTime = beginDateTime, reading = reading, headerSettings = headerSettings)
        val prefix: String = if (beginDate == today) {
            ""
        } else {
            eventText.dayLabel(date = beginDate, today = today, reading = reading) + EVENT_SEPARATOR
        }
        val suffix: String = EVENT_SEPARATOR + time
        return createEventModel(event = event, text = prefix + eventText.title(event) + suffix, suffix = suffix)
    }

    private fun createAllDayEvent(event: UpcomingEvent, day: LocalDate, today: LocalDate, reading: ClockReading): EventModel =
        createEventModel(
            event = event,
            text = eventText.dayLabel(date = day, today = today, reading = reading) + EVENT_SEPARATOR + eventText.title(event)
        )

    private fun createEventModel(event: UpcomingEvent, text: String, suffix: String = ""): EventModel = EventModel(
        text = text,
        suffix = suffix,
        spokenText = text.replace(EVENT_SEPARATOR, SPOKEN_SEPARATOR),
        color = event.eventColor(),
        tapTarget = HeaderTapTarget.OpenEvent(target = event.openEventTarget()),
        clickLabel = R.string.open_event,
        clickLabelArg = eventText.title(event)
    )

    private fun createTimeModel(
        dateTime: ZonedDateTime,
        reading: ClockReading,
        is24Hour: Boolean,
        settings: HeaderSettings
    ): HeaderTimeModel? {
        val showTime: Boolean = settings.clockVisible
        val weekdayText: String? = if (settings.clockStyle.isWeekday) {
            formatDate(preset = DateFormatPreset.WEEKDAY, reading = reading)
        } else {
            null
        }
        if (weekdayText == null && !showTime) {
            return null
        }
        val timeSpokenText: String? = if (showTime) {
            clockText.format(
                dateTime = dateTime,
                pattern = clockText.timePattern(reading = reading, is24Hour = is24Hour),
                locale = reading.locale
            )
        } else {
            null
        }
        val tapTarget: HeaderTapTarget = settings.clockStyle.timeDefaultTapTarget()
        val common: HeaderTimeModel = HeaderTimeModel(
            text = null,
            weekdayText = weekdayText,
            hourText = null,
            minuteText = null,
            amPmText = null,
            spokenText = listOfNotNull(weekdayText, timeSpokenText).joinToString(SPOKEN_SEPARATOR),
            tapTarget = tapTarget,
            clickLabel = tapTarget.clickLabel()
        )
        return when {
            !showTime -> common

            settings.clockStyle == ClockStyle.STACKED -> common.copy(
                hourText = clockText.format(
                    dateTime = dateTime,
                    pattern = if (is24Hour) HOUR_PATTERN_24_HOUR else HOUR_PATTERN_12_HOUR,
                    locale = reading.locale
                ),
                minuteText = clockText.format(dateTime = dateTime, pattern = MINUTE_PATTERN, locale = reading.locale),
                amPmText = if (!is24Hour && settings.showAmPm) {
                    clockText.format(dateTime = dateTime, pattern = DAY_PERIOD_PATTERN, locale = reading.locale)
                } else {
                    null
                }
            )

            else -> common.copy(
                text = clockText.format(
                    dateTime = dateTime,
                    pattern = clockText.displayPattern(reading = reading, is24Hour = is24Hour, showAmPm = !is24Hour && settings.showAmPm),
                    locale = reading.locale
                )
            )
        }
    }

    private fun createDateModel(
        dateTime: ZonedDateTime,
        reading: ClockReading,
        preset: DateFormatPreset,
        style: ClockStyle
    ): HeaderLineModel {
        val tapTarget: HeaderTapTarget = style.dateDefaultTapTarget(atEpochMillis = reading.instant.toEpochMilli())
        // The Weekday time line already speaks the weekday.
        val spokenStyle: FormatStyle = if (style.isWeekday) FormatStyle.LONG else FormatStyle.FULL
        return HeaderLineModel(
            text = clockText.formatDate(dateTime = dateTime, preset = preset, reading = reading),
            spokenText = DateTimeFormatter.ofLocalizedDate(spokenStyle).withLocale(reading.locale).format(dateTime),
            tapTarget = tapTarget,
            clickLabel = tapTarget.clickLabel()
        )
    }

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
}
