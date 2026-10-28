package feature.settings

import app.cash.turbine.test
import core.data.calendar.CalendarInfo
import core.data.calendar.ICalendarEventSource
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.media.IMediaSessionSource
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.testing.MainDispatcherRule
import core.data.testing.WidgetAreaRepositoryState
import core.data.testing.WidgetSettingsRepositoryFixture
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createCalendarEventSourceMock
import core.data.testing.createClockSourceMock
import core.data.testing.createDatePatternSourceMock
import core.data.testing.createGrantedCalendarEventSourceMock
import core.data.testing.createHeaderSettingsActions
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createMediaSessionSourceMock
import core.data.testing.createRailLaunchable
import core.data.testing.createRemoveAppWidgetMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.createWidgetSettingsRepositoryFixture
import core.data.testing.headerPlace
import core.data.testing.personalCalendar
import core.data.testing.workCalendar
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.MediaPlayerSettings
import core.ui.R as CoreUiR
import core.ui.layout.HomeHeightState
import core.ui.widgets.HeaderFormatter
import core.ui.widgets.IHeaderTapTargetResolverImpl
import io.mockk.coVerify
import io.mockk.verify
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun `exposes the Header and both Header parts as shown by default`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(actualState.headerShown)
            assertTrue(actualState.clockVisible)
            assertTrue(actualState.dateVisible)
            assertEquals(BatteryVisibility.NEVER, actualState.batteryVisibility)
            assertTrue(actualState.canHideClock)
            assertTrue(actualState.canHideDate)
            assertEquals(ClockStyle.CLASSIC, actualState.clockStyle)
            assertEquals(TimeFormat.SYSTEM, actualState.timeFormat)
            assertFalse(actualState.showAmPm)
            assertTrue(actualState.canShowAmPm)
            assertEquals(DateFormatPreset.FULL, actualState.dateFormat)
            assertEquals(HeaderAlignment.START, actualState.alignment)
            assertEquals(HeaderSize.DEFAULT, actualState.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes Clock Style previews and live date format labels`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(ClockStyle.entries, actualState.clockStylePreviews.map { preview -> preview.style })
            assertEquals("2:05", actualState.clockStylePreviews.first().time?.text)
            assertEquals(DateFormatPreset.entries, actualState.dateFormatOptions.map { option -> option.preset })
            assertEquals(
                listOf("Tuesday, October 20", "Tue, Oct 20", "October 20", "Tuesday 20", "Tuesday", "10/20/2026"),
                actualState.dateFormatOptions.map { option -> option.label }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disables Show AM PM outside effective 12-hour time`() = runTest {
        val twentyFourHour: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(timeFormat = TimeFormat.H24)
            ).mockSettingsRepository
        )
        twentyFourHour.uiState.test {
            assertFalse(expectMostRecentItem().canShowAmPm)
            cancelAndIgnoreRemainingEvents()
        }

        val systemTwentyFourHour: WidgetSettingsViewModel = createViewModel(
            mockClockSource = createClockSourceMock(MutableStateFlow(reading(isSystem24Hour = true)))
        )
        systemTwentyFourHour.uiState.test {
            assertFalse(expectMostRecentItem().canShowAmPm)
            cancelAndIgnoreRemainingEvents()
        }

        val forcedTwelveHour: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(timeFormat = TimeFormat.H12)
            ).mockSettingsRepository,
            mockClockSource = createClockSourceMock(MutableStateFlow(reading(isSystem24Hour = true)))
        )
        forcedTwelveHour.uiState.test {
            assertTrue(expectMostRecentItem().canShowAmPm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists all Header style settings`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture()
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)

        viewModel.setClockStyle(ClockStyle.BOLD)
        viewModel.setTimeFormat(TimeFormat.H24)
        viewModel.setShowAmPm(show = true)
        viewModel.setDateFormat(DateFormatPreset.NUMERIC)
        viewModel.setAlignment(HeaderAlignment.END)
        viewModel.setSize(HeaderSize.EXTRA_LARGE)
        advanceUntilIdle()

        val actualSettings: HeaderSettings = inputSettings.state.headerUpdates.last()
        assertEquals(ClockStyle.BOLD, actualSettings.clockStyle)
        assertEquals(TimeFormat.H24, actualSettings.timeFormat)
        assertTrue(actualSettings.showAmPm)
        assertEquals(DateFormatPreset.NUMERIC, actualSettings.dateFormat)
        assertEquals(HeaderAlignment.END, actualSettings.alignment)
        assertEquals(HeaderSize.EXTRA_LARGE, actualSettings.size)
    }

    @Test
    fun `persists the battery visibility`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture()
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)

        viewModel.setBatteryVisibility(BatteryVisibility.ALWAYS)
        advanceUntilIdle()

        assertEquals(BatteryVisibility.ALWAYS, inputSettings.state.headerUpdates.last().batteryVisibility)
    }

    @Test
    fun `allows hiding the clock and date when the battery is always shown`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
            ).mockSettingsRepository
        )

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(actualState.canHideClock)
            assertTrue(actualState.canHideDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the clock and date when the battery is always shown`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture(
            headerSettings = HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)

        viewModel.setClockVisible(visible = false)
        advanceUntilIdle()
        viewModel.setDateVisible(visible = false)
        advanceUntilIdle()

        assertFalse(inputSettings.state.headerUpdates.last().clockVisible)
        assertFalse(inputSettings.state.headerUpdates.last().dateVisible)
    }

    @Test
    fun `cannot hide the clock when it is the only visible Header part`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(clockVisible = true, dateVisible = false)
            ).mockSettingsRepository
        )

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(actualState.clockVisible)
            assertFalse(actualState.dateVisible)
            assertFalse(actualState.canHideClock)
            assertTrue(actualState.canHideDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cannot hide the date when it is the only visible Header part`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(clockVisible = false, dateVisible = true)
            ).mockSettingsRepository
        )

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertFalse(actualState.clockVisible)
            assertTrue(actualState.dateVisible)
            assertTrue(actualState.canHideClock)
            assertFalse(actualState.canHideDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejects hiding the last visible Header part`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryFixture(
            headerSettings = HeaderSettings(clockVisible = true, dateVisible = false)
        ).mockSettingsRepository
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)

        viewModel.setClockVisible(visible = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockSettingsRepository.updateHeaderSettings(any()) }
    }

    @Test
    fun `never re-enables a hidden clock when the battery stops being always visible`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture(
            headerSettings = HeaderSettings(
                clockVisible = false,
                dateVisible = true,
                batteryVisibility = BatteryVisibility.ALWAYS
            )
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)

        viewModel.setBatteryVisibility(BatteryVisibility.NEVER)
        advanceUntilIdle()

        val actualSettings: HeaderSettings = inputSettings.state.headerUpdates.single()
        assertEquals(BatteryVisibility.NEVER, actualSettings.batteryVisibility)
        assertFalse(actualSettings.clockVisible)
    }

    @Test
    fun `rejects hiding the last visible Header part with the battery`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryFixture(
            headerSettings = HeaderSettings(
                clockVisible = false,
                dateVisible = false,
                batteryVisibility = BatteryVisibility.ALWAYS
            )
        ).mockSettingsRepository
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)

        viewModel.setBatteryVisibility(BatteryVisibility.NEVER)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockSettingsRepository.updateHeaderSettings(any()) }
    }

    @Test
    fun `removing an App Widget reports its id and a confirmation`() = runTest {
        val mockRemoveAppWidget: IRemoveAppWidget = createRemoveAppWidgetMock(result = AppWidgetId(9))
        val viewModel: WidgetSettingsViewModel = createViewModel(mockRemoveAppWidget = mockRemoveAppWidget)

        viewModel.events.test {
            viewModel.removeWidget(widgetId = 2L)

            assertEquals(WidgetSettingsEvent.AppWidgetRemoved(appWidgetId = AppWidgetId(9)), awaitItem())
            assertEquals(WidgetSettingsEvent.ShowMessage(message = CoreUiR.string.widget_removed), awaitItem())
            coVerify(exactly = 1) { mockRemoveAppWidget(widgetId = 2L) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removes and restores the Header`() = runTest {
        val mockAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            WidgetAreaRepositoryState(listOf(headerPlace))
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(mockAreaRepository = mockAreaRepository)

        viewModel.setHeaderShown(shown = false)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockAreaRepository.removeHeader() }

        viewModel.setHeaderShown(shown = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockAreaRepository.restoreHeader(position = 0, placeId = any(), pagePosition = any()) }
    }

    @Test
    fun `stores Extend under Rail for an App Widget`() = runTest {
        val mockAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock()
        val viewModel: WidgetSettingsViewModel = createViewModel(mockAreaRepository = mockAreaRepository)

        viewModel.setExtendsUnderRail(widgetId = 12L, extendsUnderRail = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockAreaRepository.setExtendsUnderRail(widgetId = 12L, extendsUnderRail = true) }
    }

    @Test
    fun `shows the Header as hidden when it is missing`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(mockAreaRepository = createWidgetAreaRepositoryMock())

        viewModel.uiState.test {
            assertFalse(expectMostRecentItem().headerShown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the system default as the initial tap action`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(HeaderTapSetting.SystemDefault, actualState.timeTapAction.setting)
            assertEquals(HeaderTapSetting.SystemDefault, actualState.dateTapAction.setting)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_clock_default), actualState.timeTapAction.label)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_calendar_default), actualState.dateTapAction.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves tap action labels from Launchables, Nothing and the system default`() = runTest {
        val inputLaunchable: RailLaunchable = createRailLaunchable(name = "Café Maps")
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(
                    timeTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key),
                    dateTapTarget = HeaderTapSetting.Nothing
                )
            ).mockSettingsRepository,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(inputLaunchable)))
            )
        )

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(HeaderTapSetting.Launchable(key = inputLaunchable.key), actualState.timeTapAction.setting)
            assertEquals("Café Maps", (actualState.timeTapAction.label as TapActionLabel.Launchable).name)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_nothing), actualState.dateTapAction.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves an uninstalled tap action to the system default`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                headerSettings = HeaderSettings(timeTapTarget = HeaderTapSetting.Launchable(key = "uninstalled"))
            ).mockSettingsRepository,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(createRailLaunchable(name = "Maps"))))
            )
        )

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()
            assertEquals(HeaderTapSetting.SystemDefault, actualState.timeTapAction.setting)
            assertEquals(TapActionLabel.Resource(R.string.tap_action_clock_default), actualState.timeTapAction.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters Launchables by name ignoring case and accents`() {
        val inputCafe: RailLaunchable = createRailLaunchable(name = "Café")
        val inputMaps: RailLaunchable = createRailLaunchable(name = "Maps")
        val inputPhotos: RailLaunchable = createRailLaunchable(name = "Photos")
        val inputFolder: Folder = Folder(folderId = 1L)
        val inputLaunchables: List<Launchable> = listOf(inputCafe, inputMaps, inputPhotos, inputFolder)

        assertEquals(listOf(inputCafe), filterLaunchables(inputLaunchables, "cafe"))
        assertEquals(listOf(inputCafe), filterLaunchables(inputLaunchables, "CAFÉ"))
        assertEquals(listOf(inputMaps), filterLaunchables(inputLaunchables, "map"))
        assertEquals(listOf(inputCafe, inputMaps, inputPhotos), filterLaunchables(inputLaunchables, ""))
    }

    @Test
    fun `exposes picker rows once the Launchables load`() = runTest {
        val inputLaunchable: RailLaunchable = createRailLaunchable(name = "Maps")
        val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputLaunchableState)
        val viewModel: WidgetSettingsViewModel = createViewModel(mockLaunchableRepository = mockLaunchableRepository)

        viewModel.tapActionPicker.test {
            assertNull(awaitItem())
            viewModel.openTapActionPicker(HeaderTapTargetPart.Date)
            val actualLoading: TapActionPickerState = awaitItem() ?: error("Expected a picker state")
            assertEquals(HeaderTapTargetPart.Date, actualLoading.part)
            assertFalse(actualLoading.isLoaded)
            assertTrue(actualLoading.launchables.isEmpty())

            inputLaunchableState.value = createLaunchableState(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(inputLaunchable))),
                folders = listOf(Folder(folderId = 1L))
            )
            val actualLoaded: TapActionPickerState = awaitItem() ?: error("Expected a picker state")
            assertTrue(actualLoaded.isLoaded)
            assertEquals(listOf("Maps"), actualLoaded.launchables.map { row -> row.name })

            viewModel.setTapActionQuery("map")
            val actualFiltered: TapActionPickerState = awaitItem() ?: error("Expected a picker state")
            assertEquals("map", actualFiltered.query)
            assertEquals(listOf("Maps"), actualFiltered.launchables.map { row -> row.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves a tap action immediately and closes the picker`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture()
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)
        val inputTimeTarget: HeaderTapSetting = HeaderTapSetting.Launchable(key = "rail://maps")

        viewModel.tapActionPicker.test {
            assertNull(awaitItem())
            viewModel.openTapActionPicker(HeaderTapTargetPart.Time)
            assertEquals(HeaderTapTargetPart.Time, awaitItem()?.part)
            viewModel.setTimeTapTarget(inputTimeTarget)
            advanceUntilIdle()
            assertEquals(inputTimeTarget, inputSettings.state.headerUpdates.last().timeTapTarget)
            assertNull(expectMostRecentItem())

            viewModel.openTapActionPicker(HeaderTapTargetPart.Date)
            assertEquals(HeaderTapTargetPart.Date, awaitItem()?.part)
            viewModel.setDateTapTarget(HeaderTapSetting.Nothing)
            advanceUntilIdle()
            assertEquals(HeaderTapSetting.Nothing, inputSettings.state.headerUpdates.last().dateTapTarget)
            assertNull(expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes calendar event settings and calendars`() = runTest {
        val inputCalendars: List<CalendarInfo> = listOf(personalCalendar, workCalendar)
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture(
            eventSettings = EventSettings(
                enabled = true,
                lookaheadHours = 6,
                maxEvents = 2,
                includeAllDay = false,
                excludedCalendarIds = setOf("w:5")
            )
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = inputSettings.mockSettingsRepository,
            mockCalendarEventSource = createGrantedCalendarEventSourceMock(calendars = inputCalendars)
        )

        viewModel.uiState.test {
            val actualState: WidgetSettingsUiState = expectMostRecentItem()

            assertTrue(actualState.calendarAccess)
            assertTrue(actualState.eventsEnabled)
            assertEquals(6, actualState.eventLookaheadHours)
            assertEquals(2, actualState.eventMaxEvents)
            assertFalse(actualState.includeAllDay)
            assertEquals(setOf("w:5"), actualState.excludedCalendarIds)
            assertEquals(inputCalendars, actualState.calendars)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lists calendars after permission is granted`() = runTest {
        val inputCalendars: List<CalendarInfo> = listOf(personalCalendar)
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(
            permission = inputPermission,
            onRefresh = { inputPermission.value = true },
            calendars = MutableStateFlow(inputCalendars)
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                eventSettings = EventSettings(enabled = false)
            ).mockSettingsRepository,
            mockCalendarEventSource = mockCalendarEventSource
        )
        viewModel.uiState.test {
            advanceUntilIdle()
            val actualBefore: WidgetSettingsUiState = expectMostRecentItem()
            assertFalse(actualBefore.eventsEnabled)
            assertTrue(actualBefore.calendars.isEmpty())
            viewModel.onCalendarPermissionResult(granted = true, canAskAgain = true)
            advanceUntilIdle()
            val actualAfter: WidgetSettingsUiState = expectMostRecentItem()
            assertTrue(actualAfter.eventsEnabled)
            verify(exactly = 1) { mockCalendarEventSource.refreshPermission() }
            assertEquals(inputCalendars, actualAfter.calendars)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps calendars hidden while permission is denied`() = runTest {
        val inputCalendars: List<CalendarInfo> = listOf(personalCalendar)
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(
            permission = inputPermission,
            calendars = MutableStateFlow(inputCalendars)
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryFixture(
                eventSettings = EventSettings(enabled = true)
            ).mockSettingsRepository,
            mockCalendarEventSource = mockCalendarEventSource
        )
        viewModel.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().calendars.isEmpty())
            inputPermission.value = true
            advanceUntilIdle()
            assertEquals(inputCalendars, expectMostRecentItem().calendars)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prunes exclusions for calendars that no longer exist`() = runTest {
        val inputCalendars: List<CalendarInfo> = listOf(personalCalendar)
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture(
            eventSettings = EventSettings(enabled = true, excludedCalendarIds = setOf("1", "2"))
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = inputSettings.mockSettingsRepository,
            mockCalendarEventSource = createGrantedCalendarEventSourceMock(calendars = inputCalendars)
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            val actualState: WidgetSettingsUiState = expectMostRecentItem()

            assertEquals(setOf("1"), actualState.excludedCalendarIds)
            assertEquals(setOf("1"), inputSettings.state.eventUpdates.last().excludedCalendarIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps exclusions while no calendars are loaded`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture(
            eventSettings = EventSettings(enabled = true, excludedCalendarIds = setOf("1"))
        )
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = inputSettings.mockSettingsRepository,
            mockCalendarEventSource = createGrantedCalendarEventSourceMock()
        )

        viewModel.uiState.test {
            advanceUntilIdle()
            val actualState: WidgetSettingsUiState = expectMostRecentItem()

            assertEquals(setOf("1"), actualState.excludedCalendarIds)
            coVerify(exactly = 0) { inputSettings.mockSettingsRepository.updateEventSettings(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requests calendar permission without enabling events`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryFixture().mockSettingsRepository
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockCalendarEventSource = createCalendarEventSourceMock()
        )

        viewModel.events.test {
            viewModel.setEventsEnabled(enabled = true)
            advanceUntilIdle()

            assertEquals(WidgetSettingsEvent.RequestCalendarPermission, awaitItem())
            coVerify(exactly = 0) { mockSettingsRepository.updateEventSettings(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add widget opens the Home picker`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()

        viewModel.events.test {
            viewModel.openAddWidget()
            assertEquals(WidgetSettingsEvent.StartAddWidget, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saves event options`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture()
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)

        viewModel.setEventLookaheadHours(24)
        viewModel.setEventMaxEvents(3)
        viewModel.setEventIncludeAllDay(false)
        viewModel.setExcludedCalendarIds(setOf("1", "w:2"))
        advanceUntilIdle()

        val actualSaved: EventSettings = inputSettings.state.eventUpdates.last()
        assertEquals(24, actualSaved.lookaheadHours)
        assertEquals(3, actualSaved.maxEvents)
        assertFalse(actualSaved.includeAllDay)
        assertEquals(setOf("1", "w:2"), actualSaved.excludedCalendarIds)
    }

    @Test
    fun `enabling the Media Player without access requests notification access`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture()
        val mockMediaSessionSource: IMediaSessionSource = createMediaSessionSourceMock(hasAccess = false)
        val viewModel: WidgetSettingsViewModel = createViewModel(
            mockSettingsRepository = inputSettings.mockSettingsRepository,
            mockMediaSessionSource = mockMediaSessionSource
        )
        viewModel.events.test {
            viewModel.setMediaPlayerEnabled(true)
            assertEquals(WidgetSettingsEvent.RequestNotificationAccess, awaitItem())
            assertTrue(inputSettings.state.mediaPlayerUpdates.last().enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshing Media access asks the source to recheck`() = runTest {
        val mockMediaSessionSource: IMediaSessionSource = createMediaSessionSourceMock(hasAccess = false)
        val viewModel: WidgetSettingsViewModel = createViewModel(mockMediaSessionSource = mockMediaSessionSource)
        viewModel.refreshMediaAccess()
        verify(exactly = 1) { mockMediaSessionSource.refreshAccess() }
    }

    @Test
    fun `sorts excluded Media Player apps first and filters the list`() {
        val inputApps: List<ExcludableApp> = listOf(
            ExcludableApp(
                packageName = "com.example.music",
                label = "Music",
                icon = Icon.ApplicationIcon(packageName = "com.example.music"),
                isExcluded = false
            ),
            ExcludableApp(
                packageName = "com.example.video",
                label = "Video",
                icon = Icon.ApplicationIcon(packageName = "com.example.video"),
                isExcluded = true
            ),
            ExcludableApp(
                packageName = "com.example.maps",
                label = "Maps",
                icon = Icon.ApplicationIcon(packageName = "com.example.maps"),
                isExcluded = false
            )
        )
        val actualSorted: List<ExcludableApp> = filterExcludableApps(apps = inputApps, query = "")
        assertEquals(listOf("Video", "Maps", "Music"), actualSorted.map { app -> app.label })
        assertTrue(actualSorted.first().isExcluded)
        val actualFiltered: List<ExcludableApp> = filterExcludableApps(apps = inputApps, query = "ma")
        assertEquals(listOf("Maps"), actualFiltered.map { app -> app.label })
    }

    @Test
    fun `opens and closes the exclusion list`() = runTest {
        val viewModel: WidgetSettingsViewModel = createViewModel()
        viewModel.mediaPlayerExclusions.test {
            assertNull(expectMostRecentItem())
            viewModel.openExcludedApps()
            val actualOpened: MediaPlayerExclusionsState = requireNotNull(expectMostRecentItem())
            assertTrue(actualOpened.isLoaded)
            assertTrue(actualOpened.apps.isEmpty())
            viewModel.closeExcludedApps()
            assertNull(expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists the Media Player options`() = runTest {
        val inputSettings: WidgetSettingsRepositoryFixture = createWidgetSettingsRepositoryFixture()
        val viewModel: WidgetSettingsViewModel = createViewModel(mockSettingsRepository = inputSettings.mockSettingsRepository)
        viewModel.setShowWhenPaused(false)
        viewModel.setShowArtwork(false)
        viewModel.setPlacement(MediaPlayerPlacement.TOP_OF_WIDGET_AREA)
        viewModel.setExcluded(packageName = "com.example.video", excluded = true)
        advanceUntilIdle()
        val expectedSettings: MediaPlayerSettings = MediaPlayerSettings(
            showWhenPaused = false,
            showArtwork = false,
            placement = MediaPlayerPlacement.TOP_OF_WIDGET_AREA,
            excludedPackages = setOf("com.example.video")
        )
        assertEquals(expectedSettings, inputSettings.state.mediaPlayerUpdates.last())
    }

    private fun createViewModel(
        mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryFixture().mockSettingsRepository,
        mockAreaRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(
            WidgetAreaRepositoryState(listOf(headerPlace))
        ),
        mockClockSource: IClockSource = createClockSourceMock(MutableStateFlow(reading())),
        mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(groups = emptyList()),
        mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(),
        mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock(),
        mockRemoveAppWidget: IRemoveAppWidget = createRemoveAppWidgetMock(),
        homeHeightState: HomeHeightState = HomeHeightState(),
        mockMediaSessionSource: IMediaSessionSource = createMediaSessionSourceMock()
    ): WidgetSettingsViewModel = WidgetSettingsViewModel(
        settingsRepository = mockSettingsRepository,
        areaRepository = mockAreaRepository,
        clockSource = mockClockSource,
        formatter = HeaderFormatter(createDatePatternSourceMock()),
        launchableRepository = mockLaunchableRepository,
        calendarEventSource = mockCalendarEventSource,
        appWidgetHost = mockAppWidgetHost,
        removeAppWidget = mockRemoveAppWidget,
        homeHeightState = homeHeightState,
        mediaSessionSource = mockMediaSessionSource,
        headerSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = mockCalendarEventSource
        ),
        tapTargetResolver = IHeaderTapTargetResolverImpl()
    )

    private fun reading(isSystem24Hour: Boolean = false): ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = isSystem24Hour
    )
}
