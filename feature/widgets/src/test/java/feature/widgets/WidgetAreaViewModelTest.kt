package feature.widgets

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.battery.BatteryStatus
import core.data.calendar.UpcomingEvent
import core.data.icons.model.Icon
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeBatterySource
import core.data.testing.FakeCalendarEventSource
import core.data.testing.FakeClockSource
import core.data.testing.FakeDatePatternSource
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.MainDispatcherRule
import core.data.time.ClockReading
import core.data.widgets.AppWidgetId
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.RemoveAppWidget
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize
import core.ui.R as CoreUiR
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    fun `re-emits the Header when the style settings change`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.widgetAreaState.test {
            assertEquals(ClockStyle.CLASSIC, expectMostRecentItem().header().style)
            settingsRepository.updateHeaderSettings { settings ->
                settings.copy(
                    clockStyle = ClockStyle.STACKED,
                    timeFormat = TimeFormat.H24,
                    alignment = HeaderAlignment.END,
                    size = HeaderSize.EXTRA_LARGE
                )
            }
            val header: HeaderModel = awaitItem().header()
            assertEquals(ClockStyle.STACKED, header.style)
            assertEquals(HeaderAlignment.END, header.alignment)
            assertEquals(HeaderSize.EXTRA_LARGE, header.size)
            assertEquals("14", header.time?.hourText)
            assertEquals("05", header.time?.minuteText)
            assertEquals("Tuesday, October 20", header.date?.text)
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
    fun `resolves a chosen Launchable for the time and Nothing for the date`() = runTest {
        val launchable: RailLaunchable = testLaunchable(name = "Maps", deeplink = "rail://maps")
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = launchable.key),
                dateTapTarget = HeaderTapSetting.Nothing
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            launchableRepository = FakeLaunchableRepository(groups = listOf(LaunchableGroup.Builtin(items = listOf(launchable))))
        )
        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = launchable), header.time?.tapTarget)
            assertEquals(R.string.open_launchable, header.time?.clickLabel)
            assertEquals("Maps", header.time?.clickLabelArg)
            assertEquals(HeaderTapTarget.None, header.date?.tapTarget)
            assertNull(header.date?.clickLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves Nothing for the time`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.None, header.time?.tapTarget)
            assertNull(header.time?.clickLabel)
            assertEquals(HeaderTapTarget.SystemCalendar(Instant.parse("2026-10-20T14:05:00Z").toEpochMilli()), header.date?.tapTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves a chosen Launchable for the date`() = runTest {
        val launchable: RailLaunchable = testLaunchable(name = "Maps", deeplink = "rail://maps")
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(dateTapTarget = HeaderTapSetting.Launchable(key = launchable.key))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            launchableRepository = FakeLaunchableRepository(groups = listOf(LaunchableGroup.Builtin(items = listOf(launchable))))
        )
        viewModel.widgetAreaState.test {
            val date: HeaderLineModel? = expectMostRecentItem().header().date
            assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = launchable), date?.tapTarget)
            assertEquals(R.string.open_launchable, date?.clickLabel)
            assertEquals("Maps", date?.clickLabelArg)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves stale Launchables to the system default before and after loading`() = runTest {
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = "uninstalled-clock"),
                dateTapTarget = HeaderTapSetting.Launchable(key = "uninstalled-calendar")
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            launchableRepository = launchableRepository
        )
        viewModel.widgetAreaState.test {
            val unloadedHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.SystemAlarms, unloadedHeader.time?.tapTarget)
            assertTrue(unloadedHeader.date?.tapTarget is HeaderTapTarget.SystemCalendar)
            launchableRepository.setGroups(groups = listOf(LaunchableGroup.Builtin(items = listOf(testLaunchable()))))
            advanceUntilIdle()
            val loadedHeader: HeaderModel = viewModel.widgetAreaState.value.header()
            assertEquals(HeaderTapTarget.SystemAlarms, loadedHeader.time?.tapTarget)
            assertTrue(loadedHeader.date?.tapTarget is HeaderTapTarget.SystemCalendar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves a chosen Launchable once it loads`() = runTest {
        val launchable: RailLaunchable = testLaunchable(name = "Maps", deeplink = "rail://maps")
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(timeTapTarget = HeaderTapSetting.Launchable(key = launchable.key))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            launchableRepository = launchableRepository
        )
        viewModel.widgetAreaState.test {
            assertEquals(HeaderTapTarget.SystemAlarms, expectMostRecentItem().header().time?.tapTarget)
            launchableRepository.setGroups(groups = listOf(LaunchableGroup.Builtin(items = listOf(launchable))))
            assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = launchable), awaitItem().header().time?.tapTarget)
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
    fun `keeps the date in Compact style when the clock is off`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = false, clockStyle = ClockStyle.COMPACT)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertNull(header.time)
            assertEquals("Tuesday, October 20", header.date?.text)
            assertEquals(ClockStyle.COMPACT, header.style)
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
                    WidgetMenuAction.Battery,
                    WidgetMenuAction.UpcomingEvents,
                    WidgetMenuAction.ClockStyle,
                    WidgetMenuAction.AddWidget,
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
    fun `opens the Clock Style chooser with previews and applies a style`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            expectHeaderMenu()
            viewModel.openClockStyleChooser()
            val chooser: WidgetMenuState.ClockStyleChooser = expectState()
            assertEquals(ClockStyle.CLASSIC, chooser.selected)
            assertEquals(ClockStyle.entries, chooser.previews.map { preview -> preview.style })
            assertEquals("2:05", chooser.previews.first().time?.text)
            viewModel.selectClockStyle(ClockStyle.BOLD)
            advanceUntilIdle()
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            assertEquals(ClockStyle.BOLD, settingsRepository.updates.last().clockStyle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Widget settings in the empty area menu when the Header exists`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.openEmptyAreaMenu()
            val menu: WidgetMenuState.EmptyAreaMenu = expectEmptyAreaMenu()
            assertEquals(listOf(WidgetMenuAction.AddWidget, WidgetMenuAction.WidgetSettings), menu.items.map { item -> item.id })
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
                listOf(WidgetMenuAction.AddWidget, WidgetMenuAction.RestoreHeader, WidgetMenuAction.WidgetSettings),
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

    @Test
    fun `does not collect the battery while its visibility is Never`() = runTest {
        val batterySource: FakeBatterySource = FakeBatterySource(BatteryStatus(percent = 84, isCharging = false))
        val viewModel: WidgetAreaViewModel = createViewModel(batterySource = batterySource)

        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertNull(header.battery)
            advanceUntilIdle()
            assertEquals(0, batterySource.activeSubscribers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows and updates the battery when its visibility is Always`() = runTest {
        val batterySource: FakeBatterySource = FakeBatterySource(BatteryStatus(percent = 84, isCharging = false))
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            batterySource = batterySource
        )

        viewModel.widgetAreaState.test {
            val header: HeaderModel = expectMostRecentItem().header()
            assertEquals("84%", header.battery?.text)
            assertEquals(84, header.battery?.percent)
            assertEquals(R.string.battery_content_description, header.battery?.contentDescriptionRes)
            assertTrue(batterySource.activeSubscribers > 0)

            batterySource.emit(BatteryStatus(percent = 85, isCharging = false))
            assertEquals("85%", awaitItem().header().battery?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the battery only while charging or low`() = runTest {
        val batterySource: FakeBatterySource = FakeBatterySource(BatteryStatus(percent = 84, isCharging = false))
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(batteryVisibility = BatteryVisibility.CHARGING_OR_LOW)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            batterySource = batterySource
        )

        viewModel.widgetAreaState.test {
            assertNull(expectMostRecentItem().header().battery)

            batterySource.emit(BatteryStatus(percent = 20, isCharging = false))
            val low: BatteryModel? = awaitItem().header().battery
            assertEquals("20%", low?.text)
            assertTrue(low?.isLow == true)

            batterySource.emit(BatteryStatus(percent = 21, isCharging = false))
            assertNull(awaitItem().header().battery)

            batterySource.emit(BatteryStatus(percent = 50, isCharging = true))
            val charging: BatteryModel? = awaitItem().header().battery
            assertEquals("50%", charging?.text)
            assertTrue(charging?.isCharging == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps the Header visible when the battery is always shown`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setClockVisible(visible = false)
        viewModel.setDateVisible(visible = false)
        advanceUntilIdle()

        assertFalse(settingsRepository.updates.last().clockVisible)
        assertFalse(settingsRepository.updates.last().dateVisible)
    }

    @Test
    fun `restores the clock when the battery stops being always visible`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = false, dateVisible = false, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.selectBatteryVisibility(BatteryVisibility.NEVER)
        advanceUntilIdle()

        val updated: HeaderSettings = settingsRepository.updates.single()
        assertEquals(BatteryVisibility.NEVER, updated.batteryVisibility)
        assertTrue(updated.clockVisible)
    }

    @Test
    fun `rejects hiding the last visible Header part while the battery is conditional`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(
                clockVisible = true,
                dateVisible = false,
                batteryVisibility = BatteryVisibility.CHARGING_OR_LOW
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setClockVisible(visible = false)
        advanceUntilIdle()

        assertTrue(settingsRepository.updates.isEmpty())
    }

    @Test
    fun `expands the Battery menu choice and saves a visibility`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val battery: WidgetMenuItem.Choice = menu.items[2] as WidgetMenuItem.Choice
            assertEquals(WidgetMenuAction.Battery, battery.id)
            assertEquals(CoreUiR.string.battery_never, battery.valueLabel)
            assertEquals(
                listOf("NEVER", "ALWAYS", "CHARGING_OR_LOW"),
                battery.options.map { option -> option.key.value }
            )
            assertFalse(battery.expanded)

            viewModel.toggleChoice(WidgetMenuAction.Battery)
            advanceUntilIdle()
            val expanded: WidgetMenuItem.Choice = expectHeaderMenu().items[2] as WidgetMenuItem.Choice
            assertTrue(expanded.expanded)

            viewModel.selectBatteryVisibility(BatteryVisibility.CHARGING_OR_LOW)
            advanceUntilIdle()
            assertEquals(BatteryVisibility.CHARGING_OR_LOW, settingsRepository.updates.last().batteryVisibility)
            val collapsed: WidgetMenuItem.Choice = expectHeaderMenu().items[2] as WidgetMenuItem.Choice
            assertFalse(collapsed.expanded)
            assertEquals(BatteryVisibility.CHARGING_OR_LOW.name, collapsed.selectedKey.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not collect calendar events while the feature is off`() = runTest {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource()
        val viewModel: WidgetAreaViewModel = createViewModel(calendarEventSource = calendarEventSource)

        viewModel.widgetAreaState.test {
            expectMostRecentItem()
            advanceUntilIdle()

            assertEquals(0, calendarEventSource.activeSubscribers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not collect calendar events without permission`() = runTest {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialEventSettings = EventSettings(enabled = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = calendarEventSource
        )

        viewModel.widgetAreaState.test {
            expectMostRecentItem()
            advanceUntilIdle()

            assertEquals(0, calendarEventSource.activeSubscribers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requests calendar permission when enabling events without it`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )

        viewModel.events.test {
            viewModel.setUpcomingEventsEnabled(enabled = true)
            advanceUntilIdle()

            assertEquals(WidgetAreaEvent.RequestCalendarPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps events off and reports a denial`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )

        viewModel.events.test {
            viewModel.setUpcomingEventsEnabled(enabled = true)
            advanceUntilIdle()
            assertEquals(WidgetAreaEvent.RequestCalendarPermission, awaitItem())

            viewModel.onCalendarPermissionResult(granted = false, canAskAgain = true)
            advanceUntilIdle()

            assertEquals(WidgetAreaEvent.ShowCalendarPermissionDenied(permanentlyDenied = false), awaitItem())
            assertTrue(settingsRepository.eventUpdates.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists events as enabled after a permission grant`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )

        viewModel.onCalendarPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        assertTrue(settingsRepository.eventUpdates.last().enabled)
    }

    @Test
    fun `drops an event on the first clock tick after it ends`() = runTest {
        val event: UpcomingEvent = event(title = "Standup")
        val clockSource: FakeClockSource = FakeClockSource(reading(at = "2026-10-20T14:05:00Z"))
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialSettings = HeaderSettings(timeFormat = TimeFormat.H24),
            initialEventSettings = EventSettings(enabled = true, lookaheadHours = 12, maxEvents = 1)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            clockSource = clockSource,
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(events = listOf(event))
        )

        viewModel.widgetAreaState.test {
            assertEquals(listOf("Standup · 14:06"), expectMostRecentItem().header().events.map { model -> model.text })

            clockSource.emit(reading(at = "2026-10-20T14:08:00Z"))
            advanceUntilIdle()

            assertTrue(expectMostRecentItem().header().events.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Upcoming events as the fourth Header menu item`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()

        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()

            assertEquals(WidgetMenuAction.UpcomingEvents, menu.items[3].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun event(
        title: String = "Standup",
        begin: String = "2026-10-20T14:06:00Z",
        end: String = "2026-10-20T14:07:00Z"
    ): UpcomingEvent = UpcomingEvent(
        eventId = 7L,
        title = title,
        beginMillis = Instant.parse(begin).toEpochMilli(),
        endMillis = Instant.parse(end).toEpochMilli(),
        isAllDay = false,
        calendarId = 3L,
        color = 0,
        isWorkProfile = false
    )

    private fun createViewModel(
        repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
        clockSource: FakeClockSource = FakeClockSource(reading()),
        settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
        homeEditState: HomeEditState = HomeEditState(),
        launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList()),
        calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(),
        batterySource: FakeBatterySource = FakeBatterySource(BatteryStatus(percent = 84, isCharging = false)),
        appWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(),
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): WidgetAreaViewModel = WidgetAreaViewModel(
        repository = repository,
        clockSource = clockSource,
        formatter = HeaderFormatter(FakeDatePatternSource()),
        settingsRepository = settingsRepository,
        homeEditState = homeEditState,
        launchableRepository = launchableRepository,
        calendarEventSource = calendarEventSource,
        batterySource = batterySource,
        appWidgetHost = appWidgetHost,
        savedStateHandle = savedStateHandle,
        removeAppWidget = RemoveAppWidget(repository = repository, host = appWidgetHost)
    )

    @Test
    fun `silent bind with no configuration places the App Widget at the end`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(bindResults = listOf(true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        val provider: WidgetProviderInfo = appWidgetProviderInfo()
        viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
        viewModel.pickProvider(provider)
        advanceUntilIdle()
        val places: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(1L, 2L), places.map { place -> place.id })
        val widget: PlacedWidget.AppWidget = places.last().widgets.single() as PlacedWidget.AppWidget
        assertEquals(1, widget.appWidgetId)
        assertEquals(provider.provider, widget.provider)
        assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
        assertEquals(listOf(AppWidgetId(1)), host.allocatedIds)
        assertTrue(host.deletedIds.isEmpty())
    }

    @Test
    fun `a refused silent bind launches the system dialog and a denial deletes the id`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(bindResults = listOf(false))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.events.test {
            viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
            viewModel.pickProvider(appWidgetProviderInfo())
            val event: WidgetAreaEvent = awaitItem()
            assertTrue(event is WidgetAreaEvent.LaunchBind)
            viewModel.onBindResult(granted = false)
            assertEquals(WidgetAreaEvent.ShowMessage(R.string.couldnt_add_widget), awaitItem())
            assertEquals(listOf(AppWidgetId(1)), host.deletedIds)
            assertEquals(listOf(headerPlace.id), repository.places.first().map { place -> place.id })
            assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a granted bind places the App Widget after the dialog`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(bindResults = listOf(false))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
        viewModel.pickProvider(appWidgetProviderInfo())
        viewModel.onBindResult(granted = true)
        advanceUntilIdle()
        assertEquals(2, repository.places.first().size)
        assertTrue(host.deletedIds.isEmpty())
    }

    @Test
    fun `a provider that needs configuration launches it and cancelling deletes the id`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(bindResults = listOf(true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.events.test {
            viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
            viewModel.pickProvider(appWidgetProviderInfo(needsConfiguration = true))
            assertEquals(WidgetAreaEvent.LaunchConfigure(AppWidgetId(1)), awaitItem())
            viewModel.onConfigureResult(ok = false)
            assertEquals(WidgetAreaEvent.ShowMessage(R.string.couldnt_add_widget), awaitItem())
            assertEquals(listOf(AppWidgetId(1)), host.deletedIds)
            assertEquals(1, repository.places.first().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a provider that needs configuration places the App Widget when configuration succeeds`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(bindResults = listOf(true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.events.test {
            viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
            viewModel.pickProvider(appWidgetProviderInfo(needsConfiguration = true))
            awaitItem()
            viewModel.onConfigureResult(ok = true)
            advanceUntilIdle()
            assertEquals(2, repository.places.first().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a provider with optional reconfigurable configuration is placed without launching configuration`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(bindResults = listOf(true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.events.test {
            viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
            viewModel.pickProvider(appWidgetProviderInfo(needsConfiguration = false))
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.places.first().size)
    }

    @Test
    fun `cancelling the picker allocates nothing`() = runTest {
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val viewModel: WidgetAreaViewModel = createViewModel(appWidgetHost = host)
        viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
        assertTrue(viewModel.addFlowState.value is AddWidgetFlowState.Picking)
        viewModel.cancelAdd()
        assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
        assertTrue(host.allocatedIds.isEmpty())
    }

    @Test
    fun `add from the Header menu inserts directly below the Header`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = repository,
            appWidgetHost = FakeAppWidgetHostSource(bindResults = listOf(true))
        )
        viewModel.openHeaderMenu()
        viewModel.startAddFromMenu()
        advanceUntilIdle()
        viewModel.pickProvider(appWidgetProviderInfo())
        advanceUntilIdle()
        val places: List<WidgetPlace> = repository.places.first()
        assertEquals(2, places.size)
        assertTrue(places[1].widgets.single() is PlacedWidget.AppWidget)
    }

    @Test
    fun `add from an App Widget menu inserts directly below that widget`() = runTest {
        val first: WidgetPlace = headerPlace
        val second: WidgetPlace = appWidgetPlace(placeId = 2L, widgetId = 2L)
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(first, second))
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = repository,
            appWidgetHost = FakeAppWidgetHostSource(bindResults = listOf(true))
        )
        viewModel.openAppWidgetMenu(widgetId = 2L)
        viewModel.startAddFromMenu()
        advanceUntilIdle()
        viewModel.pickProvider(appWidgetProviderInfo())
        advanceUntilIdle()
        val places: List<WidgetPlace> = repository.places.first()
        assertEquals(listOf(1L, 2L, 3L), places.map { place -> place.id })
        assertTrue(places[2].widgets.single() is PlacedWidget.AppWidget)
    }

    @Test
    fun `add from the empty area inserts at the end`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = repository,
            appWidgetHost = FakeAppWidgetHostSource(bindResults = listOf(true))
        )
        viewModel.openEmptyAreaMenu()
        viewModel.startAddFromMenu()
        advanceUntilIdle()
        viewModel.pickProvider(appWidgetProviderInfo())
        advanceUntilIdle()
        assertEquals(1, repository.places.first().size)
    }

    @Test
    fun `add started from Settings returns to Settings after placing`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = repository,
            appWidgetHost = FakeAppWidgetHostSource(bindResults = listOf(true))
        )
        viewModel.events.test {
            viewModel.startAdd(AddWidgetOrigin(InsertPosition.End, returnToSettings = true))
            viewModel.pickProvider(appWidgetProviderInfo())
            advanceUntilIdle()
            assertEquals(WidgetAreaEvent.ReturnToSettings, awaitItem())
            assertEquals(2, repository.places.first().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `picker groups by app, sorts by label and filters by query`() = runTest {
        val alphaProvider: WidgetProvider = WidgetProvider(packageName = "com.alpha", className = "AlphaWidget")
        val zetaProvider: WidgetProvider = WidgetProvider(packageName = "com.zeta", className = "ZetaWidget")
        val alphaBeta: WidgetProvider = WidgetProvider(packageName = "com.alpha", className = "BetaWidget")
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(
                appWidgetProviderInfo(provider = zetaProvider, appPackageName = "com.zeta", appLabel = "Zeta", label = "Clock"),
                appWidgetProviderInfo(provider = alphaProvider, appPackageName = "com.alpha", appLabel = "Alpha", label = "Calendar"),
                appWidgetProviderInfo(provider = alphaBeta, appPackageName = "com.alpha", appLabel = "Alpha", label = "Mail")
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(appWidgetHost = host)
        viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
        advanceUntilIdle()
        val picking: AddWidgetFlowState.Picking = viewModel.addFlowState.value as AddWidgetFlowState.Picking
        assertEquals(listOf("Alpha", "Zeta"), picking.groups.map { group -> group.appLabel })
        assertEquals(listOf("Calendar", "Mail"), picking.groups.first().widgets.map { widget -> widget.label })
        viewModel.setPickerQuery("mail")
        val filtered: AddWidgetFlowState.Picking = viewModel.addFlowState.value as AddWidgetFlowState.Picking
        assertEquals(listOf("Mail"), filtered.groups.flatMap { group -> group.widgets.map { widget -> widget.label } })
        viewModel.setPickerQuery("missing")
        assertTrue((viewModel.addFlowState.value as AddWidgetFlowState.Picking).groups.isEmpty())
    }

    @Test
    fun `remove widget deletes the row, the system id and the cached view`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.events.test {
            viewModel.removeWidget(widgetId = 2L)
            assertEquals(WidgetAreaEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            assertEquals(WidgetAreaEvent.ShowMessage(R.string.widget_removed), awaitItem())
            assertEquals(listOf(AppWidgetId(9)), host.deletedIds)
            assertEquals(listOf(1L), repository.places.first().map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `menus and add flows are ignored while editing`() = runTest {
        val homeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val viewModel: WidgetAreaViewModel = createViewModel(homeEditState = homeEditState)
        viewModel.openHeaderMenu()
        viewModel.openEmptyAreaMenu()
        viewModel.openAppWidgetMenu(widgetId = 2L)
        viewModel.startAdd(AddWidgetOrigin(InsertPosition.End))
        assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)
    }

    private fun appWidgetProviderInfo(
        provider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
        appPackageName: String = provider.packageName,
        appLabel: String = "Example",
        label: String = "Example Widget",
        needsConfiguration: Boolean = false,
        profileSerial: Long = 0L
    ): WidgetProviderInfo = WidgetProviderInfo(
        provider = provider,
        profileSerial = profileSerial,
        appPackageName = appPackageName,
        appLabel = appLabel,
        appIcon = Icon.ApplicationIcon(packageName = appPackageName),
        label = label,
        description = null,
        defaultSize = WidgetSize(widthDp = 110, heightDp = 48),
        cellSize = null,
        minHeightDp = 48,
        minResizeHeightDp = 48,
        maxResizeHeightDp = null,
        needsConfiguration = needsConfiguration,
        isReconfigurable = false,
        isWorkProfile = false
    )

    private fun appWidgetPlace(placeId: Long, widgetId: Long, appWidgetId: Int = 9, profileSerial: Long = 0L): WidgetPlace = WidgetPlace(
        id = placeId,
        heightDp = null,
        currentPage = 0,
        widgets = listOf(
            PlacedWidget.AppWidget(
                id = widgetId,
                appWidgetId = appWidgetId,
                provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
                profileSerial = profileSerial,
                extendsUnderRail = false
            )
        )
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

    private fun testLaunchable(name: String = "Example", deeplink: String = "rail://example"): RailLaunchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.ApplicationIcon(packageName = "com.example"),
        deeplink = deeplink
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
