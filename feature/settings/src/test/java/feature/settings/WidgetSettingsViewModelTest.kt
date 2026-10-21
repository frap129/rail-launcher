package feature.settings

import app.cash.turbine.test
import core.data.prefs.HeaderSettings
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.MainDispatcherRule
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
            cancelAndIgnoreRemainingEvents()
        }
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
        areaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
    ): WidgetSettingsViewModel = WidgetSettingsViewModel(
        settingsRepository = settingsRepository,
        areaRepository = areaRepository
    )
}
