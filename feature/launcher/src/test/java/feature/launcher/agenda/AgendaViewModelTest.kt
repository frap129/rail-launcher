package feature.launcher.agenda

import app.cash.turbine.test
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.testing.MainDispatcherRule
import core.data.testing.createCalendarEventSourceMock
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
            val actualOpen: AgendaUiState = expectMostRecentItem()

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
            assertEquals(listOf("Standup"), expectMostRecentItem().days.single().events.map { event -> event.title })

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
            val actualLoaded: AgendaUiState = awaitItem()

            assertFalse(actualLoaded.isLoading)
            assertEquals(listOf("Standup"), actualLoaded.days.single().events.map { event -> event.title })
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

    private fun createViewModel(
        mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(hasPermission = true),
        eventSettings: EventSettings = EventSettings()
    ): AgendaViewModel = AgendaViewModel(
        clockSource = createClockSourceMock(reading = inputReading),
        settingsRepository = createWidgetSettingsRepositoryMock(
            headerSettings = HeaderSettings(),
            eventSettings = eventSettings
        ),
        calendarEventSource = mockCalendarEventSource,
        formatter = AgendaFormatter(createDatePatternSourceMock())
    )
}
