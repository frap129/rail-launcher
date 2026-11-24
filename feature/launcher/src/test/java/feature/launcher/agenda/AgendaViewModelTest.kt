package feature.launcher.agenda

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.calendar.ICalendarEventSource
import core.data.calendar.ICalendarPermissionGate
import core.data.calendar.UpcomingEvent
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.testing.MainDispatcherRule
import core.data.testing.createCalendarEventSourceMock
import core.data.testing.createCalendarPermissionGate
import core.data.testing.createClockSourceMock
import core.data.testing.createDatePatternSourceMock
import core.data.testing.createUpcomingEvent
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.time.ClockReading
import core.ui.agenda.AgendaFormatter
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val inputReading: ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = true
    )

    @Test
    fun `opens with the events the Event settings select even while the Header hides them`() = runTest {
        val inputEvents: MutableStateFlow<List<UpcomingEvent>> = MutableStateFlow(
            listOf(
                createUpcomingEvent(title = "Standup", begin = "2026-10-20T15:00:00Z", end = "2026-10-20T15:30:00Z"),
                createUpcomingEvent(title = "Holiday", begin = "2026-10-20T00:00:00Z", end = "2026-10-21T00:00:00Z", allDay = true)
            )
        )
        val actualLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
        val viewModel: AgendaViewModel = createViewModel(
            mockCalendarEventSource = createCalendarEventSourceMock(
                hasPermission = true,
                events = inputEvents,
                queriedLookaheadHours = actualLookaheadHours
            ),
            eventSettings = EventSettings(enabled = false, lookaheadHours = 48, includeAllDay = true)
        )

        viewModel.uiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.open()
            val actualOpen: AgendaUiState = awaitSettled()

            assertTrue(actualOpen.isOpen)
            assertEquals(listOf("Today"), actualOpen.days.map { day -> day.label })
            assertEquals(listOf("Standup", "Holiday"), actualOpen.days.single().events.map { event -> event.title })
            assertEquals(listOf(48), actualLookaheadHours.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updates when the events change and closes`() = runTest {
        val inputEvents: MutableStateFlow<List<UpcomingEvent>> = MutableStateFlow(
            listOf(createUpcomingEvent(title = "Standup", begin = "2026-10-20T15:00:00Z", end = "2026-10-20T15:30:00Z"))
        )
        val viewModel: AgendaViewModel = createViewModel(
            mockCalendarEventSource = createCalendarEventSourceMock(hasPermission = true, events = inputEvents),
            eventSettings = EventSettings(lookaheadHours = 48)
        )

        viewModel.uiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.open()
            assertEquals(listOf("Standup"), awaitSettled().days.single().events.map { event -> event.title })

            inputEvents.value = listOf(createUpcomingEvent(title = "Release", begin = "2026-10-21T09:00:00Z", end = "2026-10-21T10:00:00Z"))
            val actualUpdated: AgendaUiState = expectMostRecentItem()

            assertEquals(listOf("Tomorrow"), actualUpdated.days.map { day -> day.label })
            assertEquals(listOf("Release"), actualUpdated.days.single().events.map { event -> event.title })

            viewModel.close()
            assertFalse(expectMostRecentItem().isOpen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reports loading until the calendar answers after opening`() = runTest {
        val inputEvents: MutableSharedFlow<List<UpcomingEvent>> = MutableSharedFlow()
        val viewModel: AgendaViewModel = createViewModel(
            mockCalendarEventSource = createCalendarEventSourceMock(hasPermission = true, events = inputEvents)
        )

        viewModel.uiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.open()

            val actualLoading: AgendaUiState = awaitItem()
            assertTrue(actualLoading.isOpen)
            assertTrue(actualLoading.isLoading)
            assertTrue(actualLoading.days.isEmpty())

            inputEvents.emit(
                listOf(createUpcomingEvent(title = "Standup", begin = "2026-10-20T15:00:00Z", end = "2026-10-20T15:30:00Z"))
            )
            val actualLoaded: AgendaUiState = awaitSettled()

            assertFalse(actualLoaded.isLoading)
            assertEquals(listOf("Standup"), actualLoaded.days.single().events.map { event -> event.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stays in the grant state when calendar permission is missing or denied`() = runTest {
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val actualLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
        val viewModel: AgendaViewModel = createViewModel(
            mockCalendarEventSource = createCalendarEventSourceMock(
                permission = inputPermission,
                queriedLookaheadHours = actualLookaheadHours
            )
        )

        viewModel.uiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.open()
            val actualMissing: AgendaUiState = awaitSettled()

            assertTrue(actualMissing.isOpen)
            assertFalse(actualMissing.isLoading)
            assertFalse(actualMissing.hasPermission)
            assertTrue(actualMissing.days.isEmpty())
            assertTrue(actualLookaheadHours.value.isEmpty())

            viewModel.onCalendarPermissionResult(granted = false, canAskAgain = true)
            advanceUntilIdle()

            expectNoEvents()
            val actualDenied: AgendaUiState = viewModel.uiState.value
            assertTrue(actualDenied.isOpen)
            assertFalse(actualDenied.isLoading)
            assertFalse(actualDenied.hasPermission)
            assertTrue(actualDenied.days.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads the agenda in place after the calendar permission is granted from the sheet`() = runTest {
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val inputEvents: MutableSharedFlow<List<UpcomingEvent>> = MutableSharedFlow()
        val viewModel: AgendaViewModel = createGrantableViewModel(inputPermission = inputPermission, inputEvents = inputEvents)

        viewModel.uiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.open()
            assertFalse(awaitSettled().hasPermission)

            viewModel.onCalendarPermissionResult(granted = true, canAskAgain = true)
            val actualLoading: AgendaUiState = expectMostRecentItem()

            assertTrue(actualLoading.isOpen)
            assertTrue(actualLoading.hasPermission)
            assertTrue(actualLoading.isLoading)

            inputEvents.emit(
                listOf(createUpcomingEvent(title = "Standup", begin = "2026-10-20T15:00:00Z", end = "2026-10-20T15:30:00Z"))
            )
            val actualGranted: AgendaUiState = awaitSettled()

            assertFalse(actualGranted.isLoading)
            assertEquals(listOf("Standup"), actualGranted.days.single().events.map { event -> event.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads the agenda when a grant made outside the sheet reaches the calendar source`() = runTest {
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val inputEvents: MutableStateFlow<List<UpcomingEvent>> = MutableStateFlow(
            listOf(createUpcomingEvent(title = "Standup", begin = "2026-10-20T15:00:00Z", end = "2026-10-20T15:30:00Z"))
        )
        val viewModel: AgendaViewModel = createGrantableViewModel(inputPermission = inputPermission, inputEvents = inputEvents)

        viewModel.uiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.open()
            assertFalse(awaitSettled().hasPermission)

            inputPermission.value = true
            val actualGranted: AgendaUiState = awaitSettled()

            assertTrue(actualGranted.hasPermission)
            assertFalse(actualGranted.isLoading)
            assertEquals(listOf("Standup"), actualGranted.days.single().events.map { event -> event.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reports a permanent calendar denial for the permission dialog`() = runTest {
        val viewModel: AgendaViewModel = createViewModel(
            mockCalendarEventSource = createCalendarEventSourceMock(permission = MutableStateFlow(false))
        )

        viewModel.events.test {
            viewModel.onCalendarPermissionResult(granted = false, canAskAgain = false)

            assertEquals(AgendaEvent.ShowCalendarPermissionDenied, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not query the calendar while it is closed`() = runTest {
        val actualLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
        val viewModel: AgendaViewModel = createViewModel(
            mockCalendarEventSource = createCalendarEventSourceMock(
                hasPermission = true,
                queriedLookaheadHours = actualLookaheadHours
            )
        )

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().isOpen)
            assertTrue(actualLookaheadHours.value.isEmpty())
            viewModel.open()
            expectMostRecentItem()
            assertEquals(listOf(12), actualLookaheadHours.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createGrantableViewModel(
        inputPermission: MutableStateFlow<Boolean>,
        inputEvents: Flow<List<UpcomingEvent>>
    ): AgendaViewModel = createViewModel(
        mockCalendarEventSource = createCalendarEventSourceMock(
            permission = inputPermission,
            onRefresh = { inputPermission.value = true },
            events = inputEvents
        )
    )

    private fun createViewModel(
        mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(hasPermission = true),
        eventSettings: EventSettings = EventSettings(),
        mockCalendarPermissionGate: ICalendarPermissionGate = createCalendarPermissionGate(calendarEventSource = mockCalendarEventSource)
    ): AgendaViewModel = AgendaViewModel(
        clockSource = createClockSourceMock(reading = inputReading),
        settingsRepository = createWidgetSettingsRepositoryMock(
            headerSettings = HeaderSettings(),
            eventSettings = eventSettings
        ),
        calendarEventSource = mockCalendarEventSource,
        calendarPermissionGate = mockCalendarPermissionGate,
        formatter = AgendaFormatter(createDatePatternSourceMock())
    )
}

/** Waits until the agenda has left its loading state. */
private suspend fun TurbineTestContext<AgendaUiState>.awaitSettled(): AgendaUiState {
    var state: AgendaUiState = awaitItem()
    while (state.isLoading) {
        state = awaitItem()
    }
    return state
}
