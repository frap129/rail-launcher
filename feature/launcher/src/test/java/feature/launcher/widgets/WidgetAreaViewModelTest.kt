package feature.launcher.widgets

import androidx.compose.foundation.lazy.LazyListState
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import core.data.calendar.ICalendarEventSource
import core.data.calendar.MAX_EVENT_LOOKAHEAD_HOURS
import core.data.calendar.UpcomingEvent
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.media.IMediaSessionSource
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
import core.data.testing.AppWidgetHostState
import core.data.testing.MainDispatcherRule
import core.data.testing.appWidgetPlace
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createBatterySourceMock
import core.data.testing.createClockSourceMock
import core.data.testing.createDatePatternSourceMock
import core.data.testing.createHeaderSettingsActions
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createRemoveAppWidgetMock
import core.data.testing.createWidgetLifecycleReconcilerMock
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.testing.headerPlace
import core.data.testing.stackPlace
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AddAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.HeaderRemoval
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetLifecycleReconciler
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.PlaceDraft
import core.data.widgets.PlacedWidget
import core.data.widgets.ReplaceHeaderUndo
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetSpacing
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.ui.layout.HomeHeightState
import core.ui.widgets.AppWidgetModel
import core.ui.widgets.BatteryModel
import core.ui.widgets.HeaderFormatter
import core.ui.widgets.HeaderLineModel
import core.ui.widgets.HeaderModel
import core.ui.widgets.HeaderTapTarget
import core.ui.widgets.IHeaderTapTargetResolverImpl
import feature.launcher.R
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
            val actualState: WidgetAreaState = expectMostRecentItem()
            assertTrue(actualState.hasWidgets)
            assertEquals(1, actualState.places.size)
            assertEquals(1L, actualState.places.single().id)
            val actualHeader: HeaderModel = actualState.header()
            assertEquals("2:05", actualHeader.time?.text)
            assertEquals("Tuesday, October 20", actualHeader.date?.text)
            assertEquals("2:05 PM", actualHeader.time?.spokenText)
            assertEquals("Tuesday, October 20, 2026", actualHeader.date?.spokenText)
            assertEquals(CoreUiR.string.open_alarms, actualHeader.time?.clickLabel)
            assertEquals(CoreUiR.string.open_calendar, actualHeader.date?.clickLabel)
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
        val twentyFourHour: WidgetAreaViewModel = createViewModel(mockClockSource = createClock(reading(isSystem24Hour = true)))
        twentyFourHour.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals("14:05", actualHeader.time?.text)
            assertEquals("14:05", actualHeader.time?.spokenText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits the Header when the style settings change`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.widgetAreaState.test {
            assertEquals(ClockStyle.CLASSIC, expectMostRecentItem().header().style)
            mockSettingsRepository.updateHeaderSettings { settings ->
                settings.copy(
                    clockStyle = ClockStyle.STACKED,
                    timeFormat = TimeFormat.H24,
                    alignment = HeaderAlignment.END,
                    size = HeaderSize.EXTRA_LARGE
                )
            }
            val actualHeader: HeaderModel = awaitItem().header()
            assertEquals(ClockStyle.STACKED, actualHeader.style)
            assertEquals(HeaderAlignment.END, actualHeader.alignment)
            assertEquals(HeaderSize.EXTRA_LARGE, actualHeader.size)
            assertEquals("14", actualHeader.time?.hourText)
            assertEquals("05", actualHeader.time?.minuteText)
            assertEquals("Tuesday, October 20", actualHeader.date?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reformats when the reading zone or locale changes`() = runTest {
        val inputClockReadings: MutableStateFlow<ClockReading> = MutableStateFlow(reading())
        val mockClockSource: IClockSource = createClockSourceMock(inputClockReadings)
        val viewModel: WidgetAreaViewModel = createViewModel(mockClockSource = mockClockSource)
        viewModel.widgetAreaState.test {
            assertEquals("2:05", expectMostRecentItem().header().time?.text)
            inputClockReadings.value = reading(at = "2026-10-20T21:05:00Z")
            assertEquals("9:05", awaitItem().header().time?.text)
            inputClockReadings.value = reading(at = "2026-10-20T14:05:00Z", zoneId = ZoneId.of("Asia/Tokyo"))
            assertEquals("11:05", awaitItem().header().time?.text)
            inputClockReadings.value = reading(locale = Locale.GERMAN)
            assertEquals("Dienstag, Oktober 20", awaitItem().header().date?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes alarms and calendar tap targets`() = runTest {
        val inputNow: Instant = Instant.parse("2026-10-20T14:05:00Z")
        val viewModel: WidgetAreaViewModel = createViewModel(mockClockSource = createClock(reading(at = inputNow.toString())))
        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.SystemAlarms, actualHeader.time?.tapTarget)
            assertEquals(HeaderTapTarget.SystemCalendar(inputNow.toEpochMilli()), actualHeader.date?.tapTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves a chosen Launchable for the time and Nothing for the date`() = runTest {
        val inputLaunchable: RailLaunchable = testLaunchable(name = "Maps", deeplink = "rail://maps")
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key),
                dateTapTarget = HeaderTapSetting.Nothing
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(inputLaunchable)))
            )
        )
        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable), actualHeader.time?.tapTarget)
            assertEquals(CoreUiR.string.open_launchable, actualHeader.time?.clickLabel)
            assertEquals("Maps", actualHeader.time?.clickLabelArg)
            assertEquals(HeaderTapTarget.None, actualHeader.date?.tapTarget)
            assertNull(actualHeader.date?.clickLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves Nothing for the time`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.None, actualHeader.time?.tapTarget)
            assertNull(actualHeader.time?.clickLabel)
            assertEquals(HeaderTapTarget.SystemCalendar(Instant.parse("2026-10-20T14:05:00Z").toEpochMilli()), actualHeader.date?.tapTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves a chosen Launchable for the date`() = runTest {
        val inputLaunchable: RailLaunchable = testLaunchable(name = "Maps", deeplink = "rail://maps")
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(dateTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockLaunchableRepository = createLaunchableRepositoryMock(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(inputLaunchable)))
            )
        )
        viewModel.widgetAreaState.test {
            val actualDate: HeaderLineModel? = expectMostRecentItem().header().date
            assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable), actualDate?.tapTarget)
            assertEquals(CoreUiR.string.open_launchable, actualDate?.clickLabel)
            assertEquals("Maps", actualDate?.clickLabelArg)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves stale Launchables to the system default before and after loading`() = runTest {
        val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputLaunchableState)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(
                timeTapTarget = HeaderTapSetting.Launchable(key = "uninstalled-clock"),
                dateTapTarget = HeaderTapSetting.Launchable(key = "uninstalled-calendar")
            )
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockLaunchableRepository = mockLaunchableRepository
        )
        viewModel.widgetAreaState.test {
            val actualUnloadedHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals(HeaderTapTarget.SystemAlarms, actualUnloadedHeader.time?.tapTarget)
            assertTrue(actualUnloadedHeader.date?.tapTarget is HeaderTapTarget.SystemCalendar)
            inputLaunchableState.value = createLaunchableState(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(testLaunchable())))
            )
            advanceUntilIdle()
            val actualLoadedHeader: HeaderModel = viewModel.widgetAreaState.value.header()
            assertEquals(HeaderTapTarget.SystemAlarms, actualLoadedHeader.time?.tapTarget)
            assertTrue(actualLoadedHeader.date?.tapTarget is HeaderTapTarget.SystemCalendar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resolves a chosen Launchable once it loads`() = runTest {
        val inputLaunchable: RailLaunchable = testLaunchable(name = "Maps", deeplink = "rail://maps")
        val inputLaunchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
            createLaunchableState(groups = emptyList(), isLoaded = false)
        )
        val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(inputLaunchableState)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(timeTapTarget = HeaderTapSetting.Launchable(key = inputLaunchable.key))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockLaunchableRepository = mockLaunchableRepository
        )
        viewModel.widgetAreaState.test {
            assertEquals(HeaderTapTarget.SystemAlarms, expectMostRecentItem().header().time?.tapTarget)
            inputLaunchableState.value = createLaunchableState(
                groups = listOf(LaunchableGroup.Builtin(items = listOf(inputLaunchable)))
            )
            assertEquals(HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable), awaitItem().header().time?.tapTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the time line when the clock is off`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(HeaderSettings(clockVisible = false))
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertNull(actualHeader.time)
            assertEquals("Tuesday, October 20", actualHeader.date?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps the date in Compact style when the clock is off`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(clockVisible = false, clockStyle = ClockStyle.COMPACT)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertNull(actualHeader.time)
            assertEquals("Tuesday, October 20", actualHeader.date?.text)
            assertEquals(ClockStyle.COMPACT, actualHeader.style)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the date line when the date is off`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(HeaderSettings(dateVisible = false))
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals("2:05", actualHeader.time?.text)
            assertNull(actualHeader.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists clock visibility and keeps the menu open`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            expectMostRecentItem()
            viewModel.setClockVisible(visible = false)
            advanceUntilIdle()
            coVerify(exactly = 1) { mockSettingsRepository.setClockVisible(visible = false) }
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            assertFalse((actualMenu.items.first() as WidgetMenuItem.Toggle).checked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disables hiding the clock when it is the last visible Header part`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository =
            createWidgetSettingsRepositoryMock(HeaderSettings(clockVisible = true, dateVisible = false))
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val actualClock: WidgetMenuItem.Toggle = actualMenu.items.first() as WidgetMenuItem.Toggle
            assertFalse(actualClock.enabled)
            assertEquals(CoreUiR.string.header_last_part_supporting_text, actualClock.supportingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the Header menu items in canonical order`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
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
                actualMenu.items.map { item -> item.id }
            )
            assertTrue((actualMenu.items[0] as WidgetMenuItem.Toggle).checked)
            assertTrue((actualMenu.items[0] as WidgetMenuItem.Toggle).enabled)
            assertTrue((actualMenu.items[1] as WidgetMenuItem.Toggle).checked)
            assertTrue((actualMenu.items[1] as WidgetMenuItem.Toggle).enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opens the Clock Style chooser with previews and applies a style`() = runTest {
        val inputHeaderSettings: MutableStateFlow<HeaderSettings> = MutableStateFlow(HeaderSettings())
        val mockSettingsRepository: IWidgetSettingsRepository =
            createWidgetSettingsRepositoryMock(headerSettings = inputHeaderSettings)
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            expectHeaderMenu()
            viewModel.openClockStyleChooser()
            val actualChooser: WidgetMenuState.ClockStyleChooser = expectState()
            assertEquals(ClockStyle.CLASSIC, actualChooser.selected)
            assertEquals(ClockStyle.entries, actualChooser.previews.map { preview -> preview.style })
            assertEquals("2:05", actualChooser.previews.first().time?.text)
            viewModel.selectClockStyle(ClockStyle.BOLD)
            advanceUntilIdle()
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            assertEquals(ClockStyle.BOLD, inputHeaderSettings.value.clockStyle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Widget settings in the empty area menu when the Header exists`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.openEmptyAreaMenu()
            val actualMenu: WidgetMenuState.EmptyAreaMenu = expectEmptyAreaMenu()
            assertEquals(
                listOf(
                    WidgetMenuAction.EditFavorites,
                    WidgetMenuAction.AddWidget,
                    WidgetMenuAction.MoveWidgetArea,
                    WidgetMenuAction.WidgetSettings
                ),
                actualMenu.items.map { item -> item.id }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Restore Header before Widget settings when the Header is missing`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = createRepository(emptyList()))
        viewModel.menuState.test {
            viewModel.openEmptyAreaMenu()
            val actualMenu: WidgetMenuState.EmptyAreaMenu = expectEmptyAreaMenu()
            assertEquals(
                listOf(
                    WidgetMenuAction.EditFavorites,
                    WidgetMenuAction.AddWidget,
                    WidgetMenuAction.RestoreHeader,
                    WidgetMenuAction.MoveWidgetArea,
                    WidgetMenuAction.WidgetSettings
                ),
                actualMenu.items.map { item -> item.id }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not open menus while Favorites are edited`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: WidgetAreaViewModel = createViewModel(inputHomeEditState = inputHomeEditState)
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
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace))
        coEvery { mockRepository.removeHeader() } returns HeaderRemoval(position = 0, placeId = 1L, pagePosition = 0)
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.events.test {
            viewModel.removeHeader()
            assertEquals(
                WidgetEvent.ShowUndo(
                    action = UndoAction.RemoveHeader(
                        removal = HeaderRemoval(position = 0, placeId = 1L, pagePosition = 0)
                    )
                ),
                awaitItem()
            )
        }
        coVerify(exactly = 1) { mockRepository.removeHeader() }
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)
    }

    @Test
    fun `does not emit Undo when the Header is missing`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = createRepository(emptyList()))
        viewModel.events.test {
            viewModel.removeHeader()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `undo restores the Header at its old position`() = runTest {
        val inputAppWidgetPlace: WidgetPlace = WidgetPlace(
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
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(inputAppWidgetPlace, headerPlace.copy(id = 2L))
        )
        coEvery { mockRepository.removeHeader() } returns HeaderRemoval(position = 1, placeId = 2L, pagePosition = 0)
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        var actualRemoval: UndoAction.RemoveHeader? = null
        viewModel.events.test {
            viewModel.removeHeader()
            val actualEvent: WidgetEvent.ShowUndo = awaitItem() as WidgetEvent.ShowUndo
            actualRemoval = actualEvent.action as UndoAction.RemoveHeader
        }
        viewModel.undo(action = actualRemoval!!)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockRepository.restoreHeader(position = 1, placeId = any(), pagePosition = any()) }
    }

    @Test
    fun `navigates to Widget settings and closes the menu`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()
        viewModel.events.test {
            viewModel.openWidgetSettings()
            assertEquals(WidgetEvent.NavigateToWidgetSettings, awaitItem())
        }
    }

    @Test
    fun `requests Edit Favorites from the empty area menu`() = runTest {
        val actualViewModel: WidgetAreaViewModel = createViewModel()
        actualViewModel.events.test {
            actualViewModel.onMenuAction(WidgetMenuAction.EditFavorites)
            assertEquals(WidgetEvent.EditFavorites, awaitItem())
        }
    }

    @Test
    fun `does not collect the battery while its visibility is Never`() = runTest {
        val inputBatteryStatuses: MutableSharedFlow<BatteryStatus> = MutableSharedFlow(replay = 1)
        inputBatteryStatuses.tryEmit(BatteryStatus(percent = 84, isCharging = false))
        val mockBatterySource: IBatterySource = createBatterySourceMock(inputBatteryStatuses)
        val viewModel: WidgetAreaViewModel = createViewModel(mockBatterySource = mockBatterySource)

        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertNull(actualHeader.battery)
            advanceUntilIdle()
            assertEquals(0, inputBatteryStatuses.subscriptionCount.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows and updates the battery when its visibility is Always`() = runTest {
        val inputBatteryStatuses: MutableSharedFlow<BatteryStatus> = MutableSharedFlow(replay = 1)
        inputBatteryStatuses.tryEmit(BatteryStatus(percent = 84, isCharging = false))
        val mockBatterySource: IBatterySource = createBatterySourceMock(inputBatteryStatuses)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockBatterySource = mockBatterySource
        )

        viewModel.widgetAreaState.test {
            val actualHeader: HeaderModel = expectMostRecentItem().header()
            assertEquals("84%", actualHeader.battery?.text)
            assertEquals(84, actualHeader.battery?.percent)
            assertEquals(CoreUiR.string.battery_content_description, actualHeader.battery?.contentDescriptionRes)
            assertTrue(inputBatteryStatuses.subscriptionCount.value > 0)

            inputBatteryStatuses.emit(BatteryStatus(percent = 85, isCharging = false))
            assertEquals("85%", awaitItem().header().battery?.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the battery only while charging or low`() = runTest {
        val inputBatteryStatuses: MutableSharedFlow<BatteryStatus> = MutableSharedFlow(replay = 1)
        inputBatteryStatuses.tryEmit(BatteryStatus(percent = 84, isCharging = false))
        val mockBatterySource: IBatterySource = createBatterySourceMock(inputBatteryStatuses)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(batteryVisibility = BatteryVisibility.CHARGING_OR_LOW)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockBatterySource = mockBatterySource
        )

        viewModel.widgetAreaState.test {
            assertNull(expectMostRecentItem().header().battery)

            inputBatteryStatuses.emit(BatteryStatus(percent = 20, isCharging = false))
            val actualLow: BatteryModel? = awaitItem().header().battery
            assertEquals("20%", actualLow?.text)
            assertTrue(actualLow?.isLow == true)

            inputBatteryStatuses.emit(BatteryStatus(percent = 21, isCharging = false))
            assertNull(awaitItem().header().battery)

            inputBatteryStatuses.emit(BatteryStatus(percent = 50, isCharging = true))
            val actualCharging: BatteryModel? = awaitItem().header().battery
            assertEquals("50%", actualCharging?.text)
            assertTrue(actualCharging?.isCharging == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the clock and date`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)

        viewModel.setClockVisible(visible = false)
        viewModel.setDateVisible(visible = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSettingsRepository.setClockVisible(visible = false) }
        coVerify(exactly = 1) { mockSettingsRepository.setDateVisible(visible = false) }
    }

    @Test
    fun `selects the battery visibility`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)

        viewModel.selectBatteryVisibility(BatteryVisibility.NEVER)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSettingsRepository.setBatteryVisibility(visibility = BatteryVisibility.NEVER) }
    }

    @Test
    fun `disables conditional battery options while the battery is the last visible part`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            HeaderSettings(clockVisible = false, dateVisible = false, batteryVisibility = BatteryVisibility.ALWAYS)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)

        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val actualBattery: WidgetMenuItem.Choice = actualMenu.items[2] as WidgetMenuItem.Choice
            val actualEnabledByVisibility: Map<String, Boolean> = actualBattery.options.associate { option ->
                option.key.value to option.enabled
            }

            assertTrue(actualEnabledByVisibility.getValue(BatteryVisibility.ALWAYS.name))
            assertFalse(actualEnabledByVisibility.getValue(BatteryVisibility.NEVER.name))
            assertFalse(actualEnabledByVisibility.getValue(BatteryVisibility.CHARGING_OR_LOW.name))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expands the Battery menu choice and saves a visibility`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)

        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val actualBattery: WidgetMenuItem.Choice = actualMenu.items[2] as WidgetMenuItem.Choice
            assertEquals(WidgetMenuAction.Battery, actualBattery.id)
            assertEquals(CoreUiR.string.battery_never, actualBattery.valueLabel)
            assertEquals(
                listOf("NEVER", "ALWAYS", "CHARGING_OR_LOW"),
                actualBattery.options.map { option -> option.key.value }
            )
            assertFalse(actualBattery.expanded)

            viewModel.toggleChoice(WidgetMenuAction.Battery)
            advanceUntilIdle()
            val actualExpanded: WidgetMenuItem.Choice = expectHeaderMenu().items[2] as WidgetMenuItem.Choice
            assertTrue(actualExpanded.expanded)

            viewModel.selectBatteryVisibility(BatteryVisibility.CHARGING_OR_LOW)
            advanceUntilIdle()
            coVerify(exactly = 1) {
                mockSettingsRepository.setBatteryVisibility(visibility = BatteryVisibility.CHARGING_OR_LOW)
            }
            val actualCollapsed: WidgetMenuItem.Choice = expectHeaderMenu().items[2] as WidgetMenuItem.Choice
            assertFalse(actualCollapsed.expanded)
            assertEquals(BatteryVisibility.CHARGING_OR_LOW.name, actualCollapsed.selectedKey.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not collect calendar events while the feature is off`() = runTest {
        val mockCalendarEventSource: ICalendarEventSource = createCalendar()
        val viewModel: WidgetAreaViewModel = createViewModel(mockCalendarEventSource = mockCalendarEventSource)

        viewModel.widgetAreaState.test {
            expectMostRecentItem()
            advanceUntilIdle()

            verify(exactly = 0) { mockCalendarEventSource.events(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not collect calendar events without permission`() = runTest {
        val mockCalendarEventSource: ICalendarEventSource = createCalendar(hasPermission = false)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            eventSettings = EventSettings(enabled = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockCalendarEventSource = mockCalendarEventSource
        )

        viewModel.widgetAreaState.test {
            expectMostRecentItem()
            advanceUntilIdle()

            verify(exactly = 0) { mockCalendarEventSource.events(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requests calendar permission when enabling events without it`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockCalendarEventSource = createCalendar(hasPermission = false)
        )

        viewModel.events.test {
            viewModel.setUpcomingEventsEnabled(enabled = true)
            advanceUntilIdle()

            assertEquals(WidgetEvent.RequestCalendarPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps events off and reports a denial`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockCalendarEventSource = createCalendar(hasPermission = false)
        )

        viewModel.events.test {
            viewModel.setUpcomingEventsEnabled(enabled = true)
            advanceUntilIdle()
            assertEquals(WidgetEvent.RequestCalendarPermission, awaitItem())

            viewModel.onCalendarPermissionResult(granted = false, canAskAgain = true)
            advanceUntilIdle()

            assertEquals(WidgetEvent.ShowCalendarPermissionDenied(permanentlyDenied = false), awaitItem())
            coVerify(exactly = 0) { mockSettingsRepository.setEventsEnabled(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persists events as enabled after a permission grant`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockCalendarEventSource = createCalendar(hasPermission = false)
        )

        viewModel.onCalendarPermissionResult(granted = true, canAskAgain = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSettingsRepository.setEventsEnabled(enabled = true) }
    }

    @Test
    fun `requests calendar events with the effective lookahead`() = runTest {
        val inputLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
        val mockCalendarEventSource: ICalendarEventSource = createCalendar(queriedLookaheadHours = inputLookaheadHours)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            eventSettings = EventSettings(enabled = true, lookaheadHours = 99)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockCalendarEventSource = mockCalendarEventSource
        )

        viewModel.widgetAreaState.test {
            expectMostRecentItem()
            advanceUntilIdle()

            assertEquals(listOf(MAX_EVENT_LOOKAHEAD_HOURS), inputLookaheadHours.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops an event on the first clock tick after it ends`() = runTest {
        val inputEvent: UpcomingEvent = event(title = "Standup")
        val inputClockReadings: MutableStateFlow<ClockReading> = MutableStateFlow(reading(at = "2026-10-20T14:05:00Z"))
        val mockClockSource: IClockSource = createClockSourceMock(inputClockReadings)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            headerSettings = HeaderSettings(timeFormat = TimeFormat.H24),
            eventSettings = EventSettings(enabled = true, lookaheadHours = 12, maxEvents = 1)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockClockSource = mockClockSource,
            mockSettingsRepository = mockSettingsRepository,
            mockCalendarEventSource = createCalendar(events = MutableStateFlow(listOf(inputEvent)))
        )

        viewModel.widgetAreaState.test {
            assertEquals(listOf("Standup · 14:06"), expectMostRecentItem().header().events.map { model -> model.text })

            inputClockReadings.value = reading(at = "2026-10-20T14:08:00Z")
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
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()

            assertEquals(WidgetMenuAction.UpcomingEvents, actualMenu.items[3].id)
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
        val inputStack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = createRepository(listOf(inputStack)))

        viewModel.menuState.test {
            expectMostRecentItem()
            viewModel.openHeaderMenu(widgetId = 1L)
            advanceUntilIdle()
            val actualMenu: WidgetMenuState.HeaderMenu = expectMostRecentItem() as WidgetMenuState.HeaderMenu
            assertTrue(actualMenu.items.any { item -> item.id == WidgetMenuAction.RemoveFromStack })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `page settle persists the current page after the debounce`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)

        viewModel.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(299)
        coVerify(exactly = 0) { mockRepository.setCurrentPage(any(), any()) }
        advanceTimeBy(1)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.setCurrentPage(placeId = 1L, page = 1) }
    }

    @Test
    fun `merging a place over the stack cap is rejected`() = runTest {
        val inputStack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockRepository = createRepository(listOf(inputStack, appWidgetPlace(placeId = 3L, widgetId = 3L))),
            mockSettingsRepository = createWidgetSettingsRepositoryMock(widgetAreaSettings = WidgetAreaSettings(maxStackPages = 2))
        )

        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.mergePlace(sourcePlaceId = 3L, targetPlaceId = 1L)
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `splitting a stack page creates a new draft place`() = runTest {
        val inputStack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = createRepository(listOf(inputStack)))

        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.splitPage(placeId = 1L, widgetId = 2L, toIndex = 1)

        val actualDraft: WidgetEditDraft = viewModel.editState.value!!
        assertEquals(2, actualDraft.places.size)
        assertNull(actualDraft.places[1].placeId)
        assertEquals(listOf(2L), actualDraft.places[1].widgetIds)
    }

    @Test
    fun `toggling a stack expands it in the draft`() = runTest {
        val inputStack: WidgetPlace = WidgetPlace(
            id = 1L,
            heightDp = null,
            currentPage = 0,
            widgets = listOf(PlacedWidget.Header(id = 1L), appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = createRepository(listOf(inputStack)))

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
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                widgetAreaSettings = WidgetAreaSettings(heightLimitEnabled = false)
            )
        )
        collectWidgetAreaState(uncappedViewModel)
        assertEquals(WidgetAreaLayout.Uncapped, uncappedViewModel.widgetAreaState.value.layout)
        val halfViewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                widgetAreaSettings = WidgetAreaSettings(heightLimit = WidgetAreaHeightLimit.HALF)
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
        val actualPrevious: LazyListState = viewModel.widgetAreaState.value.listState

        viewModel.onHomeIntent()
        advanceUntilIdle()

        assertNotSame(actualPrevious, viewModel.widgetAreaState.value.listState)
    }

    @Test
    fun `toggling the height limit resets the Widget Area list state`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        collectWidgetAreaState(viewModel)
        val actualPrevious: LazyListState = viewModel.widgetAreaState.value.listState

        mockSettingsRepository.updateWidgetAreaSettings { settings -> settings.copy(heightLimitEnabled = false) }
        advanceUntilIdle()

        val actualChanged: WidgetAreaState = viewModel.widgetAreaState.value
        assertEquals(WidgetAreaLayout.Uncapped, actualChanged.layout)
        assertNotSame(actualPrevious, actualChanged.listState)
    }

    @Test
    fun `changing the cap fraction keeps the Widget Area list state`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        collectWidgetAreaState(viewModel)
        val actualPrevious: LazyListState = viewModel.widgetAreaState.value.listState

        mockSettingsRepository.updateWidgetAreaSettings { settings -> settings.copy(heightLimit = WidgetAreaHeightLimit.HALF) }
        advanceUntilIdle()

        val actualChanged: WidgetAreaState = viewModel.widgetAreaState.value
        assertEquals(WidgetAreaLayout.Capped(fraction = 0.5f), actualChanged.layout)
        assertSame(actualPrevious, actualChanged.listState)
    }

    @Test
    fun `starting Move Widget Area is ignored outside the None mode`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState()
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: WidgetAreaViewModel = createViewModel(inputHomeEditState = inputHomeEditState)
        collectMoveState(viewModel)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        assertNull(viewModel.moveState.value)
        assertEquals(HomeEditMode.Favorites, inputHomeEditState.mode.value)
    }

    @Test
    fun `Move Widget Area starts at the persisted offset and caps at half Home`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            widgetAreaSettings = WidgetAreaSettings(topOffsetDp = 24)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        val actualState: MoveWidgetAreaState = viewModel.moveState.value!!
        assertEquals(24, actualState.draftOffsetDp)
        assertEquals(400, actualState.maxOffsetDp)
        assertTrue(actualState.canMoveUp)
        assertTrue(actualState.canMoveDown)
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
        val inputAreaSettings: MutableStateFlow<WidgetAreaSettings> = MutableStateFlow(WidgetAreaSettings.Default)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            headerSettings = MutableStateFlow(HeaderSettings()),
            widgetAreaSettings = inputAreaSettings
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel =
            createViewModel(mockSettingsRepository = mockSettingsRepository, inputHomeEditState = inputHomeEditState)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)
        viewModel.startMoveWidgetArea()
        advanceUntilIdle()

        viewModel.setDraftOffset(16)
        viewModel.finishMoveWidgetArea()
        advanceUntilIdle()

        assertNull(viewModel.moveState.value)
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
        assertEquals(16, inputAreaSettings.value.topOffsetDp)
    }

    @Test
    fun `Back and Home intent save the Move Widget Area offset`() = runTest {
        val inputAreaSettings: MutableStateFlow<WidgetAreaSettings> = MutableStateFlow(WidgetAreaSettings.Default)
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            headerSettings = MutableStateFlow(HeaderSettings()),
            widgetAreaSettings = inputAreaSettings
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        collectMoveState(viewModel)
        viewModel.setHomeHeight(800)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()
        viewModel.setDraftOffset(8)
        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        assertEquals(8, inputAreaSettings.value.topOffsetDp)

        viewModel.startMoveWidgetArea()
        advanceUntilIdle()
        viewModel.setDraftOffset(16)
        assertTrue(viewModel.onHomeIntent())
        advanceUntilIdle()
        assertEquals(16, inputAreaSettings.value.topOffsetDp)
    }

    @Test
    fun `effective offset uses the draft while moving and the persisted value after`() = runTest {
        val inputAreaSettings: MutableStateFlow<WidgetAreaSettings> = MutableStateFlow(WidgetAreaSettings(topOffsetDp = 8))
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            headerSettings = MutableStateFlow(HeaderSettings()),
            widgetAreaSettings = inputAreaSettings
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
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
        assertEquals(24, inputAreaSettings.value.topOffsetDp)
    }

    @Test
    fun `clamps a persisted offset at render time without rewriting it`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            widgetAreaSettings = WidgetAreaSettings(topOffsetDp = 400)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        collectWidgetAreaState(viewModel)
        collectMoveState(viewModel)

        viewModel.setHomeHeight(100)
        advanceUntilIdle()

        assertEquals(48, viewModel.widgetAreaState.value.topOffsetDp)
        assertEquals(400, mockSettingsRepository.widgetAreaSettings.first().topOffsetDp)
    }

    @Test
    fun `exposes widget spacing from settings and applies changes live`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            widgetAreaSettings = WidgetAreaSettings(widgetSpacing = WidgetSpacing.LARGE)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockSettingsRepository = mockSettingsRepository)
        collectWidgetAreaState(viewModel)

        assertEquals(24, viewModel.widgetAreaState.value.widgetSpacingDp)

        mockSettingsRepository.updateWidgetAreaSettings { settings -> settings.copy(widgetSpacing = WidgetSpacing.EXTRA_LARGE) }
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
        val inputFirst: MediaSessionSnapshot = mediaSession(key = "first", title = "First")
        val inputSecond: MediaSessionSnapshot = mediaSession(key = "second", title = "Second")
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            mediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val mockMediaSessionSource: IMediaSessionSource = createMediaSession(initialSessions = listOf(inputFirst, inputSecond))
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockMediaSessionSource = mockMediaSessionSource
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = inputFirst), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the Media Player when disabled or without notification access`() = runTest {
        val inputSession: MediaSessionSnapshot = mediaSession()
        val disabled: WidgetAreaViewModel = createViewModel(
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputSession))
        )
        disabled.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        val mockEnabledSettings: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            mediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val noAccess: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockEnabledSettings,
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputSession), hasAccess = false)
        )
        noAccess.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the Media Player when no session is active`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                mediaPlayerSettings = MediaPlayerSettings(enabled = true)
            ),
            mockMediaSessionSource = createMediaSession()
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marks the Header Media Player toggle as needing notification access`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                mediaPlayerSettings = MediaPlayerSettings(enabled = true)
            ),
            mockMediaSessionSource = createMediaSession(hasAccess = false)
        )
        viewModel.menuState.test {
            viewModel.openHeaderMenu()
            val actualMenu: WidgetMenuState.HeaderMenu = expectHeaderMenu()
            val actualToggle: WidgetMenuItem.Toggle =
                actualMenu.items.single { item -> item.id == WidgetMenuAction.MediaPlayer } as WidgetMenuItem.Toggle
            assertEquals(R.string.needs_notification_access, actualToggle.supportingText)
            assertTrue(actualToggle.rowOpensAction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides the Media Player while a Home edit mode is active`() = runTest {
        val inputSession: MediaSessionSnapshot = mediaSession()
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            mediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            inputHomeEditState = inputHomeEditState,
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputSession))
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = inputSession), expectMostRecentItem())
            inputHomeEditState.tryEnter(HomeEditMode.MovingWidgetArea)
            assertEquals(MediaPlayerUiState.Hidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opens the Media Player menu and forwards transport commands`() = runTest {
        val inputSession: MediaSessionSnapshot = mediaSession(
            key = "first",
            actions = setOf(MediaCommand.Play, MediaCommand.Pause, MediaCommand.Next)
        )
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            mediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val mockMediaSessionSource: IMediaSessionSource = createMediaSession(initialSessions = listOf(inputSession))
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockMediaSessionSource = mockMediaSessionSource
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = inputSession), expectMostRecentItem())
            viewModel.menuState.test {
                viewModel.openMediaPlayerMenu()
                val actualMenu: WidgetMenuState.MediaPlayerMenu = expectMostRecentItem() as WidgetMenuState.MediaPlayerMenu
                assertEquals(inputSession.appLabel, actualMenu.label)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onMediaCommand(MediaCommand.Pause)
            viewModel.onMediaCommand(MediaCommand.Next)
            viewModel.openMediaPlayer()
            verifyOrder {
                mockMediaSessionSource.execute(inputSession.key, MediaCommand.Pause)
                mockMediaSessionSource.execute(inputSession.key, MediaCommand.Next)
            }
            verify(exactly = 1) { mockMediaSessionSource.open(inputSession.key) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enabling the Media Player without notification access requests it`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockMediaSessionSource = createMediaSession(hasAccess = false)
        )
        viewModel.events.test {
            viewModel.setMediaPlayerEnabled(enabled = true)
            assertEquals(WidgetEvent.RequestNotificationAccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { mockSettingsRepository.setMediaPlayerEnabled(enabled = true) }
    }

    @Test
    fun `exposes the Media Player placement and artwork options`() = runTest {
        val inputSession: MediaSessionSnapshot = mediaSession()
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                mediaPlayerSettings = MediaPlayerSettings(
                    enabled = true,
                    placement = MediaPlayerPlacement.TOP_OF_WIDGET_AREA,
                    showArtwork = false
                )
            ),
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputSession))
        )
        viewModel.mediaPlayerState.test {
            assertEquals(
                MediaPlayerUiState.Visible(
                    session = inputSession,
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
        val inputFirst: MediaSessionSnapshot =
            mediaSession(key = "first", title = "First", packageName = "com.example.first", appLabel = "First")
        val inputSecond: MediaSessionSnapshot =
            mediaSession(key = "second", title = "Second", packageName = "com.example.second", appLabel = "Second")
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            mediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputFirst, inputSecond))
        )
        backgroundScope.launch { viewModel.mediaPlayerState.collect {} }
        advanceUntilIdle()
        assertEquals(MediaPlayerUiState.Visible(session = inputFirst), viewModel.mediaPlayerState.value)
        viewModel.events.test {
            viewModel.excludeMediaApp()
            assertEquals(
                WidgetEvent.ShowUndo(
                    action = UndoAction.IncludeMediaApp(packageName = inputFirst.packageName, appLabel = inputFirst.appLabel)
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) {
            mockSettingsRepository.setMediaPlayerPackageExcluded(packageName = inputFirst.packageName, excluded = true)
        }
        advanceUntilIdle()
        assertEquals(MediaPlayerUiState.Visible(session = inputSecond), viewModel.mediaPlayerState.value)
    }

    @Test
    fun `undoing an exclusion restores the excluded app`() = runTest {
        val inputFirst: MediaSessionSnapshot =
            mediaSession(key = "first", title = "First", packageName = "com.example.first", appLabel = "First")
        val inputSecond: MediaSessionSnapshot =
            mediaSession(key = "second", title = "Second", packageName = "com.example.second", appLabel = "Second")
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            mediaPlayerSettings = MediaPlayerSettings(enabled = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = mockSettingsRepository,
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputFirst, inputSecond))
        )
        viewModel.mediaPlayerState.test {
            assertEquals(MediaPlayerUiState.Visible(session = inputFirst), expectMostRecentItem())
            viewModel.excludeMediaApp()
            advanceUntilIdle()
            coVerify(exactly = 1) {
                mockSettingsRepository.setMediaPlayerPackageExcluded(packageName = inputFirst.packageName, excluded = true)
            }
            advanceUntilIdle()
            assertEquals(MediaPlayerUiState.Visible(session = inputSecond), expectMostRecentItem())
            viewModel.undo(action = UndoAction.IncludeMediaApp(packageName = inputFirst.packageName))
            advanceUntilIdle()
            coVerify(exactly = 1) {
                mockSettingsRepository.setMediaPlayerPackageExcluded(packageName = inputFirst.packageName, excluded = false)
            }
            assertEquals(MediaPlayerUiState.Visible(session = inputFirst), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Media Player menu offers excluding the shown app`() = runTest {
        val inputSession: MediaSessionSnapshot = mediaSession(appLabel = "Video")
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockSettingsRepository = createWidgetSettingsRepositoryMock(
                mediaPlayerSettings = MediaPlayerSettings(enabled = true)
            ),
            mockMediaSessionSource = createMediaSession(initialSessions = listOf(inputSession))
        )
        viewModel.menuState.test {
            viewModel.openMediaPlayerMenu()
            val actualMenu: WidgetMenuState.MediaPlayerMenu = expectMostRecentItem() as WidgetMenuState.MediaPlayerMenu
            val actualExclude: WidgetMenuItem.Action = actualMenu.items[1] as WidgetMenuItem.Action
            assertEquals(WidgetMenuAction.ExcludeMediaApp, actualExclude.id)
            assertEquals("Video", actualExclude.labelArg)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Add widget from an App Widget menu starts below that widget`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)

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

            assertEquals(WidgetEvent.ReturnToSettings, awaitItem())
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
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace))
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo()),
            bindResults = listOf(false)
        )
        val actualViewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        advanceUntilIdle()
        actualViewModel.startAdd(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        actualViewModel.pickProvider(provider = appWidgetProviderInfo())
        advanceUntilIdle()

        actualViewModel.onBindResult(granted = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.addAppWidget(any()) }
        assertEquals(AddWidgetFlowState.Idle, actualViewModel.addFlowState.value)
    }

    @Test
    fun `onConfigureResult failure reports a message and deletes the id`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace))
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo(needsConfiguration = true)),
            bindResults = listOf(true)
        )
        val actualViewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        advanceUntilIdle()
        actualViewModel.events.test {
            actualViewModel.startAdd(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
            actualViewModel.pickProvider(provider = appWidgetProviderInfo(needsConfiguration = true))
            advanceUntilIdle()
            assertEquals(WidgetEvent.LaunchConfigure(AppWidgetId(1)), awaitItem())

            actualViewModel.onConfigureResult(ok = false)
            advanceUntilIdle()

            assertEquals(WidgetEvent.ShowMessage(CoreUiR.string.couldnt_add_widget), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { mockHost.deleteId(AppWidgetId(1)) }
        coVerify(exactly = 0) { mockRepository.addAppWidget(any()) }
    }

    @Test
    fun `adding into a stack reports the grown page size`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace))
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo(isVerticallyResizable = true)),
            bindResults = listOf(true)
        )
        val inputReport: WidgetSizeReport = WidgetSizeReport(
            appWidgetIds = listOf(AppWidgetId(1)),
            widthDp = 240,
            heightDp = 64
        )
        val mockResizeController: IWidgetResizeController = createResizeControllerMock(stackGrownReport = inputReport)
        val actualViewModel: WidgetAreaViewModel = createViewModel(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockHost,
            mockResizeController = mockResizeController
        )
        actualViewModel.openHeaderMenu()
        actualViewModel.startAddFromMenu()
        advanceUntilIdle()
        actualViewModel.choosePlacement(placement = AddWidgetPlacement.IntoStack)

        actualViewModel.events.test {
            actualViewModel.pickProvider(provider = appWidgetProviderInfo(isVerticallyResizable = true))
            advanceUntilIdle()

            assertEquals(
                WidgetEvent.ReportWidgetSize(
                    appWidgetIds = listOf(AppWidgetId(1)),
                    widthDp = 240,
                    heightDp = 64
                ),
                awaitItem()
            )
            coVerify(exactly = 1) { mockResizeController.onStackGrown(placeId = 1L, headerHeightDp = any(), ownerKey = any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun resizeState(): ResizeState = ResizeState(
        placeId = 2L,
        bounds = ResizeBounds(minDp = 48, maxDp = 240),
        defaultHeightDp = 48,
        draftHeightDp = 112
    )

    private fun createResizeControllerMock(
        resizeState: MutableStateFlow<ResizeState?> = MutableStateFlow(null),
        startResult: Boolean = true,
        finish: ResizeFinish? = null,
        report: WidgetSizeReport? = null,
        stackGrownReport: WidgetSizeReport? = null
    ): IWidgetResizeController {
        val homeHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null)
        return mockk {
            every { this@mockk.resizeState } returns resizeState
            every { this@mockk.homeHeightDp } returns homeHeightDp
            every { setHomeHeight(any()) } answers { homeHeightDp.value = firstArg() }
            every { setWidgetAreaWidth(any()) } just Runs
            coEvery { startResize(any(), any(), any()) } returns startResult
            coEvery { startResizeForWidget(any(), any(), any()) } returns startResult
            every { dragResize(any()) } just Runs
            every { resetResize() } just Runs
            every { finishResize() } answers {
                if (resizeState.value == null) {
                    null
                } else {
                    resizeState.value = null
                    finish
                }
            }
            coEvery { persistResize(any()) } returns report
            every { cancelResize() } answers { resizeState.value = null }
            every { onPlacesChanged(any(), any()) } just Runs
            coEvery { onStackGrown(any(), any(), any()) } returns stackGrownReport
            coEvery { clampStackHeight(any(), any(), any()) } just Runs
        }
    }

    private fun createViewModel(
        mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace)),
        mockClockSource: IClockSource = createClock(reading()),
        mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(),
        inputHomeEditState: HomeEditState = HomeEditState(),
        mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(groups = emptyList()),
        mockCalendarEventSource: ICalendarEventSource = createCalendar(),
        mockBatterySource: IBatterySource = createBattery(BatteryStatus(percent = 84, isCharging = false)),
        mockAppWidgetHost: IAppWidgetHostSource = createHost(),
        mockWidgetLifecycleReconciler: IWidgetLifecycleReconciler = createWidgetLifecycleReconcilerMock(),
        homeHeightState: HomeHeightState = HomeHeightState(),
        mockMediaSessionSource: IMediaSessionSource = createMediaSession(),
        mockResizeController: IWidgetResizeController = createResizeControllerMock(),
        mockRemoveAppWidget: IRemoveAppWidget = createRemoveAppWidgetMock()
    ): WidgetAreaViewModel = WidgetAreaViewModel(
        repository = mockRepository,
        clockSource = mockClockSource,
        formatter = HeaderFormatter(createDatePatternSourceMock()),
        settingsRepository = mockSettingsRepository,
        homeEditState = inputHomeEditState,
        launchableRepository = mockLaunchableRepository,
        calendarEventSource = mockCalendarEventSource,
        batterySource = mockBatterySource,
        appWidgetHost = mockAppWidgetHost,
        removeAppWidget = mockRemoveAppWidget,
        addFlowController = createAddController(
            mockRepository = mockRepository,
            mockAppWidgetHost = mockAppWidgetHost,
            mockSettingsRepository = mockSettingsRepository,
            homeEditState = inputHomeEditState
        ),
        widgetLifecycleReconciler = mockWidgetLifecycleReconciler,
        homeHeightState = homeHeightState,
        mediaSessionSource = mockMediaSessionSource,
        headerSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = mockCalendarEventSource
        ),
        tapTargetResolver = IHeaderTapTargetResolverImpl(),
        resizeController = mockResizeController
    )

    @Test
    fun `remove widget reports the freed system id and host view`() = runTest {
        val mockRemoveAppWidget: IRemoveAppWidget = createRemoveAppWidgetMock(result = AppWidgetId(9))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRemoveAppWidget = mockRemoveAppWidget)
        viewModel.events.test {
            viewModel.removeWidget(widgetId = 2L)
            assertEquals(WidgetEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            assertEquals(WidgetEvent.ShowMessage(CoreUiR.string.widget_removed), awaitItem())
            coVerify(exactly = 1) { mockRemoveAppWidget.invoke(2L) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `menus and add flows are ignored while editing`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val viewModel: WidgetAreaViewModel = createViewModel(inputHomeEditState = inputHomeEditState)
        viewModel.openHeaderMenu()
        viewModel.openEmptyAreaMenu()
        viewModel.openAppWidgetMenu(widgetId = 2L)
        assertFalse(viewModel.startAdd(AddWidgetOrigin(InsertPosition.End)))
        assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)
    }

    @Test
    fun `offers Reconfigure only for an available reconfigurable provider`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isReconfigurable = true))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(reconfigure = true), actualMenu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Reconfigure while the widget is unavailable`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(
                headerPlace,
                appWidgetPlace(placeId = 2L, widgetId = 2L, availability = WidgetAvailability.ProfilePaused)
            )
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isReconfigurable = true))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), actualMenu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Reconfigure when the provider has no configure activity`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(isReconfigurable = true, hasConfigureActivity = false)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), actualMenu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reconfigure launches the provider and closes the menu`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isReconfigurable = true))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(WidgetMenuAction.Reconfigure, actualMenu.items.first().id)
            viewModel.onMenuAction(WidgetMenuAction.Reconfigure)
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.events.test {
            assertEquals(WidgetEvent.LaunchReconfigure(AppWidgetId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 0) { mockHost.deleteId(any()) }
    }

    @Test
    fun `a failed reconfigure launch reports a message and keeps the widget`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.events.test {
            viewModel.onReconfigureResult(ok = false)
            assertEquals(WidgetEvent.ShowMessage(CoreUiR.string.couldnt_open_widget_settings), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, mockRepository.places.first().size)
        coVerify(exactly = 0) { mockRepository.addAppWidget(any()) }
    }

    @Test
    fun `a successful reconfigure keeps the widget without a message`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.events.test {
            viewModel.onReconfigureResult(ok = true)
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, mockRepository.places.first().size)
    }

    @Test
    fun `closes the App Widget menu when the widget disappears`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
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
        val mockRepository: IWidgetAreaRepository = createRepository(
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
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.events.test {
            viewModel.unpauseWorkProfile(widgetId = 2L)
            assertEquals(WidgetEvent.RequestQuietModeOff(profileSerial = 12L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops cached host views when reconciliation removes a widget`() = runTest {
        val inputRemovedIds: MutableSharedFlow<AppWidgetId> = MutableSharedFlow(extraBufferCapacity = 1)
        val mockReconciler: IWidgetLifecycleReconciler = createWidgetLifecycleReconcilerMock(removedIds = inputRemovedIds)
        val viewModel: WidgetAreaViewModel = createViewModel(mockWidgetLifecycleReconciler = mockReconciler)
        advanceUntilIdle()
        viewModel.events.test {
            inputRemovedIds.tryEmit(AppWidgetId(9))
            assertEquals(WidgetEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `App Widget models carry availability and the stored default height`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
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
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.widgetAreaState.test {
            val actualModel: AppWidgetModel = expectMostRecentItem().places.last().widgets.single() as AppWidgetModel
            assertEquals(WidgetAvailability.ProfilePaused, actualModel.availability)
            assertEquals(72, actualModel.minHeightDp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offers Resize first for an available vertically resizable widget`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(resize = true), actualMenu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Resize while the widget is unavailable`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(
                headerPlace,
                appWidgetPlace(placeId = 2L, widgetId = 2L, availability = WidgetAvailability.ProfilePaused)
            )
        )
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), actualMenu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Resize when the provider is not vertically resizable`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost(appWidgetProviderInfo(isVerticallyResizable = false))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), actualMenu.items.map { item -> item.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offers Resize without the provider list having been collected`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, appWidgetId = 9))
        )
        val inputHostState: AppWidgetHostState = AppWidgetHostState()
        inputHostState.providerInfoById[AppWidgetId(9)] = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 240)
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock(inputHostState)
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertTrue(actualMenu.items.map { item -> item.id }.contains(WidgetMenuAction.Resize))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startResize delegates to the controller and closes the menu`() = runTest {
        val mockResizeController: IWidgetResizeController = createResizeControllerMock(startResult = true)
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockRepository = createRepository(listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))),
            mockResizeController = mockResizeController
        )
        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            expectState<WidgetMenuState.AppWidgetMenu>()
            viewModel.startResize(placeId = 2L)
            advanceUntilIdle()
            coVerify(exactly = 1) { mockResizeController.startResize(placeId = 2L, headerHeightDp = any(), ownerKey = any()) }
            assertEquals(WidgetMenuState.Closed, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishResize persists through the controller and emits its size report`() = runTest {
        val inputFinish: ResizeFinish = ResizeFinish(
            placeId = 2L,
            storedHeightDp = 112,
            appWidgetIds = listOf(AppWidgetId(9)),
            widthDp = 240,
            heightDp = 112
        )
        val inputReport: WidgetSizeReport = WidgetSizeReport(
            appWidgetIds = listOf(AppWidgetId(9)),
            widthDp = 240,
            heightDp = 112
        )
        val mockResizeController: IWidgetResizeController = createResizeControllerMock(
            resizeState = MutableStateFlow(resizeState()),
            finish = inputFinish,
            report = inputReport
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockResizeController = mockResizeController)

        viewModel.events.test {
            viewModel.finishResize()
            advanceUntilIdle()
            assertEquals(
                WidgetEvent.ReportWidgetSize(appWidgetIds = listOf(AppWidgetId(9)), widthDp = 240, heightDp = 112),
                awaitItem()
            )
            coVerify(exactly = 1) { mockResizeController.persistResize(inputFinish) }
            assertNull(mockResizeController.resizeState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Back and Home finish Resize and are consumed while resizing`() = runTest {
        val inputFinish: ResizeFinish = ResizeFinish(
            placeId = 2L,
            storedHeightDp = 112,
            appWidgetIds = listOf(AppWidgetId(9)),
            widthDp = 240,
            heightDp = 112
        )
        val inputResizeState: MutableStateFlow<ResizeState?> = MutableStateFlow(resizeState())
        val mockResizeController: IWidgetResizeController = createResizeControllerMock(
            resizeState = inputResizeState,
            finish = inputFinish
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockResizeController = mockResizeController)

        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        inputResizeState.value = resizeState()
        assertTrue(viewModel.onHomeIntent())
        advanceUntilIdle()

        verify(exactly = 2) { mockResizeController.finishResize() }
        assertNull(inputResizeState.value)
        assertFalse(viewModel.onBack())
        assertFalse(viewModel.onHomeIntent())
    }

    @Test
    fun `forwards the Home height and Widget Area width to the resize controller`() = runTest {
        val mockResizeController: IWidgetResizeController = createResizeControllerMock()
        val viewModel: WidgetAreaViewModel = createViewModel(mockResizeController = mockResizeController)

        viewModel.setHomeHeight(heightDp = 400)
        viewModel.setWidgetAreaWidth(widthDp = 240)

        verify(exactly = 1) { mockResizeController.setHomeHeight(400) }
        verify(exactly = 1) { mockResizeController.setWidgetAreaWidth(240) }
    }

    @Test
    fun `forwards current places to the resize controller`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockResizeController: IWidgetResizeController = createResizeControllerMock()
        createViewModel(mockRepository = mockRepository, mockResizeController = mockResizeController)
        advanceUntilIdle()

        verify(exactly = 1) {
            mockResizeController.onPlacesChanged(match { places -> places.map { place -> place.id } == listOf(1L, 2L) }, null)
        }

        mockRepository.removeWidget(widgetId = 2L)
        advanceUntilIdle()

        verify(exactly = 1) {
            mockResizeController.onPlacesChanged(match { places -> places.map { place -> place.id } == listOf(1L) }, null)
        }
    }

    @Test
    fun `renders a stored height clamped to the current bounds`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, heightDp = 400))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            appWidgetProviderInfo(minResizeHeightDp = 48, maxResizeHeightDp = 200, isVerticallyResizable = true)
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.setHomeHeight(heightDp = 600)
        viewModel.widgetAreaState.test {
            val actualPlace: WidgetPlaceModel = expectMostRecentItem().places.last()
            assertEquals(200, actualPlace.heightDp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Edit Widgets from the Header menu enters the mode with a draft of the current order`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, inputHomeEditState = inputHomeEditState)
        viewModel.openHeaderMenu()
        viewModel.onMenuAction(WidgetMenuAction.EditWidgets)
        advanceUntilIdle()
        assertEquals(HomeEditMode.Widgets, inputHomeEditState.mode.value)
        assertEquals(listOf(1L, 2L), viewModel.editState.value?.placeIds)
        assertEquals(emptySet<Long>(), viewModel.editState.value?.removedWidgetIds)
    }

    @Test
    fun `Edit Widgets from an App Widget menu enters the mode`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, inputHomeEditState = inputHomeEditState)
        viewModel.openAppWidgetMenu(widgetId = 2L)
        viewModel.onMenuAction(WidgetMenuAction.EditWidgets)
        advanceUntilIdle()
        assertEquals(HomeEditMode.Widgets, inputHomeEditState.mode.value)
        assertEquals(listOf(1L, 2L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `Edit Widgets is ignored while Favorites or Resize is active`() = runTest {
        val inputHomeEditState: HomeEditState = HomeEditState().apply { tryEnter(HomeEditMode.Favorites) }
        val viewModel: WidgetAreaViewModel = createViewModel(inputHomeEditState = inputHomeEditState)
        viewModel.editWidgets()
        advanceUntilIdle()
        assertEquals(HomeEditMode.Favorites, inputHomeEditState.mode.value)
        assertNull(viewModel.editState.value)
        inputHomeEditState.exit(HomeEditMode.Favorites)
        inputHomeEditState.tryEnter(HomeEditMode.Resizing(placeId = 1L))
        viewModel.editWidgets()
        advanceUntilIdle()
        assertEquals(HomeEditMode.Resizing(1L), inputHomeEditState.mode.value)
        assertNull(viewModel.editState.value)
    }

    @Test
    fun `moving a place in Edit Widgets reorders the draft and rejects out of range moves`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L), appWidgetPlace(placeId = 3L, widgetId = 3L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
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
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost()
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        advanceUntilIdle()
        val actualDraft: WidgetEditDraft = viewModel.editState.value!!
        assertEquals(listOf(1L), actualDraft.placeIds)
        assertFalse(actualDraft.places.any { place -> place.placeId == 2L })
        assertEquals(setOf(2L), actualDraft.removedWidgetIds)
        coVerify(exactly = 0) { mockRepository.commitEdit(any()) }
        coVerify(exactly = 0) { mockRepository.removeHeader() }
        verify(exactly = 0) { mockHost.deleteId(any()) }
        assertEquals(listOf(1L, 2L), mockRepository.places.first().map { place -> place.id })
    }

    @Test
    fun `widgetAreaState renders the draft order and flags editing`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.widgetAreaState.test {
            assertEquals(listOf(1L, 2L), expectMostRecentItem().places.map { place -> place.id })
            viewModel.editWidgets()
            advanceUntilIdle()
            viewModel.moveWidgetPlace(fromIndex = 0, toIndex = 1)
            val actualMoved: WidgetAreaState = expectMostRecentItem()
            assertTrue(actualMoved.isEditing)
            assertEquals(listOf(2L, 1L), actualMoved.places.map { place -> place.id })
            viewModel.removeWidgetPlaceInEdit(placeId = 2L)
            val actualRemoved: WidgetAreaState = expectMostRecentItem()
            assertEquals(listOf(1L), actualRemoved.places.map { place -> place.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Done commits the draft, deletes freed ids and exits only after persistence`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val mockHost: IAppWidgetHostSource = createHost()
        coEvery { mockRepository.commitEdit(any()) } returns listOf(AppWidgetId(9))
        val viewModel: WidgetAreaViewModel = createViewModel(
            mockRepository = mockRepository,
            inputHomeEditState = inputHomeEditState,
            mockAppWidgetHost = mockHost
        )
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.moveWidgetPlace(fromIndex = 0, toIndex = 1)
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        viewModel.events.test {
            viewModel.finishEditingWidgets()
            assertEquals(WidgetEvent.RemoveHostView(AppWidgetId(9)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        coVerify(exactly = 1) { mockRepository.commitEdit(editDroppingSecondPlace()) }
        verify(exactly = 1) { mockHost.deleteId(AppWidgetId(9)) }
        assertNull(viewModel.editState.value)
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
    }

    @Test
    fun `Done twice commits and frees ids once`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost()
        coEvery { mockRepository.commitEdit(any()) } returns listOf(AppWidgetId(9))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        viewModel.finishEditingWidgets()
        viewModel.finishEditingWidgets()
        advanceUntilIdle()
        coVerify(exactly = 1) { mockRepository.commitEdit(any()) }
        verify(exactly = 1) { mockHost.deleteId(AppWidgetId(9)) }
        assertNull(viewModel.editState.value)
    }

    @Test
    fun `Back commits Edit Widgets and is consumed`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, inputHomeEditState = inputHomeEditState)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        coVerify(exactly = 1) { mockRepository.commitEdit(editDroppingSecondPlace()) }
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
    }

    @Test
    fun `Home commits Edit Widgets and is consumed`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val inputHomeEditState: HomeEditState = HomeEditState()
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, inputHomeEditState = inputHomeEditState)
        viewModel.editWidgets()
        advanceUntilIdle()
        viewModel.removeWidgetPlaceInEdit(placeId = 2L)
        assertTrue(viewModel.onHomeIntent())
        advanceUntilIdle()
        coVerify(exactly = 1) { mockRepository.commitEdit(editDroppingSecondPlace()) }
        assertEquals(HomeEditMode.None, inputHomeEditState.mode.value)
    }

    @Test
    fun `a vanished place is dropped from the draft`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.editWidgets()
        advanceUntilIdle()
        mockRepository.removeWidget(widgetId = 2L)
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.editState.value?.placeIds)
    }

    @Test
    fun `a place added while editing is appended to the draft`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(headerPlace))
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        viewModel.editWidgets()
        advanceUntilIdle()
        mockRepository.addAppWidget(
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
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 1L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertFalse(actualMenu.items.any { item -> item.id == WidgetMenuAction.ReplaceHeader })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hides Replace Header when the Header shares the widget's stack`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(stackPlace()))
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertFalse(actualMenu.items.any { item -> item.id == WidgetMenuAction.ReplaceHeader })
            val actualExtend: WidgetMenuItem.Toggle = actualMenu.items
                .filterIsInstance<WidgetMenuItem.Toggle>()
                .first { item -> item.id == WidgetMenuAction.ExtendUnderRail }
            assertFalse(actualExtend.enabled)
            assertEquals(CoreUiR.string.not_available_in_stack, actualExtend.supportingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orders the stack page menu with Edit widgets before Remove from stack`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(stackPlace()))
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()

            assertEquals(
                expectedAppWidgetMenuOrder(resize = true, replaceHeader = false, removeFromStack = true),
                actualMenu.items.map { item -> item.id }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows Replace Header when a Header exists outside the widget's place`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        val mockHost: IAppWidgetHostSource = createHost(
            providers = listOf(appWidgetProviderInfo())
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository, mockAppWidgetHost = mockHost)

        viewModel.menuState.test {
            viewModel.openAppWidgetMenu(widgetId = 2L)
            val actualMenu: WidgetMenuState.AppWidgetMenu = expectState()
            assertEquals(expectedAppWidgetMenuOrder(), actualMenu.items.map { item -> item.id })
            val actualExtend: WidgetMenuItem.Toggle = actualMenu.items
                .filterIsInstance<WidgetMenuItem.Toggle>()
                .single { item -> item.id == WidgetMenuAction.ExtendUnderRail }
            assertTrue(actualExtend.enabled)
            assertEquals(CoreUiR.string.extend_under_rail_supporting_text, actualExtend.supportingText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaces the Header, emits Undo and undoes through the repository`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L))
        )
        coEvery { mockRepository.replaceHeader(widgetId = 2L) } returns ReplaceHeaderUndo(
            widgetId = 2L,
            headerPlaceId = 1L,
            headerPosition = 0,
            headerPagePosition = 0,
            headerHeightDp = null,
            headerCurrentPage = 0,
            widgetPlaceId = 2L,
            widgetPosition = 1,
            widgetPagePosition = 0,
            widgetHeightDp = null,
            widgetCurrentPage = 0
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)
        var actualAction: UndoAction.ReplaceHeader? = null

        viewModel.events.test {
            viewModel.replaceHeader(widgetId = 2L)
            val actualEvent: WidgetEvent.ShowUndo = awaitItem() as WidgetEvent.ShowUndo
            val actualReplaceHeader: UndoAction.ReplaceHeader = actualEvent.action as UndoAction.ReplaceHeader
            actualAction = actualReplaceHeader
            assertEquals(1L, actualReplaceHeader.undo.headerPlaceId)
            assertEquals(2L, actualReplaceHeader.undo.widgetPlaceId)
        }
        coVerify(exactly = 1) { mockRepository.replaceHeader(widgetId = 2L) }
        assertEquals(WidgetMenuState.Closed, viewModel.menuState.value)

        viewModel.undo(action = actualAction!!)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockRepository.undoReplaceHeader(any()) }
    }

    @Test
    fun `delegates Extend under Rail to the repository`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(appWidgetPlace(placeId = 1L, widgetId = 1L))
        )
        val viewModel: WidgetAreaViewModel = createViewModel(mockRepository = mockRepository)

        viewModel.setExtendsUnderRail(widgetId = 1L, extendsUnderRail = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.setExtendsUnderRail(widgetId = 1L, extendsUnderRail = true) }
    }

    @Test
    fun `scroll to place requests are set and cleared`() = runTest {
        val viewModel: WidgetAreaViewModel = createViewModel()

        viewModel.requestScrollToPlace(placeId = 2L)
        assertEquals(2L, viewModel.scrollToPlaceId.value)

        viewModel.onScrolledToPlace()
        assertNull(viewModel.scrollToPlaceId.value)
    }

    private fun editDroppingSecondPlace(): WidgetAreaEdit = WidgetAreaEdit(
        places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L))),
        removedWidgetIds = setOf(2L)
    )

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
        val actualState: WidgetMenuState = expectMostRecentItem()
        assertTrue("Expected ${T::class.simpleName} but was $actualState", actualState is T)
        return actualState as T
    }
}
