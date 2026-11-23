package core.data.calendar

import core.data.prefs.EventSettings
import core.data.testing.createUpcomingEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

class AgendaProjectionTest {
    @Test
    fun `groups timed events by their local day across midnight`() {
        val inputNow: Instant = Instant.parse("2026-10-20T21:50:00Z")
        val inputLateNight: UpcomingEvent = createUpcomingEvent(
            title = "Late night",
            begin = "2026-10-20T21:55:00Z",
            end = "2026-10-20T22:20:00Z"
        )
        val inputTonight: UpcomingEvent = createUpcomingEvent(
            title = "Tonight",
            begin = "2026-10-20T22:30:00Z",
            end = "2026-10-20T23:00:00Z"
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputTonight, inputLateNight),
            eventSettings = EventSettings(lookaheadHours = 12),
            now = inputNow,
            zoneId = BERLIN
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), actualDays.map { day -> day.date })
        assertEquals(listOf("Late night"), actualDays[0].events.map { event -> event.title })
        assertEquals(listOf("Tonight"), actualDays[1].events.map { event -> event.title })
    }

    @Test
    fun `groups all-day events by their UTC days whatever the time zone`() {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val inputAllDay: UpcomingEvent = createUpcomingEvent(
            title = "Conference",
            begin = "2026-10-20T00:00:00Z",
            end = "2026-10-22T00:00:00Z",
            allDay = true
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputAllDay),
            eventSettings = EventSettings(lookaheadHours = 48, includeAllDay = true),
            now = inputNow,
            zoneId = NEW_YORK
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), actualDays.map { day -> day.date })
    }

    @Test
    fun `starts a multi-day all-day event from today when it began earlier`() {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val inputAllDay: UpcomingEvent = createUpcomingEvent(
            title = "Conference",
            begin = "2026-10-19T00:00:00Z",
            end = "2026-10-22T00:00:00Z",
            allDay = true
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputAllDay),
            eventSettings = EventSettings(lookaheadHours = 48, includeAllDay = true),
            now = inputNow,
            zoneId = BERLIN
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), actualDays.map { day -> day.date })
    }

    @Test
    fun `lists ongoing events under today in begin order`() {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val inputAllHands: UpcomingEvent = createUpcomingEvent(
            title = "All hands",
            begin = "2026-10-19T23:00:00Z",
            end = "2026-10-20T15:00:00Z"
        )
        val inputStandup: UpcomingEvent = createUpcomingEvent(
            title = "Standup",
            begin = "2026-10-20T13:00:00Z",
            end = "2026-10-20T14:30:00Z"
        )
        val inputRelease: UpcomingEvent = createUpcomingEvent(
            title = "Release",
            begin = "2026-10-20T16:00:00Z",
            end = "2026-10-20T17:00:00Z"
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputRelease, inputStandup, inputAllHands),
            eventSettings = EventSettings(lookaheadHours = 12),
            now = inputNow,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20)), actualDays.map { day -> day.date })
        assertEquals(listOf("All hands", "Standup", "Release"), actualDays.single().events.map { event -> event.title })
    }

    @Test
    fun `excludes events at the lookahead cut-off`() {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val inputAtCutOff: UpcomingEvent = createUpcomingEvent(
            title = "At cut-off",
            begin = "2026-10-20T15:05:00Z",
            end = "2026-10-20T15:30:00Z"
        )
        val inputInside: UpcomingEvent = createUpcomingEvent(
            title = "Inside",
            begin = "2026-10-20T15:04:00Z",
            end = "2026-10-20T15:20:00Z"
        )
        val inputAllDayAfter: UpcomingEvent = createUpcomingEvent(
            title = "Tomorrow",
            begin = "2026-10-21T00:00:00Z",
            end = "2026-10-22T00:00:00Z",
            allDay = true
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputAtCutOff, inputInside, inputAllDayAfter),
            eventSettings = EventSettings(lookaheadHours = 1, includeAllDay = true),
            now = inputNow,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 20)), actualDays.map { day -> day.date })
        assertEquals(listOf("Inside"), actualDays.single().events.map { event -> event.title })
    }

    @Test
    fun `filters the calendars the Header filters including work calendars`() {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val inputPersonal: UpcomingEvent = createUpcomingEvent(
            title = "Personal",
            begin = "2026-10-20T15:00:00Z",
            end = "2026-10-20T15:30:00Z",
            calendarId = 1L
        )
        val inputExcluded: UpcomingEvent = createUpcomingEvent(
            title = "Excluded",
            begin = "2026-10-20T15:00:00Z",
            end = "2026-10-20T15:30:00Z",
            calendarId = 9L
        )
        val inputExcludedWork: UpcomingEvent = createUpcomingEvent(
            title = "Excluded work",
            begin = "2026-10-20T15:00:00Z",
            end = "2026-10-20T15:30:00Z",
            calendarId = 5L,
            isWorkProfile = true
        )
        val inputWork: UpcomingEvent = createUpcomingEvent(
            title = "Work",
            begin = "2026-10-20T15:00:00Z",
            end = "2026-10-20T15:30:00Z",
            calendarId = 6L,
            isWorkProfile = true
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputPersonal, inputExcluded, inputExcludedWork, inputWork),
            eventSettings = EventSettings(lookaheadHours = 12, excludedCalendarIds = setOf("9", "w:5")),
            now = inputNow,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(listOf("Personal", "Work"), actualDays.single().events.map { event -> event.title })
    }

    @Test
    fun `leaves all-day events out when they are disabled`() {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val inputAllDay: UpcomingEvent = createUpcomingEvent(
            title = "Holiday",
            begin = "2026-10-20T00:00:00Z",
            end = "2026-10-21T00:00:00Z",
            allDay = true
        )

        val actualDays: List<AgendaDay> = buildAgendaDays(
            events = listOf(inputAllDay),
            eventSettings = EventSettings(lookaheadHours = 12, includeAllDay = false),
            now = inputNow,
            zoneId = ZoneId.of("UTC")
        )

        assertTrue(actualDays.isEmpty())
    }
}
