package feature.launcher.widgets

import androidx.compose.foundation.lazy.LazyListState
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.battery.BatteryStatus
import core.data.calendar.MAX_EVENT_LOOKAHEAD_HOURS
import core.data.calendar.UpcomingEvent
import core.data.icons.model.Icon
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.media.MediaCommand
import core.data.media.MediaSessionKey
import core.data.media.MediaSessionSnapshot
import core.data.media.PlaybackStatus
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
import core.data.testing.FakeMediaSessionSource
import core.data.testing.FakeRemoveAppWidget
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.MainDispatcherRule
import core.data.testing.appWidgetPlace
import core.data.testing.createHeaderSettingsActions
import core.data.testing.headerPlace
import core.data.testing.stackPlace
import core.data.time.ClockReading
import core.data.widgets.AddAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.HeaderRemoval
import core.data.widgets.HomeHeightState
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetLifecycleReconciler
import core.data.widgets.InsertPosition
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.PlaceDraft
import core.data.widgets.PlacedWidget
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSpacing
import core.ui.R as CoreUiR
import core.widgets.AppWidgetModel
import core.widgets.BatteryModel
import core.widgets.HeaderFormatter
import core.widgets.HeaderLineModel
import core.widgets.HeaderModel
import core.widgets.HeaderTapTarget
import core.widgets.IHeaderTapTargetResolverImpl
import core.widgets.R as CoreWidgetsR
import feature.launcher.R
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAreaViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

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
            assertEquals(CoreWidgetsR.string.open_alarms, header.time?.clickLabel)
            assertEquals(CoreWidgetsR.string.open_calendar, header.date?.clickLabel)
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
            assertEquals(CoreWidgetsR.string.open_launchable, header.time?.clickLabel)
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
            assertEquals(CoreWidgetsR.string.open_launchable, date?.clickLabel)
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
                    WidgetMenuAction.MoveWidgetArea,
                    WidgetMenuAction.EditWidgets,
                    WidgetMenuAction.MediaPlayer,
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
            assertEquals(
                listOf(WidgetMenuAction.AddWidget, WidgetMenuAction.MoveWidgetArea, WidgetMenuAction.WidgetSettings),
                menu.items.map { item -> item.id }
            )
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
                listOf(
                    WidgetMenuAction.AddWidget,
                    WidgetMenuAction.RestoreHeader,
                    WidgetMenuAction.MoveWidgetArea,
                    WidgetMenuAction.WidgetSettings
                ),
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
            assertEquals(
                WidgetAreaEvent.ShowUndo(
                    action = UndoAction.RemoveHeader(
                        removal = HeaderRemoval(position = 0, placeId = 1L, pagePosition = 0)
                    )
                ),
                awaitItem()
            )
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
        var removal: UndoAction.RemoveHeader? = null
        viewModel.events.test {
            viewModel.removeHeader()
            val event: WidgetAreaEvent.ShowUndo = awaitItem() as WidgetAreaEvent.ShowUndo
            removal = event.action as UndoAction.RemoveHeader
        }
        viewModel.undo(action = removal!!)
        advanceUntilIdle()
        assertEquals(listOf(1), repository.restoredHeaders.map { restored -> restored.position })
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
            assertEquals(CoreWidgetsR.string.battery_content_description, header.battery?.contentDescriptionRes)
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
    fun `never re-enables a hidden clock when the battery stops being always visible`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = false, dateVisible = true, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.selectBatteryVisibility(BatteryVisibility.NEVER)
        advanceUntilIdle()

        val actualSettings: HeaderSettings = settingsRepository.updates.single()
        assertEquals(BatteryVisibility.NEVER, actualSettings.batteryVisibility)
        assertFalse(actualSettings.clockVisible)
    }

    @Test
    fun `disables conditional battery options while the battery is the last visible part`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = false, dateVisible = false, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val battery: WidgetMenuItem.Choice = menu.items[2] as WidgetMenuItem.Choice
            val enabledByVisibility: Map<String, Boolean> = battery.options.associate { option ->
                option.key.value to option.enabled
            }

            assertTrue(enabledByVisibility.getValue(BatteryVisibility.ALWAYS.name))
            assertFalse(enabledByVisibility.getValue(BatteryVisibility.NEVER.name))
            assertFalse(enabledByVisibility.getValue(BatteryVisibility.CHARGING_OR_LOW.name))
            cancelAndIgnoreRemainingEvents()
        }
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
    fun `requests calendar events with the effective lookahead`() = runTest {
        val calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource()
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialEventSettings = EventSettings(enabled = true, lookaheadHours = 99)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = calendarEventSource
        )

        viewModel.widgetAreaState.test {
            expectMostRecentItem()
            advanceUntilIdle()

            assertEquals(listOf(MAX_EVENT_LOOKAHEAD_HOURS), calendarEventSource.queriedLookaheadHours)
            cancelAndIgnoreRemainingEvents()
        }
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

    @Test
    fun `a stack Header menu offers Remove from stack`() = runTest {
        val stack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = FakeWidgetAreaRepository(listOf(stack)))

        viewModel.menuState.test {
            expectMostRecentItem()
            viewModel.openHeaderMenu(widgetId = 1L)
            advanceUntilIdle()
            val menu: WidgetMenuState.HeaderMenu = expectMostRecentItem() as WidgetMenuState.HeaderMenu
            assertTrue(menu.items.any { item -> item.id == WidgetMenuAction.RemoveFromStack })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `page settle persists the current page after the debounce`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)

        viewModel.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(299)
        assertTrue(repository.currentPageUpdates.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf(1L to 1), repository.currentPageUpdates)
    }

    @Test
    fun `merging a place over the stack cap is rejected`() = runTest {
        val stack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = FakeWidgetAreaRepository(listOf(stack, appWidgetPlace(placeId = 3L, widgetId = 3L))),
            settingsRepository = FakeWidgetSettingsRepository(initialAreaSettings = WidgetAreaSettings(maxStackPages = 2))
        )

        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.mergePlace(sourcePlaceId = 3L, targetPlaceId = 1L)
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `splitting a stack page creates a new draft place`() = runTest {
        val stack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = FakeWidgetAreaRepository(listOf(stack)))

        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.splitPage(placeId = 1L, widgetId = 2L, toIndex = 1)

        val draft: WidgetEditDraft = viewModel.editState.value!!
        assertEquals(2, draft.places.size)
        assertNull(draft.places[1].placeId)
        assertEquals(listOf(2L), draft.places[1].widgetIds)
    }

    @Test
    fun `toggling a stack expands it in the draft`() = runTest {
        val stack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = FakeWidgetAreaRepository(listOf(stack)))

        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.toggleStackExpanded(placeId = 1L)

        assertTrue(1L in viewModel.editState.value!!.expandedPlaceIds)
    }

    @Test
    fun `Widget Area layout follows the height settings`() = runTest {
        val defaultViewModel: WidgetAreaViewModel = createViewModel()
        collectWidgetAreaState(defaultViewModel)
        assertEquals(
            WidgetAreaLayout.Capped(fraction = 2f / 3f),
            defaultViewModel.widgetAreaState.value.layout
        )

        val uncappedViewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                initialAreaSettings = WidgetAreaSettings(heightLimitEnabled = false)
            )
        )
        collectWidgetAreaState(uncappedViewModel)
        assertEquals(WidgetAreaLayout.Uncapped, uncappedViewModel.widgetAreaState.value.layout)
        val halfViewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                initialAreaSettings = WidgetAreaSettings(heightLimit = WidgetAreaHeightLimit.HALF)
            )
        )
        collectWidgetAreaState(halfViewModel)
        assertEquals(
            WidgetAreaLayout.Capped(fraction = 0.5f),
            halfViewModel.widgetAreaState.value.layout
        )
    }

    @Test
    fun `Home intent recreates the Widget Area list state`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        collectWidgetAreaState(viewModel)
        val previous: LazyListState = viewModel.widgetAreaState.value.listState

        viewModel.onHomeIntent()
        advanceUntilIdle()

        assertNotSame(previous, viewModel.widgetAreaState.value.listState)
    }

    @Test
    fun `toggling the height limit resets the Widget Area list state`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectWidgetAreaState(viewModel)
        val previous: LazyListState = viewModel.widgetAreaState.value.listState

        settingsRepository.setHeightLimitEnabled(false)
        advanceUntilIdle()

        val changed: WidgetAreaState = viewModel.widgetAreaState.value
        assertEquals(WidgetAreaLayout.Uncapped, changed.layout)
        assertNotSame(previous, changed.listState)
    }

    @Test
    fun `changing the cap fraction keeps the Widget Area list state`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectWidgetAreaState(viewModel)
        val previous: LazyListState = viewModel.widgetAreaState.value.listState

        settingsRepository.setHeightLimit(WidgetAreaHeightLimit.HALF)
        advanceUntilIdle()

        val changed: WidgetAreaState = viewModel.widgetAreaState.value
        assertEquals(WidgetAreaLayout.Capped(fraction = 0.5f), changed.layout)
        assertSame(previous, changed.listState)
    }

    @Test
    fun `starting Move Widget Area is ignored outside the None mode`() = runTest {
        val homeEditState: HomeEditState = HomeEditState()
        homeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: WidgetAreaViewModel = createViewModel(homeEditState = homeEditState)
        collectMoveState(viewModel)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        assertNull(viewModel.moveState.value)
        assertEquals(HomeEditMode.Favorites, homeEditState.mode.value)
    }

    @Test
    fun `Move Widget Area starts at the persisted offset and caps at half Home`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(topOffsetDp = 24)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        val state: MoveWidgetAreaState = viewModel.moveState.value!!
        assertEquals(24, state.draftOffsetDp)
        assertEquals(400, state.maxOffsetDp)
        assertTrue(state.canMoveUp)
        assertTrue(state.canMoveDown)
    }

    @Test
    fun `Move Widget Area steps and snaps the draft offset`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)
        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        viewModel.stepOffset(MoveWidgetAreaStep.Up)
        assertEquals(0, viewModel.moveState.value?.draftOffsetDp)
        viewModel.stepOffset(MoveWidgetAreaStep.Down)
        assertEquals(8, viewModel.moveState.value?.draftOffsetDp)

        viewModel.setDraftOffset(13)
        assertEquals(16, viewModel.moveState.value?.draftOffsetDp)
        viewModel.setDraftOffset(10_000)
        assertEquals(400, viewModel.moveState.value?.draftOffsetDp)
        assertFalse(viewModel.moveState.value!!.canMoveDown)

        viewModel.resetOffset()
        assertEquals(0, viewModel.moveState.value?.draftOffsetDp)
        assertFalse(viewModel.moveState.value!!.canMoveUp)
    }

    @Test
    fun `finishing Move Widget Area persists the draft and exits the mode`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val homeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel =
            createViewModel(settingsRepository = settingsRepository, homeEditState = homeEditState)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)
        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        viewModel.setDraftOffset(16)
        viewModel.finishMoveWidgetArea()
        advanceUntilIdle()

        assertNull(viewModel.moveState.value)
        assertEquals(HomeEditMode.None, homeEditState.mode.value)
        assertEquals(16, settingsRepository.areaUpdates.last().topOffsetDp)
    }

    @Test
    fun `Back and Home intent save the Move Widget Area offset`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()
        viewModel.setDraftOffset(8)
        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        assertEquals(8, settingsRepository.areaUpdates.last().topOffsetDp)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()
        viewModel.setDraftOffset(16)
        assertTrue(viewModel.onHomeIntent())
        advanceUntilIdle()
        assertEquals(16, settingsRepository.areaUpdates.last().topOffsetDp)
    }

    @Test
    fun `effective offset uses the draft while moving and the persisted value after`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(topOffsetDp = 8)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectWidgetAreaState(viewModel)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()
        viewModel.setDraftOffset(24)
        assertEquals(24, viewModel.widgetAreaState.value.topOffsetDp)

        viewModel.finishMoveWidgetArea()
        advanceUntilIdle()
        assertEquals(24, viewModel.widgetAreaState.value.topOffsetDp)
        assertEquals(24, settingsRepository.widgetAreaSettings.first().topOffsetDp)
    }

    @Test
    fun `clamps a persisted offset at render time without rewriting it`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(topOffsetDp = 400)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectWidgetAreaState(viewModel)
        collectMoveState(viewModel)

        viewModel.setHomeHeight(100)
        advanceUntilIdle()

        assertEquals(48, viewModel.widgetAreaState.value.topOffsetDp)
        assertEquals(400, settingsRepository.widgetAreaSettings.first().topOffsetDp)
    }

    @Test
    fun `exposes widget spacing from settings and applies changes live`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(widgetSpacing = WidgetSpacing.LARGE)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(settingsRepository = settingsRepository)
        collectWidgetAreaState(viewModel)

        assertEquals(24, viewModel.widgetAreaState.value.widgetSpacingDp)

        settingsRepository.setWidgetSpacing(WidgetSpacing.EXTRA_LARGE)
        advanceUntilIdle()

        assertEquals(32, viewModel.widgetAreaState.value.widgetSpacingDp)
    }

    private fun TestScope.collectWidgetAreaState(viewModel: WidgetAreaViewModel) {
        backgroundScope.launch { viewModel.widgetAreaState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.collectMoveState(viewModel: WidgetAreaViewModel) {
        backgroundScope.launch { viewModel.moveState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `shows the system's first eligible Media session`() = runTest {
        val first: MediaSessionSnapshot = mediaSession(key = "first", title = "First")
        val second: MediaSessionSnapshot = mediaSession(key = "second", title = "Second")
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val mediaSessionSource: FakeMediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(first, second))
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            mediaSessionSource = mediaSessionSource
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = first), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the Media Player when disabled or without notification access`() = runTest {
        val session: MediaSessionSnapshot = mediaSession()
        val disabled: WidgetAreaViewModel = createViewModel(
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(session))
        )
        disabled.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        val enabledSettings: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val noAccess: WidgetAreaViewModel = createViewModel(
            settingsRepository = enabledSettings,
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(session), hasAccess = false)
        )
        noAccess.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the Media Player when no session is active`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
            ),
            mediaSessionSource = FakeMediaSessionSource()
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marks the Header Media Player toggle as needing notification access`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
            ),
            mediaSessionSource = FakeMediaSessionSource(hasAccess = false)
        )
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val menu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val toggle: WidgetMenuItem.Toggle =
                menu.items.single { item -> item.id == WidgetMenuAction.MediaPlayer } as WidgetMenuItem.Toggle
            assertEquals(R.string.needs_notification_access, toggle.supportingText)
            assertTrue(toggle.rowOpensAction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the Media Player while a Home edit mode is active`() = runTest {
        val session: MediaSessionSnapshot = mediaSession()
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val homeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            homeEditState = homeEditState,
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(session))
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = session), expectMostRecentItem())
            homeEditState.tryEnter(HomeEditMode.MovingWidgetArea)
            assertEquals(MediaPlayerUiState.Hidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opens the Media Player menu and forwards transport commands`() = runTest {
        val session: MediaSessionSnapshot = mediaSession(
            key = "first",
            actions = setOf(MediaCommand.Play, MediaCommand.Pause, MediaCommand.Next)
        )
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val mediaSessionSource: FakeMediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(session))
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            mediaSessionSource = mediaSessionSource
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = session), expectMostRecentItem())
            viewModel.menuState.test {
                viewModel.openMediaPlayerMenu()
                val menu: WidgetMenuState.MediaPlayerMenu = expectMostRecentItem() as WidgetMenuState.MediaPlayerMenu
                assertEquals(session.appLabel, menu.label)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onMediaCommand(MediaCommand.Pause)
            viewModel.onMediaCommand(MediaCommand.Next)
            viewModel.openMediaPlayer()
            assertEquals(
                listOf(session.key to MediaCommand.Pause, session.key to MediaCommand.Next),
                mediaSessionSource.commands
            )
            assertEquals(listOf(session.key), mediaSessionSource.opened)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enabling the Media Player without notification access requests it`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            mediaSessionSource = FakeMediaSessionSource(hasAccess = false)
        )
        viewModel.events.test {
            viewModel.setMediaPlayerEnabled(enabled = true)
            assertEquals(WidgetAreaEvent.RequestNotificationAccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(settingsRepository.mediaPlayerUpdates.last().enabled)
    }

    @Test
    fun `exposes the Media Player placement and artwork options`() = runTest {
        val session: MediaSessionSnapshot = mediaSession()
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                initialMediaPlayerSettings = MediaPlayerSettings(
                    enabled = true,
                    placement = MediaPlayerPlacement.TOP_OF_WIDGET_AREA,
                    showArtwork = false
                )
            ),
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(session))
        )
        viewModel.mediaPlayerState.test {
            assertEquals(
                MediaPlayerUiState.Visible(
                    session = session,
                    placement = MediaPlayerPlacement.TOP_OF_WIDGET_AREA,
                    showArtwork = false
                ),
                expectMostRecentItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `excluding the shown app emits Undo and selects the next session`() = runTest {
        val first: MediaSessionSnapshot =
            mediaSession(key = "first", title = "First", packageName = "com.example.first", appLabel = "First")
        val second: MediaSessionSnapshot =
            mediaSession(key = "second", title = "Second", packageName = "com.example.second", appLabel = "Second")
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(first, second))
        )
        backgroundScope.launch { viewModel.mediaPlayerState.collect {} }
        advanceUntilIdle()
        assertEquals(MediaPlayerUiState.Visible(session = first), viewModel.mediaPlayerState.value)
        viewModel.events.test {
            viewModel.excludeMediaApp()
            assertEquals(
                WidgetAreaEvent.ShowUndo(
                    action = UndoAction.IncludeMediaApp(packageName = first.packageName, appLabel = first.appLabel)
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertEquals(MediaPlayerUiState.Visible(session = second), viewModel.mediaPlayerState.value)
    }

    @Test
    fun `undoing an exclusion restores the excluded app`() = runTest {
        val first: MediaSessionSnapshot =
            mediaSession(key = "first", title = "First", packageName = "com.example.first", appLabel = "First")
        val second: MediaSessionSnapshot =
            mediaSession(key = "second", title = "Second", packageName = "com.example.second", appLabel = "Second")
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = settingsRepository,
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(first, second))
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = first), expectMostRecentItem())
            viewModel.excludeMediaApp()
            advanceUntilIdle()
            assertEquals(MediaPlayerUiState.Visible(session = second), expectMostRecentItem())
            viewModel.undo(action = UndoAction.IncludeMediaApp(packageName = first.packageName))
            advanceUntilIdle()
            assertEquals(MediaPlayerUiState.Visible(session = first), expectMostRecentItem())
            assertTrue(settingsRepository.mediaPlayerUpdates.last().excludedPackages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Media Player menu offers excluding the shown app`() = runTest {
        val session: MediaSessionSnapshot = mediaSession(appLabel = "Video")
        val viewModel: WidgetAreaViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                initialMediaPlayerSettings = MediaPlayerSettings(enabled = true)
            ),
            mediaSessionSource = FakeMediaSessionSource(initialSessions = listOf(session))
        )
        viewModel.menuState.test {
            viewModel.openMediaPlayerMenu()
            val menu: WidgetMenuState.MediaPlayerMenu = expectMostRecentItem() as WidgetMenuState.MediaPlayerMenu
            val exclude: WidgetMenuItem.Action = menu.items[1] as WidgetMenuItem.Action
            assertEquals(WidgetMenuAction.ExcludeMediaApp, exclude.id)
            assertEquals("Video", exclude.labelArg)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Add widget from an App Widget menu starts below that widget`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)

        viewModel.openAppWidgetMenu(widgetId = 2L)
        viewModel.onMenuAction(WidgetMenuAction.AddWidget)
        advanceUntilIdle()

        val actualChoosing: AddWidgetFlowState.ChoosingPlacement =
            viewModel.addFlowState.value as AddWidgetFlowState.ChoosingPlacement
        assertEquals(InsertPosition.Below(placeId = 2L), actualChoosing.origin.insertAt)
    }

    @Test
    fun `add flow events reach the launcher`() = runTest {
        val actualViewModel: WidgetAreaViewModel = createViewModel()

        actualViewModel.events.test {
            actualViewModel.startAdd(origin = settingsAddOrigin())
            actualViewModel.cancelAdd()

            assertEquals(WidgetAreaEvent.ReturnToSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setPickerQuery reaches the picker state`() = runTest {
        val actualViewModel: WidgetAreaViewModel = createViewModel()
        actualViewModel.startAdd(origin = AddWidgetOrigin(insertAt = InsertPosition.End))

        actualViewModel.setPickerQuery(query = "mail")

        val actualPicking: AddWidgetFlowState.Picking = actualViewModel.addFlowState.value as AddWidgetFlowState.Picking
        assertEquals("mail", actualPicking.query)
    }

    @Test
    fun `onBindResult places the App Widget after the dialog`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo()),
            bindResults = listOf(false)
        )
        val actualViewModel: WidgetAreaViewModel = createViewModel(repository = mockRepository, appWidgetHost = mockHost)
        advanceUntilIdle()
        actualViewModel.startAdd(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        actualViewModel.pickProvider(provider = appWidgetProviderInfo())
        advanceUntilIdle()

        actualViewModel.onBindResult(granted = true)
        advanceUntilIdle()

        assertEquals(2, mockRepository.places.first().size)
        assertEquals(AddWidgetFlowState.Idle, actualViewModel.addFlowState.value)
    }

    @Test
    fun `onConfigureResult failure reports a message and deletes the id`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo(needsConfiguration = true)),
            bindResults = listOf(true)
        )
        val actualViewModel: WidgetAreaViewModel = createViewModel(repository = mockRepository, appWidgetHost = mockHost)
        advanceUntilIdle()
        actualViewModel.events.test {
            actualViewModel.startAdd(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
            actualViewModel.pickProvider(provider = appWidgetProviderInfo(needsConfiguration = true))
            advanceUntilIdle()
            assertEquals(WidgetAreaEvent.LaunchConfigure(AppWidgetId(1)), awaitItem())

            actualViewModel.onConfigureResult(ok = false)
            advanceUntilIdle()

            assertEquals(WidgetAreaEvent.ShowMessage(R.string.couldnt_add_widget), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(AppWidgetId(1)), mockHost.deletedIds)
        assertEquals(1, mockRepository.places.first().size)
    }

    @Test
    fun `adding into a stack reports the grown page size`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo(isVerticallyResizable = true)),
            bindResults = listOf(true)
        )
        val report: WidgetSizeReport = WidgetSizeReport(
            appWidgetIds = listOf(AppWidgetId(1)),
            widthDp = 240,
            heightDp = 64
        )
        val resizeController: TestWidgetResizeController = TestWidgetResizeController(stackGrownReport = report)
        val actualViewModel: WidgetAreaViewModel = createViewModel(
            repository = mockRepository,
            appWidgetHost = mockHost,
            resizeController = resizeController
        )
        actualViewModel.openHeaderMenu()
        actualViewModel.startAddFromMenu()
        advanceUntilIdle()
        actualViewModel.choosePlacement(placement = AddWidgetPlacement.IntoStack)

        actualViewModel.events.test {
            actualViewModel.pickProvider(provider = appWidgetProviderInfo(isVerticallyResizable = true))
            advanceUntilIdle()

            assertEquals(
                WidgetAreaEvent.ReportWidgetSize(
                    appWidgetIds = listOf(AppWidgetId(1)),
                    widthDp = 240,
                    heightDp = 64
                ),
                awaitItem()
            )
            assertEquals(listOf(1L), resizeController.stackGrownPlaceIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun resizeState(): ResizeState = ResizeState(
        placeId = 2L,
        bounds = ResizeBounds(minDp = 48, maxDp = 240),
        defaultHeightDp = 48,
        draftHeightDp = 112
    )

    private class TestWidgetResizeController(
        override val resizeState: MutableStateFlow<ResizeState?> = MutableStateFlow(null),
        override val homeHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null),
        private val startResult: Boolean = true,
        private val finish: ResizeFinish? = null,
        private val report: WidgetSizeReport? = null,
        val stackGrownReport: WidgetSizeReport? = null
    ) : IWidgetResizeController {
        val startedPlaceIds: MutableList<Long> = mutableListOf()
        val persistedFinishes: MutableList<ResizeFinish> = mutableListOf()
        val stackGrownPlaceIds: MutableList<Long> = mutableListOf()
        val widgetAreaWidths: MutableList<Int> = mutableListOf()
        val placesChanges: MutableList<List<WidgetPlace>> = mutableListOf()
        var finishedCount: Int = 0

        override fun setHomeHeight(heightDp: Int) {
            homeHeightDp.value = heightDp
        }

        override fun setWidgetAreaWidth(widthDp: Int) {
            widgetAreaWidths += widthDp
        }

        override suspend fun startResize(placeId: Long, headerHeightDp: Int?): Boolean {
            startedPlaceIds += placeId
            return startResult
        }

        override suspend fun startResizeForWidget(widgetId: Long, headerHeightDp: Int?): Boolean = startResult

        override fun dragResize(deltaDp: Float) = Unit

        override fun resetResize() = Unit

        override fun finishResize(): ResizeFinish? {
            if (resizeState.value == null) {
                return null
            }
            finishedCount += 1
            resizeState.value = null
            return finish
        }

        override suspend fun persistResize(finish: ResizeFinish): WidgetSizeReport? {
            persistedFinishes += finish
            return report
        }

        override fun cancelResize() {
            resizeState.value = null
        }

        override fun onPlacesChanged(places: List<WidgetPlace>) {
            placesChanges += places
        }

        override suspend fun onStackGrown(placeId: Long, headerHeightDp: Int?): WidgetSizeReport? {
            stackGrownPlaceIds += placeId
            return stackGrownReport
        }

        override suspend fun clampStackHeight(placeId: Long, headerHeightDp: Int?) = Unit
    }

    private class TestWidgetLifecycleReconciler : IWidgetLifecycleReconciler {
        private val removed: MutableSharedFlow<AppWidgetId> = MutableSharedFlow(extraBufferCapacity = 1)
        override val isStartupReconciled: StateFlow<Boolean> = MutableStateFlow(true)
        override val removedIds: Flow<AppWidgetId> = removed

        fun emitRemovedId(id: AppWidgetId) {
            removed.tryEmit(id)
        }

        override suspend fun reconcileAtStartup(pendingIds: Set<AppWidgetId>) = Unit

        override suspend fun removeGoneProviders() = Unit
    }

    private fun createViewModel(
        repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
        clockSource: FakeClockSource = FakeClockSource(reading()),
        settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
        homeEditState: HomeEditState = HomeEditState(),
        launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList()),
        calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(),
        batterySource: FakeBatterySource = FakeBatterySource(BatteryStatus(percent = 84, isCharging = false)),
        appWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(),
        widgetLifecycleReconciler: IWidgetLifecycleReconciler = TestWidgetLifecycleReconciler(),
        homeHeightState: HomeHeightState = HomeHeightState(),
        mediaSessionSource: FakeMediaSessionSource = FakeMediaSessionSource(),
        resizeController: IWidgetResizeController = TestWidgetResizeController(),
        removeAppWidget: IRemoveAppWidget = FakeRemoveAppWidget()
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
        removeAppWidget = removeAppWidget,
        addFlowController = createAddController(
            repository = repository,
            appWidgetHost = appWidgetHost,
            settingsRepository = settingsRepository,
            homeEditState = homeEditState
        ),
        widgetLifecycleReconciler = widgetLifecycleReconciler,
        homeHeightState = homeHeightState,
        mediaSessionSource = mediaSessionSource,
        headerSettingsActions = createHeaderSettingsActions(
            settingsRepository = settingsRepository,
            calendarEventSource = calendarEventSource
        ),
        tapTargetResolver = IHeaderTapTargetResolverImpl(),
        resizeController = resizeController
    )

    @Test
    fun `remove widget reports the freed system id and host view`() = runTest {
        val removeAppWidget: FakeRemoveAppWidget = FakeRemoveAppWidget(result = AppWidgetId(9))
        val viewModel: WidgetAreaViewModel = createViewModel(removeAppWidget = removeAppWidget)
        viewModel.events.test {
            viewModel.removeWidget(widgetId = 2L)
            assertEquals(WidgetAreaEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            assertEquals(WidgetAreaEvent.ShowMessage(CoreWidgetsR.string.widget_removed), awaitItem())
            assertEquals(listOf(2L), removeAppWidget.removedWidgetIds)
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
        assertFalse(viewModel.startAdd(AddWidgetOrigin(InsertPosition.End)))
        assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)
    }

    @Test
    fun `offers Reconfigure only for an available reconfigurable provider`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isReconfigurable = true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(reconfigure = true), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Reconfigure while the widget is unavailable`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(
                headerPlace,
                appWidgetPlace(placeId = 2L, widgetId = 2L, availability = WidgetAvailability.ProfilePaused)
            )
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isReconfigurable = true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Reconfigure when the provider has no configure activity`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(isReconfigurable = true, hasConfigureActivity = false)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reconfigure launches the provider and closes the menu`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isReconfigurable = true))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(WidgetMenuAction.Reconfigure, menu.items.first().id)
            viewModel.onMenuAction(WidgetMenuAction.Reconfigure)
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.events.test {
            assertEquals(WidgetAreaEvent.LaunchReconfigure(AppWidgetId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(host.deletedIds.isEmpty())
    }

    @Test
    fun `a failed reconfigure launch reports a message and keeps the widget`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.events.test {
            viewModel.onReconfigureResult(ok = false)
            assertEquals(WidgetAreaEvent.ShowMessage(R.string.couldnt_open_widget_settings), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.places.first().size)
        assertTrue(repository.addedAppWidgets.isEmpty())
    }

    @Test
    fun `a successful reconfigure keeps the widget without a message`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.events.test {
            viewModel.onReconfigureResult(ok = true)
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repository.places.first().size)
    }

    @Test
    fun `closes the App Widget menu when the widget disappears`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            expectState<WidgetMenuState.AppWidgetMenu>()
            viewModel.removeWidget(widgetId = 2L)
            advanceUntilIdle()
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a paused work widget asks to leave quiet mode`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(
                headerPlace,
                appWidgetPlace(
                    placeId = 2L,
                    widgetId = 2L,
                    profileSerial = 12L,
                    availability = WidgetAvailability.ProfilePaused
                )
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.events.test {
            viewModel.unpauseWorkProfile(widgetId = 2L)
            assertEquals(WidgetAreaEvent.RequestQuietModeOff(profileSerial = 12L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops cached host views when reconciliation removes a widget`() = runTest {
        val reconciler: TestWidgetLifecycleReconciler = TestWidgetLifecycleReconciler()
        val viewModel: WidgetAreaViewModel = createViewModel(widgetLifecycleReconciler = reconciler)
        advanceUntilIdle()
        viewModel.events.test {
            reconciler.emitRemovedId(id = AppWidgetId(9))
            assertEquals(WidgetAreaEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `App Widget models carry availability and the stored default height`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(
                headerPlace,
                appWidgetPlace(
                    placeId = 2L,
                    widgetId = 2L,
                    availability = WidgetAvailability.ProfilePaused,
                    defaultHeightDp = 72
                )
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.widgetAreaState.test {
            val model: AppWidgetModel = expectMostRecentItem().places.last().widgets.single() as AppWidgetModel
            assertEquals(WidgetAvailability.ProfilePaused, model.availability)
            assertEquals(72, model.minHeightDp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offers Resize first for an available vertically resizable widget`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(resize = true), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Resize while the widget is unavailable`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(
                headerPlace,
                appWidgetPlace(placeId = 2L, widgetId = 2L, availability = WidgetAvailability.ProfilePaused)
            )
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Resize when the provider is not vertically resizable`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(appWidgetProviderInfo(isVerticallyResizable = false))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), menu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offers Resize without the provider list having been collected`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, appWidgetId = 9))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        host.providerInfoById[AppWidgetId(9)] = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertTrue(menu.items.map { item -> item.id }.contains(WidgetMenuAction.Resize))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startResize delegates to the controller and closes the menu`() = runTest {
        val resizeController: TestWidgetResizeController = TestWidgetResizeController(startResult = true)
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = FakeWidgetAreaRepository(listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))),
            resizeController = resizeController
        )
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            expectState<WidgetMenuState.AppWidgetMenu>()
            viewModel.startResize(placeId = 2L)
            advanceUntilIdle()
            assertEquals(listOf(2L), resizeController.startedPlaceIds)
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishResize persists through the controller and emits its size report`() = runTest {
        val finish: ResizeFinish = ResizeFinish(
            placeId = 2L,
            storedHeightDp = 112,
            appWidgetIds = listOf(AppWidgetId(9)),
            widthDp = 240,
            heightDp = 112
        )
        val report: WidgetSizeReport = WidgetSizeReport(
            appWidgetIds = listOf(AppWidgetId(9)),
            widthDp = 240,
            heightDp = 112
        )
        val resizeController: TestWidgetResizeController = TestWidgetResizeController(
            resizeState = MutableStateFlow(resizeState()),
            finish = finish,
            report = report
        )
        val viewModel: WidgetAreaViewModel = createViewModel(resizeController = resizeController)

        viewModel.events.test {
            viewModel.finishResize()
            advanceUntilIdle()
            assertEquals(
                WidgetAreaEvent.ReportWidgetSize(appWidgetIds = listOf(AppWidgetId(9)), widthDp = 240, heightDp = 112),
                awaitItem()
            )
            assertEquals(listOf(finish), resizeController.persistedFinishes)
            assertNull(resizeController.resizeState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Back and Home finish Resize and are consumed while resizing`() = runTest {
        val finish: ResizeFinish = ResizeFinish(
            placeId = 2L,
            storedHeightDp = 112,
            appWidgetIds = listOf(AppWidgetId(9)),
            widthDp = 240,
            heightDp = 112
        )
        val resizeController: TestWidgetResizeController = TestWidgetResizeController(
            resizeState = MutableStateFlow(resizeState()),
            finish = finish
        )
        val viewModel: WidgetAreaViewModel = createViewModel(resizeController = resizeController)

        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        resizeController.resizeState.value = resizeState()
        assertTrue(viewModel.onHomeIntent())
        advanceUntilIdle()

        assertEquals(2, resizeController.finishedCount)
        assertNull(resizeController.resizeState.value)
        assertFalse(viewModel.onBack())
        assertFalse(viewModel.onHomeIntent())
    }

    @Test
    fun `forwards the Home height and Widget Area width to the resize controller`() = runTest {
        val resizeController: TestWidgetResizeController = TestWidgetResizeController()
        val viewModel: WidgetAreaViewModel = createViewModel(resizeController = resizeController)

        viewModel.setHomeHeight(heightDp = 400)
        viewModel.setWidgetAreaWidth(widthDp = 240)

        assertEquals(400, resizeController.homeHeightDp.value)
        assertEquals(listOf(240), resizeController.widgetAreaWidths)
    }

    @Test
    fun `forwards current places to the resize controller`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val resizeController: TestWidgetResizeController = TestWidgetResizeController()
        createViewModel(repository = repository, resizeController = resizeController)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), resizeController.placesChanges.last().map { place -> place.id })

        repository.removeWidget(widgetId = 2L)
        advanceUntilIdle()

        assertEquals(listOf(1L), resizeController.placesChanges.last().map { place -> place.id })
    }

    @Test
    fun `renders a stored height clamped to the current bounds`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 400))
        )
        val host: FakeAppWidgetHostSource = hostWithProviderInfo(
            appWidgetProviderInfo(minResizeHeightDp = 48, maxResizeHeightDp = 200, isVerticallyResizable = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.setHomeHeight(heightDp = 600)
        viewModel.widgetAreaState.test {
            val place: WidgetPlaceModel = expectMostRecentItem().places.last()
            assertEquals(200, place.heightDp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Edit Widgets from the Header menu enters the mode with a draft of the current order`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, homeEditState = homeEditState)
        viewModel.openHeaderMenu()
        viewModel.onMenuAction(WidgetMenuAction.EditWidgets)
        advanceUntilIdle()
        assertEquals(HomeEditMode.Widgets, homeEditState.mode.value)
        assertEquals(listOf(1L, 2L), viewModel.editState.value?.placeIds)
        assertEquals(emptySet<Long>(), viewModel.editState.value?.removedWidgetIds)
    }

    @Test
    fun `Edit Widgets from an App Widget menu enters the mode`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, homeEditState = homeEditState)
        viewModel.openAppWidgetMenu(widgetId = 2L)
        viewModel.onMenuAction(WidgetMenuAction.EditWidgets)
        advanceUntilIdle()
        assertEquals(HomeEditMode.Widgets, homeEditState.mode.value)
        assertEquals(listOf(1L, 2L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `Edit Widgets is ignored while Favorites or Resize is active`() = runTest {
        val homeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val viewModel: WidgetAreaViewModel = createViewModel(homeEditState = homeEditState)
        viewModel.editWidgets()
        advanceUntilIdle()
        assertEquals(HomeEditMode.Favorites, homeEditState.mode.value)
        assertNull(viewModel.editState.value)
        homeEditState.exit(HomeEditMode.Favorites)
        homeEditState.tryEnter(HomeEditMode.Resizing(placeId = 1L))
        viewModel.editWidgets()
        advanceUntilIdle()
        assertEquals(HomeEditMode.Resizing(1L), homeEditState.mode.value)
        assertNull(viewModel.editState.value)
    }

    @Test
    fun `moving a place in Edit Widgets reorders the draft and rejects out of range moves`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L), appWidgetPlace(placeId = 3L, widgetId = 3L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.moveWidgetPlace(fromIndex = 0, toIndex = 2)
        assertEquals(listOf(2L, 3L, 1L), viewModel.editState.value?.placeIds)
        viewModel.moveWidgetPlace(fromIndex = 0, toIndex = 3)
        viewModel.moveWidgetPlace(fromIndex = -1, toIndex = 1)
        assertEquals(listOf(2L, 3L, 1L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `removing a place in Edit Widgets only changes the draft`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        advanceUntilIdle()
        val draft: WidgetEditDraft = viewModel.editState.value!!
        assertEquals(listOf(1L), draft.placeIds)
        assertFalse(draft.places.any { place -> place.placeId == 2L })
        assertEquals(setOf(2L), draft.removedWidgetIds)
        assertTrue(repository.edits.isEmpty())
        assertTrue(repository.removedHeaders.isEmpty())
        assertTrue(host.deletedIds.isEmpty())
        assertEquals(listOf(1L, 2L), repository.places.first().map { place -> place.id })
    }

    @Test
    fun `widgetAreaState renders the draft order and flags editing`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.widgetAreaState.test {
            assertEquals(listOf(1L, 2L), expectMostRecentItem().places.map { place -> place.id })
            viewModel.editWidgets()
            advanceUntilIdle()
            viewModel.moveWidgetPlace(fromIndex = 0, toIndex = 1)
            val moved: WidgetAreaState = expectMostRecentItem()
            assertTrue(moved.isEditing)
            assertEquals(listOf(2L, 1L), moved.places.map { place -> place.id })
            viewModel.removeWidgetPlaceInEdit(placeId = 2L)
            val removed: WidgetAreaState = expectMostRecentItem()
            assertEquals(listOf(1L), removed.places.map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Done commits the draft, deletes freed ids and exits only after persistence`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val viewModel: WidgetAreaViewModel = createViewModel(
            repository = repository,
            homeEditState = homeEditState,
            appWidgetHost = host
        )
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.moveWidgetPlace(fromIndex = 0, toIndex = 1)
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        viewModel.events.test {
            viewModel.finishEditingWidgets()
            assertEquals(WidgetAreaEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertEquals(
            listOf(
                WidgetAreaEdit(
                    places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L))),
                    removedWidgetIds = setOf(2L)
                )
            ),
            repository.edits
        )
        assertEquals(listOf(AppWidgetId(9)), host.deletedIds)
        assertEquals(listOf(1L), repository.places.first().map { place -> place.id })
        assertNull(viewModel.editState.value)
        assertEquals(HomeEditMode.None, homeEditState.mode.value)
    }

    @Test
    fun `Done twice commits and frees ids once`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        viewModel.finishEditingWidgets()
        viewModel.finishEditingWidgets()
        advanceUntilIdle()
        assertEquals(1, repository.edits.size)
        assertEquals(listOf(AppWidgetId(9)), host.deletedIds)
        assertNull(viewModel.editState.value)
    }

    @Test
    fun `Back commits Edit Widgets and is consumed`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, homeEditState = homeEditState)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        assertEquals(
            listOf(
                WidgetAreaEdit(
                    places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L))),
                    removedWidgetIds = setOf(2L)
                )
            ),
            repository.edits
        )
        assertEquals(HomeEditMode.None, homeEditState.mode.value)
    }

    @Test
    fun `Home commits Edit Widgets and is consumed`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val homeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, homeEditState = homeEditState)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        assertTrue(viewModel.onHomeIntent())
        advanceUntilIdle()
        assertEquals(
            listOf(
                WidgetAreaEdit(
                    places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L))),
                    removedWidgetIds = setOf(2L)
                )
            ),
            repository.edits
        )
        assertEquals(HomeEditMode.None, homeEditState.mode.value)
    }

    @Test
    fun `a vanished place is dropped from the draft`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.editWidgets()
        advanceUntilIdle()
        repository.removeWidget(widgetId = 2L)
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `a place added while editing is appended to the draft`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        viewModel.editWidgets()
        advanceUntilIdle()
        repository.addAppWidget(
            AddAppWidget(
                appWidgetId = AppWidgetId(7),
                provider = WidgetProvider(packageName = "com.example", className = "AddedWidget"),
                profileSerial = 0L,
                insertAt = InsertPosition.End,
                defaultHeightDp = 48
            )
        )
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `hides Replace Header when there is no Header`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 1L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertFalse(menu.items.any { item -> item.id == WidgetMenuAction.ReplaceHeader })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Replace Header when the Header shares the widget's stack`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(stackPlace()))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertFalse(menu.items.any { item -> item.id == WidgetMenuAction.ReplaceHeader })
            val extend: WidgetMenuItem.Toggle = menu.items
                .filterIsInstance<WidgetMenuItem.Toggle>()
                .first { item -> item.id == WidgetMenuAction.ExtendUnderRail }
            assertFalse(extend.enabled)
            assertEquals(CoreWidgetsR.string.not_available_in_stack, extend.supportingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders the stack page menu with Edit widgets before Remove from stack`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(stackPlace()))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()

            assertEquals(
                expectedAppWidgetMenuOrder(resize = true, replaceHeader = false, removeFromStack = true),
                menu.items.map { item -> item.id }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Replace Header when a Header exists outside the widget's place`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository, appWidgetHost = host)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val menu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), menu.items.map { item -> item.id })
            val extend: WidgetMenuItem.Toggle = menu.items
                .filterIsInstance<WidgetMenuItem.Toggle>()
                .single { item -> item.id == WidgetMenuAction.ExtendUnderRail }
            assertTrue(extend.enabled)
            assertEquals(CoreWidgetsR.string.extend_under_rail_supporting_text, extend.supportingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaces the Header, emits Undo and undoes through the repository`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)
        var action: UndoAction.ReplaceHeader? = null

        viewModel.events.test {
            viewModel.replaceHeader(widgetId = 2L)
            val event: WidgetAreaEvent.ShowUndo = awaitItem() as WidgetAreaEvent.ShowUndo
            action = event.action as UndoAction.ReplaceHeader
            assertEquals(1L, action!!.undo.headerPlaceId)
            assertEquals(2L, action!!.undo.widgetPlaceId)
        }
        assertEquals(listOf(2L), repository.replacedWidgetIds)
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)

        viewModel.undo(action = action!!)
        advanceUntilIdle()
        assertEquals(1, repository.undoneReplaceHeaders.size)
    }

    @Test
    fun `stores Extend under Rail only for a lone widget`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(repository = repository)

        viewModel.setExtendsUnderRail(widgetId = 1L, extendsUnderRail = true)
        advanceUntilIdle()
        assertEquals(listOf(1L to true), repository.extendsUnderRailUpdates)

        val stacked: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(
                PlacedWidget.AppWidget(
                    id = 1L,
                    appWidgetId = 9,
                    provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
                    profileSerial = 0L,
                    extendsUnderRail = false
                ),
                PlacedWidget.AppWidget(
                    id = 2L,
                    appWidgetId = 10,
                    provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
                    profileSerial = 0L,
                    extendsUnderRail = false
                )
            )
        )
        val stackedRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(stacked))
        val stackedViewModel: WidgetAreaViewModel = createViewModel(repository = stackedRepository)
        stackedViewModel.setExtendsUnderRail(widgetId = 1L, extendsUnderRail = true)
        advanceUntilIdle()
        assertTrue(stackedRepository.extendsUnderRailUpdates.isEmpty())
    }

    private fun expectedAppWidgetMenuOrder(
        resize: Boolean = false,
        reconfigure: Boolean = false,
        replaceHeader: Boolean = true,
        removeFromStack: Boolean = false
    ): List<WidgetMenuAction> = buildList {
        if (resize) add(WidgetMenuAction.Resize)
        if (reconfigure) add(WidgetMenuAction.Reconfigure)
        add(WidgetMenuAction.AddWidget)
        if (replaceHeader) add(WidgetMenuAction.ReplaceHeader)
        add(WidgetMenuAction.ExtendUnderRail)
        add(WidgetMenuAction.MoveWidgetArea)
        add(WidgetMenuAction.EditWidgets)
        add(if (removeFromStack) WidgetMenuAction.RemoveFromStack else WidgetMenuAction.RemoveWidget)
        add(WidgetMenuAction.WidgetSettings)
    }

    private fun hostWithProviderInfo(
        info: WidgetProviderInfo,
        appWidgetId: AppWidgetId = AppWidgetId(9),
        bindResults: List<Boolean> = emptyList()
    ): FakeAppWidgetHostSource = FakeAppWidgetHostSource(
        initialProviders = listOf(info),
        bindResults = bindResults
    ).apply {
        providerInfoById[appWidgetId] = info
    }

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

    private fun mediaSession(
        key: String = "session",
        title: String = "Song",
        artist: String? = "Artist",
        packageName: String = "com.example.music",
        appLabel: String = "Music",
        state: PlaybackStatus = PlaybackStatus.PLAYING,
        actions: Set<MediaCommand> = setOf(MediaCommand.Play, MediaCommand.Pause)
    ): MediaSessionSnapshot = MediaSessionSnapshot(
        key = MediaSessionKey(value = key),
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        artist = artist,
        artwork = null,
        state = state,
        actions = actions
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
