package feature.launcher.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.calendar.CalendarPermissionDecision
import core.data.calendar.ICalendarEventSource
import core.data.calendar.ICalendarPermissionGate
import core.data.calendar.UpcomingEvent
import core.data.calendar.buildAgendaDays
import core.data.calendar.effectiveLookaheadHours
import core.data.prefs.EventSettings
import core.data.time.IClockSource
import core.data.widgets.IWidgetSettingsRepository
import core.ui.agenda.AgendaDayModel
import core.ui.agenda.AgendaFormatter
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The agenda sheet's state: whether it is open, whether it is still loading, whether calendar
 * access is granted and the days it lists.
 */
data class AgendaUiState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
    val days: List<AgendaDayModel> = emptyList()
)

private data class AgendaContentRequest(val lookaheadHours: Int, val hasPermission: Boolean)

private sealed interface AgendaContent {
    data object Denied : AgendaContent

    data object Loading : AgendaContent

    data class Loaded(val events: List<UpcomingEvent>) : AgendaContent
}

/**
 * The launcher's agenda sheet. It reads the calendar only while the sheet is open, and uses the
 * Event settings' lookahead, calendars and all-day option whatever the Header shows. Calendar
 * access goes through the shared permission gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(
    private val clockSource: IClockSource,
    private val settingsRepository: IWidgetSettingsRepository,
    private val calendarEventSource: ICalendarEventSource,
    private val calendarPermissionGate: ICalendarPermissionGate,
    private val formatter: AgendaFormatter
) : ViewModel() {
    private val isOpenState: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val eventSettings: Flow<EventSettings> = settingsRepository.eventSettings

    private val eventChannel: EventChannel<AgendaEvent> = EventChannel()

    val events: Flow<AgendaEvent> = eventChannel.flow

    private val agendaContent: Flow<AgendaContent> = combine(
        eventSettings,
        calendarEventSource.hasPermission
    ) { settings, hasPermission ->
        AgendaContentRequest(lookaheadHours = settings.effectiveLookaheadHours(), hasPermission = hasPermission)
    }.distinctUntilChanged().flatMapLatest { request ->
        if (request.hasPermission) {
            calendarEventSource.events(lookaheadHours = request.lookaheadHours)
                .map<List<UpcomingEvent>, AgendaContent> { events -> AgendaContent.Loaded(events = events) }
                .onStart { emit(AgendaContent.Loading) }
        } else {
            flowOf(AgendaContent.Denied)
        }
    }

    val uiState: StateFlow<AgendaUiState> = isOpenState.flatMapLatest { isOpen ->
        if (!isOpen) {
            flowOf(AgendaUiState())
        } else {
            combine(
                clockSource.readings,
                eventSettings,
                settingsRepository.headerSettings,
                agendaContent
            ) { reading, settings, headerSettings, content ->
                AgendaUiState(
                    isOpen = true,
                    isLoading = content is AgendaContent.Loading,
                    hasPermission = content !is AgendaContent.Denied,
                    days = when (content) {
                        is AgendaContent.Loaded -> formatter.format(
                            days = buildAgendaDays(
                                events = content.events,
                                eventSettings = settings,
                                now = reading.instant,
                                zoneId = reading.zoneId
                            ),
                            reading = reading,
                            headerSettings = headerSettings
                        )

                        AgendaContent.Denied,
                        AgendaContent.Loading -> emptyList()
                    }
                )
            }.onStart { emit(AgendaUiState(isOpen = true, isLoading = true)) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = AgendaUiState()
    )

    fun open() {
        isOpenState.value = true
    }

    fun close() {
        isOpenState.value = false
    }

    fun onCalendarPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        viewModelScope.launch {
            val decision: CalendarPermissionDecision = calendarPermissionGate.onPermissionResult(
                granted = granted,
                canAskAgain = canAskAgain
            )
            if (decision is CalendarPermissionDecision.Denied && decision.permanentlyDenied) {
                eventChannel.send(AgendaEvent.ShowCalendarPermissionDenied)
            }
        }
    }

    /** Rechecks calendar access, so a grant made outside the sheet loads the agenda on resume. */
    fun refreshCalendarPermission() {
        calendarPermissionGate.refreshPermission()
    }
}
