package core.ui.widgets

import core.data.battery.BatteryStatus
import core.data.calendar.UpcomingEvent
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.TimeFormat
import core.data.testing.FakeDatePatternSource
import core.data.time.ClockReading
import core.data.time.IDatePatternSource
import core.ui.R
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderFormatterTest {
    private val inputReading: ClockReading = reading()

    @Test
    fun `formats each time format against the system 24-hour flag`() {
        assertEquals("2:05", time(HeaderSettings(timeFormat = TimeFormat.SYSTEM), isSystem24Hour = false))
        assertEquals("14:05", time(HeaderSettings(timeFormat = TimeFormat.SYSTEM), isSystem24Hour = true))
        assertEquals("2:05", time(HeaderSettings(timeFormat = TimeFormat.H12), isSystem24Hour = true))
        assertEquals("14:05", time(HeaderSettings(timeFormat = TimeFormat.H24), isSystem24Hour = false))
    }

    @Test
    fun `shows AM PM only in effective 12-hour time`() {
        val actualTwelveHour: HeaderModel = format(HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = true))
        assertEquals("2:05 PM", actualTwelveHour.time?.text)
        assertEquals("2:05 PM", actualTwelveHour.time?.spokenText)
        assertNull(actualTwelveHour.time?.amPmText)

        val actualTwentyFourHour: HeaderModel = format(HeaderSettings(timeFormat = TimeFormat.H24, showAmPm = true))
        assertEquals("14:05", actualTwentyFourHour.time?.text)
        assertEquals("14:05", actualTwentyFourHour.time?.spokenText)

        val actualSystemTwentyFourHour: HeaderModel = format(
            HeaderSettings(timeFormat = TimeFormat.SYSTEM, showAmPm = true),
            isSystem24Hour = true
        )
        assertEquals("14:05", actualSystemTwentyFourHour.time?.text)

        val actualTwelveHourWithoutMarker: HeaderModel = format(HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = false))
        assertEquals("2:05", actualTwelveHourWithoutMarker.time?.text)
        assertEquals("2:05 PM", actualTwelveHourWithoutMarker.time?.spokenText)
    }

    @Test
    fun `splits stacked time into hours minutes and AM PM`() {
        val actualTwelveHour: HeaderModel = format(
            HeaderSettings(clockStyle = ClockStyle.STACKED, timeFormat = TimeFormat.H12, showAmPm = true)
        )
        assertNull(actualTwelveHour.time?.text)
        assertEquals("2", actualTwelveHour.time?.hourText)
        assertEquals("05", actualTwelveHour.time?.minuteText)
        assertEquals("PM", actualTwelveHour.time?.amPmText)
        assertEquals("2:05 PM", actualTwelveHour.time?.spokenText)

        val actualTwentyFourHour: HeaderModel = format(
            HeaderSettings(clockStyle = ClockStyle.STACKED, timeFormat = TimeFormat.H24, showAmPm = true)
        )
        assertEquals("14", actualTwentyFourHour.time?.hourText)
        assertEquals("05", actualTwentyFourHour.time?.minuteText)
        assertNull(actualTwentyFourHour.time?.amPmText)
    }

    @Test
    fun `does not leak a locale day period prefix into stacked hours`() {
        val formatter: HeaderFormatter = HeaderFormatter(
            FakeDatePatternSource(mapOf("hm" to "a h:mm", "Hm" to "H:mm"))
        )
        val actualHeader: HeaderModel = formatter.format(
            id = 1L,
            reading = reading(locale = Locale.KOREAN),
            settings = HeaderSettings(clockStyle = ClockStyle.STACKED, timeFormat = TimeFormat.H12, showAmPm = true)
        )
        assertEquals("2", actualHeader.time?.hourText)
        assertEquals("05", actualHeader.time?.minuteText)
        assertEquals("오후", actualHeader.time?.amPmText)
    }

    @Test
    fun `keeps the locale day period position in 12-hour display`() {
        val formatter: HeaderFormatter = HeaderFormatter(
            FakeDatePatternSource(mapOf("hm" to "a h:mm", "Hm" to "H:mm"))
        )
        val actualWithMarker: HeaderModel = formatter.format(
            id = 1L,
            reading = reading(locale = Locale.KOREAN),
            settings = HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = true)
        )
        val actualWithoutMarker: HeaderModel = formatter.format(
            id = 1L,
            reading = reading(locale = Locale.KOREAN),
            settings = HeaderSettings(timeFormat = TimeFormat.H12, showAmPm = false)
        )
        assertEquals("오후 2:05", actualWithMarker.time?.text)
        assertEquals("2:05", actualWithoutMarker.time?.text)
    }

    @Test
    fun `speaks the time with the locale pattern`() {
        val mockPatternSource: FakeDatePatternSource = FakeDatePatternSource(
            overrides = mapOf("hm" to "h.mm a", "Hm" to "HH.mm")
        )

        val actualTwelveHour: HeaderModel = format(
            settings = HeaderSettings(timeFormat = TimeFormat.H12),
            datePatternSource = mockPatternSource
        )
        val actualTwentyFourHour: HeaderModel = format(
            settings = HeaderSettings(timeFormat = TimeFormat.H24),
            datePatternSource = mockPatternSource
        )

        assertEquals("2.05 PM", actualTwelveHour.time?.spokenText)
        assertEquals("14.05", actualTwentyFourHour.time?.spokenText)
    }

    @Test
    fun `requests and formats every date preset skeleton`() {
        val mockSkeletonSource: RecordingDatePatternSource = RecordingDatePatternSource()
        val formatter: HeaderFormatter = HeaderFormatter(mockSkeletonSource)
        DateFormatPreset.entries.forEach { preset -> formatter.formatDate(inputReading, preset) }
        assertEquals(DateFormatPreset.entries.map { preset -> preset.skeleton }, mockSkeletonSource.requestedSkeletons)
        assertEquals(
            listOf("Tuesday, October 20", "Tue, Oct 20", "October 20", "Tuesday 20", "Tuesday", "10/20/2026"),
            DateFormatPreset.entries.map { preset -> formatter.formatDate(inputReading, preset) }
        )
    }

    @Test
    fun `carries the selected style alignment and size into the model`() {
        val actualHeader: HeaderModel = format(
            HeaderSettings(
                clockStyle = ClockStyle.COMPACT,
                dateFormat = DateFormatPreset.WEEKDAY,
                alignment = HeaderAlignment.CENTER,
                size = HeaderSize.LARGE
            )
        )
        assertEquals(1L, actualHeader.id)
        assertEquals(ClockStyle.COMPACT, actualHeader.style)
        assertEquals(HeaderAlignment.CENTER, actualHeader.alignment)
        assertEquals(HeaderSize.LARGE, actualHeader.size)
        assertEquals("2:05", actualHeader.time?.text)
        assertEquals("Tuesday", actualHeader.date?.text)
    }

    @Test
    fun `hides the battery when its visibility is Never`() {
        val actualHeader: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.NEVER),
            battery = BatteryStatus(percent = 84, isCharging = true)
        )

        assertNull(actualHeader.battery)
    }

    @Test
    fun `always shows the battery when a status is available`() {
        val actualHeader: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS),
            battery = BatteryStatus(percent = 84, isCharging = false)
        )

        assertEquals("84%", actualHeader.battery?.text)
        assertFalse(actualHeader.battery?.isCharging == true)
        assertFalse(actualHeader.battery?.isLow == true)
        assertEquals(84, actualHeader.battery?.percent)
        assertEquals(R.string.battery_content_description, actualHeader.battery?.contentDescriptionRes)
    }

    @Test
    fun `hides the battery when Always is selected without a status`() {
        val actualHeader: HeaderModel = format(HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS))

        assertNull(actualHeader.battery)
    }

    @Test
    fun `shows the battery while charging or at or below the low threshold`() {
        val actualCharging: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.CHARGING_OR_LOW),
            battery = BatteryStatus(percent = 84, isCharging = true)
        )
        assertEquals("84%", actualCharging.battery?.text)
        assertTrue(actualCharging.battery?.isCharging == true)
        assertFalse(actualCharging.battery?.isLow == true)
        assertEquals(84, actualCharging.battery?.percent)
        assertEquals(R.string.battery_content_description_charging, actualCharging.battery?.contentDescriptionRes)

        val actualLow: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.CHARGING_OR_LOW),
            battery = BatteryStatus(percent = 20, isCharging = false)
        )
        assertEquals("20%", actualLow.battery?.text)
        assertTrue(actualLow.battery?.isLow == true)
    }

    @Test
    fun `hides the battery above the low threshold when not charging`() {
        val actualHeader: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.CHARGING_OR_LOW),
            battery = BatteryStatus(percent = 21, isCharging = false)
        )

        assertNull(actualHeader.battery)
    }

    @Test
    fun `does not mark a charging battery as low`() {
        val actualHeader: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS),
            battery = BatteryStatus(percent = 20, isCharging = true)
        )

        assertTrue(actualHeader.battery?.isCharging == true)
        assertFalse(actualHeader.battery?.isLow == true)
    }

    @Test
    fun `shows a full battery as charging`() {
        val actualHeader: HeaderModel = format(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS),
            battery = BatteryStatus(percent = 100, isCharging = true)
        )

        assertEquals("100%", actualHeader.battery?.text)
        assertTrue(actualHeader.battery?.isCharging == true)
        assertFalse(actualHeader.battery?.isLow == true)
        assertEquals(100, actualHeader.battery?.percent)
        assertEquals(R.string.battery_content_description_charging, actualHeader.battery?.contentDescriptionRes)
    }

    @Test
    fun `excludes events at the lookahead and now boundaries`() {
        val inputLookaheadEnd: UpcomingEvent = event(title = "At window end", begin = "2026-10-20T15:05:00Z", end = "2026-10-20T15:30:00Z")
        val inputEndedNow: UpcomingEvent = event(title = "Ends now", begin = "2026-10-20T13:00:00Z", end = "2026-10-20T14:05:00Z")
        val inputInside: UpcomingEvent = event(title = "Inside", begin = "2026-10-20T15:04:00Z", end = "2026-10-20T15:05:00Z")

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputLookaheadEnd, inputEndedNow, inputInside),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 1)
        )

        assertEquals(listOf("Inside · 15:04"), actualSelected.map { model -> model.text })
    }

    @Test
    fun `orders ongoing timed events before upcoming events`() {
        val inputUpcoming: UpcomingEvent = event(title = "Later", begin = "2026-10-20T14:06:00Z", end = "2026-10-20T14:30:00Z")
        val inputOngoingLater: UpcomingEvent = event(title = "Ongoing later", begin = "2026-10-20T14:00:00Z", end = "2026-10-20T16:00:00Z")
        val inputOngoingSooner: UpcomingEvent =
            event(title = "Ongoing sooner", begin = "2026-10-20T13:00:00Z", end = "2026-10-20T14:30:00Z")

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputUpcoming, inputOngoingLater, inputOngoingSooner),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 12, maxEvents = 3)
        )

        assertEquals(
            listOf("Ongoing sooner · until 14:30", "Ongoing later · until 16:00", "Later · 14:06"),
            actualSelected.map { model -> model.text }
        )
    }

    @Test
    fun `formats ongoing today tomorrow and untitled events`() {
        val inputToday: UpcomingEvent = event(title = "Standup", begin = "2026-10-20T14:00:00Z", end = "2026-10-20T15:00:00Z")
        val inputTomorrow: UpcomingEvent = event(title = "Release", begin = "2026-10-20T14:00:00Z", end = "2026-10-21T01:00:00Z")
        val inputUntitled: UpcomingEvent = event(title = "", begin = "2026-10-20T14:00:00Z", end = "2026-10-20T14:30:00Z")

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputToday, inputTomorrow, inputUntitled),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 12, maxEvents = 3)
        )

        assertEquals(
            listOf("(No title) · until 14:30", "Standup · until 15:00", "Release · until tomorrow 01:00"),
            actualSelected.map { model -> model.text }
        )
    }

    @Test
    fun `formats upcoming events by day`() {
        val inputToday: UpcomingEvent = event(title = "Today", begin = "2026-10-20T14:06:00Z", end = "2026-10-20T14:30:00Z")
        val inputTomorrow: UpcomingEvent = event(title = "Release", begin = "2026-10-21T14:06:00Z", end = "2026-10-21T14:30:00Z")
        val inputWeekday: UpcomingEvent = event(title = "Weekday", begin = "2026-10-22T09:30:00Z", end = "2026-10-22T10:00:00Z")

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputToday, inputTomorrow, inputWeekday),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 48, maxEvents = 3)
        )

        assertEquals(
            listOf("Today · 14:06", "Tomorrow · Release · 14:06", "Thursday · Weekday · 09:30"),
            actualSelected.map { model -> model.text }
        )
    }

    @Test
    fun `shows all-day events after timed events on the same day`() {
        val inputAllDay: UpcomingEvent = event(
            title = "Holiday",
            begin = "2026-10-20T00:00:00Z",
            end = "2026-10-21T00:00:00Z",
            allDay = true
        )
        val inputTimed: UpcomingEvent = event(title = "Standup", begin = "2026-10-20T14:06:00Z", end = "2026-10-20T14:30:00Z")

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputAllDay, inputTimed),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 12, includeAllDay = true, maxEvents = 2)
        )

        assertEquals(listOf("Standup · 14:06", "Today · Holiday"), actualSelected.map { model -> model.text })
    }

    @Test
    fun `hides all-day events when they are disabled`() {
        val inputAllDay: UpcomingEvent = event(
            title = "Holiday",
            begin = "2026-10-20T00:00:00Z",
            end = "2026-10-21T00:00:00Z",
            allDay = true
        )

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputAllDay),
            eventSettings = EventSettings(enabled = true, includeAllDay = false)
        )

        assertTrue(actualSelected.isEmpty())
    }

    @Test
    fun `expands a multi-day all-day event for each day it covers`() {
        val inputAllDay: UpcomingEvent = event(
            title = "Conference",
            begin = "2026-10-20T00:00:00Z",
            end = "2026-10-22T00:00:00Z",
            allDay = true
        )

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputAllDay),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 48, includeAllDay = true, maxEvents = 3)
        )

        assertEquals(listOf("Today · Conference", "Tomorrow · Conference"), actualSelected.map { model -> model.text })
    }

    @Test
    fun `filters excluded calendars and limits the event count`() {
        val inputExcluded: UpcomingEvent =
            event(title = "Hidden", begin = "2026-10-20T14:06:00Z", end = "2026-10-20T14:30:00Z", calendarId = 9L)
        val inputFirst: UpcomingEvent =
            event(title = "First", begin = "2026-10-20T14:06:00Z", end = "2026-10-20T14:30:00Z", calendarId = 1L)
        val inputSecond: UpcomingEvent =
            event(title = "Second", begin = "2026-10-20T14:07:00Z", end = "2026-10-20T14:30:00Z", calendarId = 1L)

        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(inputExcluded, inputFirst, inputSecond),
            eventSettings = EventSettings(
                enabled = true,
                lookaheadHours = 12,
                maxEvents = 1,
                excludedCalendarIds = setOf("9")
            )
        )

        assertEquals(listOf("First · 14:06"), actualSelected.map { model -> model.text })
    }

    @Test
    fun `carries the calendar color and event tap target into each line`() {
        val actualSelected: List<EventModel> = selectEvents(
            events = listOf(
                event(title = "Standup", begin = "2026-10-20T14:06:00Z", end = "2026-10-20T14:30:00Z", color = 0xFF00FF00.toInt())
            ),
            eventSettings = EventSettings(enabled = true)
        )

        val actualModel: EventModel = actualSelected.single()
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00FF00.toInt()), actualModel.color)
        assertEquals(R.string.open_event, actualModel.clickLabel)
        assertEquals("Standup", actualModel.clickLabelArg)
        assertEquals(
            HeaderTapTarget.OpenEvent(
                eventId = 1L,
                beginMillis = Instant.parse("2026-10-20T14:06:00Z").toEpochMilli(),
                endMillis = Instant.parse("2026-10-20T14:30:00Z").toEpochMilli(),
                isWorkProfile = false
            ),
            actualModel.tapTarget
        )
    }

    private fun selectEvents(events: List<UpcomingEvent>, eventSettings: EventSettings): List<EventModel> =
        HeaderFormatter(FakeDatePatternSource()).selectEvents(
            events = events,
            eventSettings = eventSettings,
            headerSettings = HeaderSettings(timeFormat = TimeFormat.H24),
            reading = inputReading
        )

    private fun event(
        title: String = "Event",
        begin: String = "2026-10-20T14:06:00Z",
        end: String = "2026-10-20T14:30:00Z",
        allDay: Boolean = false,
        calendarId: Long = 1L,
        color: Int = 0
    ): UpcomingEvent = UpcomingEvent(
        eventId = 1L,
        title = title,
        beginMillis = Instant.parse(begin).toEpochMilli(),
        endMillis = Instant.parse(end).toEpochMilli(),
        isAllDay = allDay,
        calendarId = calendarId,
        color = color,
        isWorkProfile = false
    )

    private fun format(
        settings: HeaderSettings,
        isSystem24Hour: Boolean = false,
        battery: BatteryStatus? = null,
        datePatternSource: IDatePatternSource = FakeDatePatternSource()
    ): HeaderModel = HeaderFormatter(datePatternSource).format(
        id = 1L,
        reading = reading(isSystem24Hour = isSystem24Hour),
        settings = settings,
        battery = battery
    )

    private fun time(settings: HeaderSettings, isSystem24Hour: Boolean): String? = format(settings, isSystem24Hour).time?.text

    private fun reading(locale: Locale = Locale.US, isSystem24Hour: Boolean = false): ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = locale,
        isSystem24Hour = isSystem24Hour
    )

    private class RecordingDatePatternSource : IDatePatternSource {
        val requestedSkeletons: MutableList<String> = mutableListOf()

        override fun bestPattern(skeleton: String, locale: Locale): String {
            requestedSkeletons.add(skeleton)
            return when (skeleton) {
                "EEEEMMMMd" -> "EEEE, MMMM d"
                "EEEMMMd" -> "EEE, MMM d"
                "MMMMd" -> "MMMM d"
                "EEEEd" -> "EEEE d"
                "EEEE" -> "EEEE"
                "yMd" -> "M/d/y"
                else -> skeleton
            }
        }
    }
}
