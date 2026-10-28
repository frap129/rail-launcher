package core.data.testing

import core.data.calendar.CalendarInfo
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * A MockK [ICalendarEventSource] reading [permission], [events] and [calendars] while permission is granted,
 * and recording every lookahead query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createCalendarEventSourceMock(
    permission: MutableStateFlow<Boolean> = MutableStateFlow(false),
    onRefresh: () -> Unit = {},
    events: Flow<List<UpcomingEvent>> = emptyFlow(),
    calendars: Flow<List<CalendarInfo>> = MutableStateFlow(emptyList()),
    queriedLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
): ICalendarEventSource {
    val mockCalendarEventSource: ICalendarEventSource = mockk()
    every { mockCalendarEventSource.hasPermission } returns permission
    every { mockCalendarEventSource.calendars } returns permission.flatMapLatest { granted ->
        if (granted) calendars else flowOf(emptyList())
    }
    every { mockCalendarEventSource.events(any()) } answers {
        queriedLookaheadHours.update { current -> current + firstArg<Int>() }
        events
    }
    every { mockCalendarEventSource.refreshPermission() } answers { onRefresh() }
    return mockCalendarEventSource
}

/** A [createCalendarEventSourceMock] with permission granted and [calendars] exposed. */
fun createGrantedCalendarEventSourceMock(
    calendars: List<CalendarInfo> = emptyList(),
    events: Flow<List<UpcomingEvent>> = emptyFlow(),
    queriedLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
): ICalendarEventSource = createCalendarEventSourceMock(
    permission = MutableStateFlow(true),
    events = events,
    calendars = MutableStateFlow(calendars),
    queriedLookaheadHours = queriedLookaheadHours
)
