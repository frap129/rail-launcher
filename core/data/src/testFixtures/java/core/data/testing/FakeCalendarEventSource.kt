package core.data.testing

import core.data.calendar.CalendarInfo
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

class FakeCalendarEventSource(
    hasPermission: Boolean = true,
    events: List<UpcomingEvent> = emptyList(),
    calendars: List<CalendarInfo> = emptyList()
) : ICalendarEventSource {
    private val permission: MutableStateFlow<Boolean> = MutableStateFlow(hasPermission)
    private val eventValues: MutableStateFlow<List<UpcomingEvent>> = MutableStateFlow(events)
    private val calendarValues: MutableStateFlow<List<CalendarInfo>> = MutableStateFlow(calendars)
    private val subscribers: MutableStateFlow<Int> = MutableStateFlow(0)

    val activeSubscribers: Int get() = subscribers.value
    val queriedWindowEnds: MutableList<Long> = mutableListOf()
    var refreshCount: Int = 0
        private set
    private var grantOnRefresh: Boolean = false

    override val hasPermission: StateFlow<Boolean> = permission.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val calendars: Flow<List<CalendarInfo>> = permission.flatMapLatest { granted ->
        if (granted) calendarValues else flowOf(emptyList())
    }

    override fun events(windowEndMillis: Long): Flow<List<UpcomingEvent>> {
        queriedWindowEnds.add(windowEndMillis)
        return eventValues
            .onStart { subscribers.update { count -> count + 1 } }
            .onCompletion { subscribers.update { count -> count - 1 } }
    }

    fun emitEvents(events: List<UpcomingEvent>) {
        eventValues.value = events
    }

    fun setPermission(granted: Boolean) {
        permission.value = granted
    }

    fun grantOnNextRefresh() {
        grantOnRefresh = true
    }

    override fun refreshPermission() {
        refreshCount++
        if (grantOnRefresh) {
            grantOnRefresh = false
            permission.value = true
        }
    }
}
