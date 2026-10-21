package feature.settings

import app.cash.turbine.test
import core.data.calendar.CalendarInfo
import core.data.icons.model.Icon
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.testing.FakeCalendarEventSource
import core.data.testing.FakeClockSource
import core.data.testing.FakeDatePatternSource
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.MainDispatcherRule
import core.data.time.ClockReading
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import feature.widgets.HeaderFormatter
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
class WidgetSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val headerPlace: WidgetPlace = WidgetPlace(
        id = 1L,
        heightDp = null,
        currentPage = 0,
        widgets = listOf(PlacedWidget.Header(id = 1L))
    )

    @Test
    fun `exposes the Header and both Header parts as shown by default`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(state.headerShown)
            assertTrue(state.clockVisible)
            assertTrue(state.dateVisible)
            assertEquals(BatteryVisibility.NEVER, state.batteryVisibility)
            assertTrue(state.canHideClock)
            assertTrue(state.canHideDate)
            assertEquals(ClockStyle.CLASSIC, state.clockStyle)
            assertEquals(TimeFormat.SYSTEM, state.timeFormat)
            assertFalse(state.showAmPm)
            assertTrue(state.canShowAmPm)
            assertEquals(DateFormatPreset.FULL, state.dateFormat)
            assertEquals(HeaderAlignment.START, state.alignment)
            assertEquals(HeaderSize.DEFAULT, state.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes Clock Style previews and live date format labels`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(ClockStyle.entries, state.clockStylePreviews.map { preview -> preview.style })
            assertEquals("2:05", state.clockStylePreviews.first().time?.text)
            assertEquals(DateFormatPreset.entries, state.dateFormatOptions.map { option -> option.preset })
            assertEquals(
                listOf("Tuesday, October 20", "Tue, Oct 20", "October 20", "Tuesday 20", "Tuesday", "10/20/2026"),
                state.dateFormatOptions.map { option -> option.label }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disables Show AM PM outside effective 12-hour time`() = runTest {
        val twentyFourHour: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(HeaderSettings(timeFormat = TimeFormat.H24))
        )
        twentyFourHour.uiState.test {
            assertFalse(expectMostRecentItem().canShowAmPm)
            cancelAndIgnoreRemainingEvents()
        }

        val systemTwentyFourHour: WidgetSettingsViewModel = createViewModel(
            clockSource = FakeClockSource(reading(isSystem24Hour = true))
        )
        systemTwentyFourHour.uiState.test {
            assertFalse(expectMostRecentItem().canShowAmPm)
            cancelAndIgnoreRemainingEvents()
        }

        val forcedTwelveHour: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(HeaderSettings(timeFormat = TimeFormat.H12)),
            clockSource = FakeClockSource(reading(isSystem24Hour = true))
        )
        forcedTwelveHour.uiState.test {
            assertTrue(expectMostRecentItem().canShowAmPm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists all Header style settings`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setClockStyle(ClockStyle.BOLD)
        viewModel.setTimeFormat(TimeFormat.H24)
        viewModel.setShowAmPm(show = true)
        viewModel.setDateFormat(DateFormatPreset.NUMERIC)
        viewModel.setAlignment(HeaderAlignment.END)
        viewModel.setSize(HeaderSize.EXTRA_LARGE)
        advanceUntilIdle()

        val settings: HeaderSettings = settingsRepository.updates.last()
        assertEquals(ClockStyle.BOLD, settings.clockStyle)
        assertEquals(TimeFormat.H24, settings.timeFormat)
        assertTrue(settings.showAmPm)
        assertEquals(DateFormatPreset.NUMERIC, settings.dateFormat)
        assertEquals(HeaderAlignment.END, settings.alignment)
        assertEquals(HeaderSize.EXTRA_LARGE, settings.size)
    }

    @Test
    fun `persists the battery visibility`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setBatteryVisibility(BatteryVisibility.ALWAYS)
        advanceUntilIdle()

        assertEquals(BatteryVisibility.ALWAYS, settingsRepository.updates.last().batteryVisibility)
    }

    @Test
    fun `allows hiding the clock and date when the battery is always shown`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
            )
        )

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(state.canHideClock)
            assertTrue(state.canHideDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the clock and date when the battery is always shown`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setClockVisible(visible = false)
        advanceUntilIdle()
        viewModel.setDateVisible(visible = false)
        advanceUntilIdle()

        assertFalse(settingsRepository.updates.last().clockVisible)
        assertFalse(settingsRepository.updates.last().dateVisible)
    }

    @Test
    fun `cannot hide the clock when it is the only visible Header part`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                HeaderSettings(clockVisible = true, dateVisible = false)
            )
        )

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(state.clockVisible)
            assertFalse(state.dateVisible)
            assertFalse(state.canHideClock)
            assertTrue(state.canHideDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cannot hide the date when it is the only visible Header part`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                HeaderSettings(clockVisible = false, dateVisible = true)
            )
        )

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertFalse(state.clockVisible)
            assertTrue(state.dateVisible)
            assertTrue(state.canHideClock)
            assertFalse(state.canHideDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejects hiding the last visible Header part`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = true, dateVisible = false)
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setClockVisible(visible = false)
        advanceUntilIdle()

        assertTrue(settingsRepository.updates.isEmpty())
    }

    @Test
    fun `restores the clock when the battery stops being always visible`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            HeaderSettings(clockVisible = false, dateVisible = false, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setBatteryVisibility(BatteryVisibility.NEVER)
        advanceUntilIdle()

        val updated: HeaderSettings = settingsRepository.updates.single()
        assertEquals(BatteryVisibility.NEVER, updated.batteryVisibility)
        assertTrue(updated.clockVisible)
    }

    @Test
    fun `removes and restores the Header`() = runTest {
        val areaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val viewModel: WidgetSettingsViewModel = createViewModel(areaRepository = areaRepository)

        viewModel.setHeaderShown(shown = false)
        advanceUntilIdle()
        assertEquals(1, areaRepository.removedHeaders.size)

        viewModel.setHeaderShown(shown = true)
        advanceUntilIdle()
        assertEquals(listOf(0), areaRepository.restoredHeaderPositions)
    }

    @Test
    fun `shows the Header as hidden when it is missing`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(areaRepository = FakeWidgetAreaRepository())

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().headerShown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the system default as the initial tap action`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(HeaderTapSetting.SystemDefault, state.timeTapAction.setting)
            assertEquals(HeaderTapSetting.SystemDefault, state.dateTapAction.setting)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_clock_default), state.timeTapAction.label)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_calendar_default), state.dateTapAction.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves tap action labels from Launchables, Nothing and the system default`() = runTest {
        val launchable: RailLaunchable = testLaunchable(name = "Café Maps")
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                HeaderSettings(
                    timeTapTarget = HeaderTapSetting.Launchable(key = launchable.key),
                    dateTapTarget = HeaderTapSetting.Nothing
                )
            ),
            launchableRepository = FakeLaunchableRepository(groups = listOf(LaunchableGroup.Builtin(items = listOf(launchable))))
        )

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(HeaderTapSetting.Launchable(key = launchable.key), state.timeTapAction.setting)
            assertEquals("Café Maps", (state.timeTapAction.label as TapActionLabel.Launchable).name)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_nothing), state.dateTapAction.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves an uninstalled tap action to the system default`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = FakeWidgetSettingsRepository(
                HeaderSettings(timeTapTarget = HeaderTapSetting.Launchable(key = "uninstalled"))
            ),
            launchableRepository = FakeLaunchableRepository(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(testLaunchable(name = "Maps"))))
            )
        )

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(HeaderTapSetting.SystemDefault, state.timeTapAction.setting)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_clock_default), state.timeTapAction.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters Launchables by name ignoring case and accents`() {
        val cafe: RailLaunchable = testLaunchable(name = "Café")
        val maps: RailLaunchable = testLaunchable(name = "Maps")
        val photos: RailLaunchable = testLaunchable(name = "Photos")
        val launchables: List<RailLaunchable> = listOf(cafe, maps, photos)

        assertEquals(listOf(cafe), filterLaunchables(launchables, "cafe"))
        assertEquals(listOf(cafe), filterLaunchables(launchables, "CAFÉ"))
        assertEquals(listOf(maps), filterLaunchables(launchables, "map"))
        assertEquals(launchables, filterLaunchables(launchables, ""))
    }

    @Test
    fun `exposes picker rows once the Launchables load`() = runTest {
        val launchable: RailLaunchable = testLaunchable(name = "Maps")
        val launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList(), isLoaded = false)
        val viewModel: WidgetSettingsViewModel = createViewModel(launchableRepository = launchableRepository)

        viewModel.tapActionPicker.test {
            assertNull(awaitItem())
            viewModel.openTapActionPicker(HeaderTapTargetPart.Date)
            val loading: TapActionPickerState = awaitItem() ?: error("Expected a picker state")
            assertEquals(HeaderTapTargetPart.Date, loading.part)
            assertFalse(loading.isLoaded)
            assertTrue(loading.launchables.isEmpty())

            launchableRepository.setGroups(groups = listOf(LaunchableGroup.Builtin(items = listOf(launchable))))
            val loaded: TapActionPickerState = awaitItem() ?: error("Expected a picker state")
            assertTrue(loaded.isLoaded)
            assertEquals(listOf("Maps"), loaded.launchables.map { row -> row.name })

            viewModel.setTapActionQuery("map")
            val filtered: TapActionPickerState = awaitItem() ?: error("Expected a picker state")
            assertEquals("map", filtered.query)
            assertEquals(listOf("Maps"), filtered.launchables.map { row -> row.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves a tap action immediately and closes the picker`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)
        val timeTarget: HeaderTapSetting = HeaderTapSetting.Launchable(key = "rail://maps")

        viewModel.tapActionPicker.test {
            assertNull(awaitItem())
            viewModel.openTapActionPicker(HeaderTapTargetPart.Time)
            assertEquals(HeaderTapTargetPart.Time, awaitItem()?.part)
            viewModel.setTimeTapTarget(timeTarget)
            advanceUntilIdle()
            assertEquals(timeTarget, settingsRepository.updates.last().timeTapTarget)
            assertNull(expectMostRecentItem())

            viewModel.openTapActionPicker(HeaderTapTargetPart.Date)
            assertEquals(HeaderTapTargetPart.Date, awaitItem()?.part)
            viewModel.setDateTapTarget(HeaderTapSetting.Nothing)
            advanceUntilIdle()
            assertEquals(HeaderTapSetting.Nothing, settingsRepository.updates.last().dateTapTarget)
            assertNull(expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes calendar event settings and calendars`() = runTest {
        val calendars: List<CalendarInfo> = listOf(
            CalendarInfo(id = 1L, name = "Personal", accountName = "me@example.com", color = 0, isWorkProfile = false),
            CalendarInfo(id = 5L, name = "Work", accountName = "work@example.com", color = 0, isWorkProfile = true)
        )
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialEventSettings = EventSettings(
                enabled = true,
                lookaheadHours = 6,
                maxEvents = 2,
                includeAllDay = false,
                excludedCalendarIds = setOf("w:5")
            )
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = true, calendars = calendars)
        )

        viewModel.uiState.test {
            val state: WidgetSettingsUiState = expectMostRecentItem()

            assertTrue(state.calendarAccess)
            assertTrue(state.eventsEnabled)
            assertEquals(6, state.eventLookaheadHours)
            assertEquals(2, state.eventMaxEvents)
            assertFalse(state.includeAllDay)
            assertEquals(setOf("w:5"), state.excludedCalendarIds)
            assertEquals(calendars, state.calendars)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prunes exclusions for calendars that no longer exist`() = runTest {
        val calendars: List<CalendarInfo> = listOf(
            CalendarInfo(id = 1L, name = "Personal", accountName = "me@example.com", color = 0, isWorkProfile = false)
        )
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialEventSettings = EventSettings(enabled = true, excludedCalendarIds = setOf("1", "2"))
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = true, calendars = calendars)
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            val state: WidgetSettingsUiState = expectMostRecentItem()

            assertEquals(setOf("1"), state.excludedCalendarIds)
            assertEquals(setOf("1"), settingsRepository.eventUpdates.last().excludedCalendarIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps exclusions while no calendars are loaded`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialEventSettings = EventSettings(enabled = true, excludedCalendarIds = setOf("1"))
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = true, calendars = emptyList())
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            val state: WidgetSettingsUiState = expectMostRecentItem()

            assertEquals(setOf("1"), state.excludedCalendarIds)
            assertTrue(settingsRepository.eventUpdates.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requests calendar permission without enabling events`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetSettingsViewModel = createViewModel(
            settingsRepository = settingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )

        viewModel.events.test {
            viewModel.setEventsEnabled(enabled = true)
            advanceUntilIdle()

            assertEquals(WidgetSettingsEvent.RequestCalendarPermission, awaitItem())
            assertTrue(settingsRepository.eventUpdates.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves event options`() = runTest {
        val settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val viewModel: WidgetSettingsViewModel = createViewModel(settingsRepository = settingsRepository)

        viewModel.setEventLookaheadHours(24)
        viewModel.setEventMaxEvents(3)
        viewModel.setEventIncludeAllDay(false)
        viewModel.setExcludedCalendarIds(setOf("1", "w:2"))
        advanceUntilIdle()

        val saved: EventSettings = settingsRepository.eventUpdates.last()
        assertEquals(24, saved.lookaheadHours)
        assertEquals(3, saved.maxEvents)
        assertFalse(saved.includeAllDay)
        assertEquals(setOf("1", "w:2"), saved.excludedCalendarIds)
    }

    private fun testLaunchable(name: String): RailLaunchable = RailLaunchable(
        defaultName = name,
        defaultIcon = Icon.ApplicationIcon(packageName = "com.example"),
        deeplink = "rail://${name.lowercase()}"
    )

    private fun createViewModel(
        settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
        areaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
        clockSource: FakeClockSource = FakeClockSource(reading()),
        launchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(groups = emptyList()),
        calendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource()
    ): WidgetSettingsViewModel = WidgetSettingsViewModel(
        settingsRepository = settingsRepository,
        areaRepository = areaRepository,
        clockSource = clockSource,
        formatter = HeaderFormatter(
            FakeDatePatternSource(
                mapOf(
                    "hm" to "h:mm a",
                    "Hm" to "HH:mm",
                    "EEEEMMMMd" to "EEEE, MMMM d",
                    "EEEMMMd" to "EEE, MMM d",
                    "MMMMd" to "MMMM d",
                    "EEEEd" to "EEEE d",
                    "EEEE" to "EEEE",
                    "yMd" to "M/d/y"
                )
            )
        ),
        launchableRepository = launchableRepository,
        calendarEventSource = calendarEventSource
    )

    private fun reading(isSystem24Hour: Boolean = false): ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = isSystem24Hour
    )
}
