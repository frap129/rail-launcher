package core.ui.agenda

import androidx.compose.ui.graphics.Color
import core.data.calendar.AgendaDay
import core.data.calendar.UpcomingEvent
import core.data.prefs.HeaderSettings
import core.data.prefs.TimeFormat
import core.data.testing.createDatePatternSourceMock
import core.data.testing.createUpcomingEvent
import core.data.time.ClockReading
import core.ui.calendar.OpenEventTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaFormatterTest {
    private val inputReading: ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = true
    )
    private val formatter: AgendaFormatter = AgendaFormatter(createDatePatternSourceMock())

    @Test
    fun `labels days as today tomorrow and the weekday`() {
        val inputDays: List<AgendaDay> = listOf(
            AgendaDay(date = LocalDate.of(2026, 10, 20), events = listOf(createUpcomingEvent(title = "Standup"))),
            AgendaDay(date = LocalDate.of(2026, 10, 21), events = listOf(createUpcomingEvent(title = "Release"))),
            AgendaDay(date = LocalDate.of(2026, 10, 22), events = listOf(createUpcomingEvent(title = "Review")))
        )

        val actualDays: List<AgendaDayModel> = formatter.format(
            days = inputDays,
            reading = inputReading,
            headerSettings = HeaderSettings(timeFormat = TimeFormat.SYSTEM)
        )

        assertEquals(listOf("Today", "Tomorrow", "Thursday, October 22"), actualDays.map { day -> day.label })
    }

    @Test
    fun `formats timed events with the Header time and all-day events without one`() {
        val inputTimed: UpcomingEvent = createUpcomingEvent(title = "Standup", begin = "2026-10-20T15:00:00Z", end = "2026-10-20T15:30:00Z")
        val inputAllDay: UpcomingEvent = createUpcomingEvent(
            title = "Holiday",
            begin = "2026-10-20T00:00:00Z",
            end = "2026-10-21T00:00:00Z",
            allDay = true
        )
        val inputDays: List<AgendaDay> = listOf(
            AgendaDay(date = LocalDate.of(2026, 10, 20), events = listOf(inputTimed, inputAllDay))
        )

        val actualDay: AgendaDayModel = formatter.format(
            days = inputDays,
            reading = inputReading,
            headerSettings = HeaderSettings(timeFormat = TimeFormat.SYSTEM)
        ).single()

        val actualTimed: AgendaEventModel = actualDay.events[0]
        val actualAllDay: AgendaEventModel = actualDay.events[1]
        assertEquals("Standup", actualTimed.title)
        assertEquals("15:00", actualTimed.timeText)
        assertEquals("Holiday", actualAllDay.title)
        assertNull(actualAllDay.timeText)
    }

    @Test
    fun `formats an ongoing event with its until text`() {
        val inputOvernightReading: ClockReading = ClockReading(
            instant = Instant.parse("2026-10-20T00:30:00Z"),
            zoneId = ZoneId.of("UTC"),
            locale = Locale.US,
            isSystem24Hour = true
        )
        val inputOngoing: UpcomingEvent = createUpcomingEvent(
            title = "Night shift",
            begin = "2026-10-19T23:00:00Z",
            end = "2026-10-20T01:00:00Z"
        )

        val actualEvent: AgendaEventModel = formatter.format(
            days = listOf(AgendaDay(date = LocalDate.of(2026, 10, 20), events = listOf(inputOngoing))),
            reading = inputOvernightReading,
            headerSettings = HeaderSettings()
        ).single().events.single()

        assertEquals("until 01:00", actualEvent.timeText)
    }

    @Test
    fun `carries the event color and its calendar target`() {
        val inputEvent: UpcomingEvent = createUpcomingEvent(
            title = "Standup",
            begin = "2026-10-20T15:00:00Z",
            end = "2026-10-20T15:30:00Z",
            color = 0xFF00FF00.toInt(),
            isWorkProfile = true
        )

        val actualEvent: AgendaEventModel = formatter.format(
            days = listOf(AgendaDay(date = LocalDate.of(2026, 10, 20), events = listOf(inputEvent))),
            reading = inputReading,
            headerSettings = HeaderSettings()
        ).single().events.single()

        assertEquals(Color(0xFF00FF00.toInt()), actualEvent.color)
        assertEquals(
            OpenEventTarget(
                eventId = 1L,
                beginMillis = Instant.parse("2026-10-20T15:00:00Z").toEpochMilli(),
                endMillis = Instant.parse("2026-10-20T15:30:00Z").toEpochMilli(),
                isWorkProfile = true
            ),
            actualEvent.tapTarget
        )
    }
}
