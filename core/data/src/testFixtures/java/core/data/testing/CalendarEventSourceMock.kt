package core.data.testing

import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update

/** A MockK [ICalendarEventSource] reading [permission] and [events], and recording every lookahead query. */
fun createCalendarEventSourceMock(
    permission: MutableStateFlow<Boolean> = MutableStateFlow(false),
    onRefresh: () -> Unit = {},
    events: Flow<List<UpcomingEvent>> = emptyFlow(),
    queriedLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
): ICalendarEventSource {
    val mockCalendarEventSource: ICalendarEventSource = mockk()
    every { mockCalendarEventSource.hasPermission } returns permission
    every { mockCalendarEventSource.events(any()) } answers {
        queriedLookaheadHours.update { current -> current + firstArg<Int>() }
        events
    }
    every { mockCalendarEventSource.refreshPermission() } answers { onRefresh() }
    return mockCalendarEventSource
}
