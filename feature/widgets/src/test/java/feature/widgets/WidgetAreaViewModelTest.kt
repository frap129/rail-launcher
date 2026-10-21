package feature.widgets

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.prefs.HeaderSettings
import core.data.testing.FakeClockSource
import core.data.testing.FakeDatePatternSource
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.MainDispatcherRule
import core.data.time.ClockReading
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.ui.R as CoreUiR
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAreaViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val headerPlace: WidgetPlace = WidgetPlace(
        id = 1L,
        heightDp = null,
        currentPage = 0,
        widgets = listOf(PlacedWidget.Header(id = 1L))
    )

    @Test
    fun `emits one place with a Header by default`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.widgetAreaState.test {
            val state: WidgetAreaState = expectMostRecentItem()
            assertTrue(state.hasWidgets)
            assertEquals(1, state.places.size)
            assertEquals(1L, state.places.single().id)
            val header: HeaderModel = state.header()
            assertEquals("2:05", header.time?.text)
            assertEquals("Tuesday, October 20", header.date?.text)
            assertEquals("2:05 PM", header.time?.spokenText)
            assertEquals("Tuesday, October 20, 2026", header.date?.spokenText)
            assertEquals(R.string.open_alarms, header.time?.clickLabel)
            assertEquals(R.string.open_calendar, header.date?.clickLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `formats time in 12 and 24 hour formats`() = runTest {
        val twelveHour: WidgetAreaViewModel = createViewModel()
        twelveHour.widgetAreaState.test {
            assertEquals("2:05", expectMostRecentItem().header().time?.text)
            cancelAndIgnoreRemainingEvents()
        }
        val twentyFourHour: WidgetAreaViewModel = createViewModel(clockSource = FakeClockSource(reading(isSystem24Hour = true)))
        twentyFourHour.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertEquals("14:05", header.time?.text)
            assertEquals("14:05", header.time?.spokenText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reformats when the reading zone or locale changes`() = runTest {
        val clockSource: FakeClockSource = FakeClockSource(reading())
        val viewModel: WidgetAreaViewModel = createViewModel(clockSource = clockSource)
        viewModel.widgetAreaState.test {
            assertEquals("2:05", expectMostRecentItem().header().time?.text)
            clockSource.emit(reading(at = "2026-10-20T21:05:00Z"))
            assertEquals("9:05", awaitItem().header().time?.text)
            clockSource.emit(reading(at = "2026-10-20T14:05:00Z", zoneId = ZoneId.of("Asia/Tokyo")))
            assertEquals("11:05", awaitItem().header().time?.text)
            clockSource.emit(reading(locale = Locale.GERMAN))
            assertEquals("Dienstag, Oktober 20", awaitItem().header().date?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes alarms and calendar tap targets`() = runTest {
        val now: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val viewModel: WidgetAreaViewModel = createViewModel(clockSource = FakeClockSource(reading(at = now.toString())))
        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.SystemAlarms, header.time?.tapTarget)
            assertEquals(HeaderTapTarget.SystemCalendar(now.toEpochMilli()), header.date?.tapTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the time line when the clock is off`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(HeaderSettings(clockVisible = false))
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertNull(header.time)
            assertEquals("Tuesday, October 20", header.date?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the date line when the date is off`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(HeaderSettings(dateVisible = false))
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertEquals("2:05", header.time?.text)
            assertNull(header.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists clock visibility and keeps the menu open`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            expectMostRecentItem()
            viewModel.setClockVisible(visible = false)
            advanceUntilIdle()
            assertEquals(false, settingsRepository.updates.single().clockVisible)
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            assertFalse((menu.items.first() as WidgetMenuItem.Toggle).checked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejects hiding the last visible Header part`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = true, dateVisible = false)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.setClockVisible(visible = false)
        advanceUntilIdle()
        assertTrue(settingsRepository.updates.isEmpty())
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val clock: WidgetMenuItem.Toggle = menu.items.first() as WidgetMenuItem.Toggle
            assertFalse(clock.enabled)
            assertEquals(CoreUiR.string.header_last_part_supporting_text, clock.supportingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the Header menu items in canonical order`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            assertEquals(
                listOf(
                    WidgetMenuAction.Clock,
                    WidgetMenuAction.Date,
                    WidgetMenuAction.RemoveHeader,
                    WidgetMenuAction.WidgetSettings
                ),
                menu.items.map { item -> item.id }
            )
            assertTrue((menu.items[0] as WidgetMenuItem.Toggle).checked)
            assertTrue((menu.items[0] as WidgetMenuItem.Toggle).enabled)
            assertTrue((menu.items[1] as WidgetMenuItem.Toggle).checked)
            assertTrue((menu.items[1] as WidgetMenuItem.Toggle).enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Widget settings in the empty area menu when the Header exists`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.openEmptyAreaMenu()
            val menu: WidgetMenuState.EmptyAreaMenu = expectEmptyAreaMenu()
            assertEquals(listOf(WidgetMenuAction.WidgetSettings), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Restore Header before Widget settings when the Header is missing`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(repository = FakeWidgetAreaRepository())
        viewModel.menuState.test {
            viewModel.openEmptyAreaMenu()
            val menu: WidgetMenuState.EmptyAreaMenu = expectEmptyAreaMenu()
            assertEquals(
                listOf(WidgetMenuAction.RestoreHeader, WidgetMenuAction.WidgetSettings),
                menu.items.map { item -> item.id }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not open menus while Favorites are edited`() = runTest {
        val homeEditState: HomeEditState = HomeEditState()
        homeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: WidgetAreaViewModel = createViewModel(homeEditState = homeEditState)
        viewModel.menuState.test {
            assertEquals(WidgetMenuState.Closed, awaitItem())
            viewModel.openHeaderMenu()
            viewModel.openEmptyAreaMenu()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `removes the Header and emits Undo`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.events.test {
            viewModel.removeHeader()
            assertEquals(WidgetAreaEvent.ShowUndo(position = 0), awaitItem())
        }
        assertEquals(1, repository.removedHeaders.size)
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)
    }

    @Test
    fun `does not emit Undo when the Header is missing`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(repository = FakeWidgetAreaRepository())
        viewModel.events.test {
            viewModel.removeHeader()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `undo restores the Header at its old position`() = runTest {
        val appWidgetPlace: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(
                PlacedWidget.AppWidget(
                    id = 1L,
                    appWidgetId = 10,
                    provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                    profileSerial = 1L,
                    extendsUnderRail = false
                )
            )
        )
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(appWidgetPlace, headerPlace.copy(id = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        var undoPosition: Int = -1
        viewModel.events.test {
            viewModel.removeHeader()
            val event: WidgetAreaEvent.ShowUndo = awaitItem() as WidgetAreaEvent.ShowUndo
            undoPosition = event.position
        }
        viewModel.undoRemoveHeader(position = undoPosition)
        advanceUntilIdle()
        assertEquals(listOf(1), repository.restoredHeaderPositions)
    }

    @Test
    fun `navigates to Widget settings and closes the menu`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.events.test {
            viewModel.openWidgetSettings()
            assertEquals(WidgetAreaEvent.NavigateToWidgetSettings, awaitItem())
        }
    }

    private fun createViewModel(
        repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
        clockSource: FakeClockSource = FakeClockSource(reading()),
        settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
        homeEditState: HomeEditState = HomeEditState()
    ): WidgetAreaViewModel = WidgetAreaViewModel(
        repository = repository,
        clockSource = clockSource,
        datePatternSource = FakeDatePatternSource(),
        settingsRepository = settingsRepository,
        homeEditState = homeEditState
    )

    private fun reading(
        at: String = "2026-10-20T14:05:00Z",
        zoneId: ZoneId = ZoneId.of("UTC"),
        locale: Locale = Locale.US,
        isSystem24Hour: Boolean = false
    ): ClockReading = ClockReading(
        instant = Instant.parse(at),
        zoneId = zoneId,
        locale = locale,
        isSystem24Hour = isSystem24Hour
    )

    private fun WidgetAreaState.header(): HeaderModel = places.single().widgets.single() as HeaderModel

    private fun TurbineTestContext<WidgetMenuState>.expectHeaderMenu(): WidgetMenuState.HeaderMenu = expectState()

    private fun TurbineTestContext<WidgetMenuState>.expectEmptyAreaMenu(): WidgetMenuState.EmptyAreaMenu = expectState()

    private inline fun <reified T : WidgetMenuState> TurbineTestContext<WidgetMenuState>.expectState(): T {
        val state: WidgetMenuState = expectMostRecentItem()
        assertTrue("Expected ${T::class.simpleName} but was $state", state is T)
        return state as T
    }
}
