package core.data.calendar

import core.data.prefs.EventSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One local day of the agenda: its date and the events happening on it. */
data class AgendaDay(val date: LocalDate, val events: List<UpcomingEvent>)

/**
 * The upcoming events the Header and the agenda share: the timed events still running, the
 * upcoming timed events by local day, and each day an all-day event covers.
 */
data class GroupedEvents(
    val ongoing: List<UpcomingEvent>,
    val timedByDay: Map<LocalDate, List<UpcomingEvent>>,
    val allDayByDay: Map<LocalDate, List<UpcomingEvent>>
) {
    /** Every day with an event, earliest first. */
    val days: List<LocalDate>
        get() = (timedByDay.keys + allDayByDay.keys).sorted()
}

/**
 * Filters [events] with the Calendar filters and lookahead the Header and the agenda share, then
 * groups them by local day. An all-day event covers every day from today to the day before its end.
 */
fun groupUpcomingEvents(events: List<UpcomingEvent>, eventSettings: EventSettings, now: Instant, zoneId: ZoneId): GroupedEvents {
    val nowMillis: Long = now.toEpochMilli()
    val windowEndMillis: Long = nowMillis + Duration.ofHours(eventSettings.effectiveLookaheadHours().toLong()).toMillis()
    val today: LocalDate = now.atZone(zoneId).toLocalDate()
    val excludedCalendarIds: Set<String> = eventSettings.excludedCalendarIds
    val timedEvents: List<UpcomingEvent> = events.filter { event ->
        !event.isAllDay &&
            event.calendarKey !in excludedCalendarIds &&
            event.endMillis > nowMillis &&
            event.beginMillis < windowEndMillis
    }
    val ongoing: List<UpcomingEvent> = timedEvents
        .filter { event -> event.beginMillis <= nowMillis }
        .sortedBy { event -> event.endMillis }
    val timedByDay: Map<LocalDate, List<UpcomingEvent>> = timedEvents
        .filter { event -> event.beginMillis > nowMillis }
        .sortedBy { event -> event.beginMillis }
        .groupBy { event -> Instant.ofEpochMilli(event.beginMillis).atZone(zoneId).toLocalDate() }
    val allDayByDay: Map<LocalDate, List<UpcomingEvent>> = if (eventSettings.includeAllDay) {
        events.filter { event ->
            event.isAllDay && event.calendarKey !in excludedCalendarIds && allDayEndDate(event) > today
        }.flatMap { event ->
            allDayDates(event = event, today = today, windowEndMillis = windowEndMillis, zoneId = zoneId).map { date -> date to event }
        }.groupBy(keySelector = { (date, _) -> date }, valueTransform = { (_, event) -> event })
    } else {
        emptyMap()
    }
    return GroupedEvents(ongoing = ongoing, timedByDay = timedByDay, allDayByDay = allDayByDay)
}

/**
 * The agenda's days within the Event settings' lookahead, with the same filters as the Header.
 * Ongoing events join today, and each day lists its timed events before its all-day events.
 */
fun buildAgendaDays(events: List<UpcomingEvent>, eventSettings: EventSettings, now: Instant, zoneId: ZoneId): List<AgendaDay> {
    val grouped: GroupedEvents = groupUpcomingEvents(
        events = events,
        eventSettings = eventSettings,
        now = now,
        zoneId = zoneId
    )
    val today: LocalDate = now.atZone(zoneId).toLocalDate()
    val days: List<LocalDate> = (grouped.days + if (grouped.ongoing.isEmpty()) emptyList() else listOf(today))
        .distinct()
        .sorted()
    return days.map { day ->
        val ongoing: List<UpcomingEvent> = if (day == today) grouped.ongoing else emptyList()
        val timed: List<UpcomingEvent> = grouped.timedByDay[day].orEmpty()
        AgendaDay(
            date = day,
            events = (ongoing + timed).sortedBy { event -> event.beginMillis } + grouped.allDayByDay[day].orEmpty()
        )
    }
}

private fun allDayDates(event: UpcomingEvent, today: LocalDate, windowEndMillis: Long, zoneId: ZoneId): List<LocalDate> {
    val firstDay: LocalDate = maxOf(allDayBeginDate(event), today)
    val lastDay: LocalDate = allDayEndDate(event).minusDays(1)
    return generateSequence(firstDay) { day -> day.plusDays(1) }
        .takeWhile { day -> !day.isAfter(lastDay) }
        .filter { day -> day.atStartOfDay(zoneId).toInstant().toEpochMilli() < windowEndMillis }
        .toList()
}

/** All-day events store their midnight bounds in UTC, so their days are read in UTC. */
private fun allDayBeginDate(event: UpcomingEvent): LocalDate = Instant.ofEpochMilli(event.beginMillis).atZone(ZoneOffset.UTC).toLocalDate()

private fun allDayEndDate(event: UpcomingEvent): LocalDate = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC).toLocalDate()
