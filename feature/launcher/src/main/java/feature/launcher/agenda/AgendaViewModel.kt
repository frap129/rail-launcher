package feature.launcher.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import core.data.calendar.buildAgendaDays
import core.data.calendar.effectiveLookaheadHours
import core.data.prefs.EventSettings
import core.data.time.IClockSource
import core.data.widgets.IWidgetSettingsRepository
import core.ui.agenda.AgendaDayModel
import core.ui.agenda.AgendaFormatter
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** The agenda sheet's state: whether it is open, whether it is still loading and the days it lists. */
data class AgendaUiState(val isOpen: Boolean = false, val isLoading: Boolean = false, val days: List<AgendaDayModel> = emptyList())

private data class AgendaEventsRequest(val lookaheadHours: Int, val hasPermission: Boolean)

/**
 * The launcher's agenda sheet. It reads the calendar only while the sheet is open, and uses the
 * Event settings' lookahead, calendars and all-day option whatever the Header shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(
    private val clockSource: IClockSource,
    private val settingsRepository: IWidgetSettingsRepository,
    private val calendarEventSource: ICalendarEventSource,
    private val formatter: AgendaFormatter
) : ViewModel() {
    private val isOpenState: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val eventSettings: Flow<EventSettings> = settingsRepository.eventSettings

    private val agendaEvents: Flow<List<UpcomingEvent>> = combine(
        eventSettings,
        calendarEventSource.hasPermission
    ) { settings, hasPermission ->
        AgendaEventsRequest(lookaheadHours = settings.effectiveLookaheadHours(), hasPermission = hasPermission)
    }.distinctUntilChanged().flatMapLatest { request ->
        if (request.hasPermission) {
            calendarEventSource.events(lookaheadHours = request.lookaheadHours)
        } else {
            flowOf(emptyList())
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
                agendaEvents
            ) { reading, settings, headerSettings, events ->
                AgendaUiState(
                    isOpen = true,
                    days = formatter.format(
                        days = buildAgendaDays(
                            events = events,
                            eventSettings = settings,
                            now = reading.instant,
                            zoneId = reading.zoneId
                        ),
                        reading = reading,
                        headerSettings = headerSettings
                    )
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
}
