package feature.settings

import app.cash.turbine.test
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.TimeFormat
import core.data.testing.FakeClockSource
import core.data.testing.FakeDatePatternSource
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

    private fun createViewModel(
        settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
        areaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
        clockSource: FakeClockSource = FakeClockSource(reading())
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
        )
    )

    private fun reading(isSystem24Hour: Boolean = false): ClockReading = ClockReading(
        instant = Instant.parse("2026-10-20T14:05:00Z"),
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = isSystem24Hour
    )
}
