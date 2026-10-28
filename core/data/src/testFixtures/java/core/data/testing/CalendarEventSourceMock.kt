package core.data.testing

import core.data.calendar.ICalendarEventSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [ICalendarEventSource] reading [permission] and running [onRefresh] when asked to refresh. */
fun createCalendarEventSourceMock(
    permission: MutableStateFlow<Boolean> = MutableStateFlow(false),
    onRefresh: () -> Unit = {}
): ICalendarEventSource {
    val mockCalendarEventSource: ICalendarEventSource = mockk()
    every { mockCalendarEventSource.hasPermission } returns permission
    every { mockCalendarEventSource.refreshPermission() } answers { onRefresh() }
    return mockCalendarEventSource
}
