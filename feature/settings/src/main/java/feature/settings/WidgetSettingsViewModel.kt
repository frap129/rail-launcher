package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_TIMEOUT_MS: Long = 5_000L

data class WidgetSettingsUiState(
    val headerShown: Boolean = true,
    val clockVisible: Boolean = true,
    val dateVisible: Boolean = true,
    val canHideClock: Boolean = true,
    val canHideDate: Boolean = true
)

class WidgetSettingsViewModel(
    private val settingsRepository: IWidgetSettingsRepository,
    private val areaRepository: IWidgetAreaRepository
) : ViewModel() {
    val uiState: StateFlow<WidgetSettingsUiState> = combine(
        areaRepository.places,
        settingsRepository.headerSettings
    ) { places, settings ->
        WidgetSettingsUiState(
            headerShown = places.any { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } },
            clockVisible = settings.clockVisible,
            dateVisible = settings.dateVisible,
            canHideClock = settings.canHideClock,
            canHideDate = settings.canHideDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetSettingsUiState()
    )

    fun setHeaderShown(shown: Boolean) {
        viewModelScope.launch {
            if (shown) {
                areaRepository.restoreHeader(position = 0)
            } else {
                areaRepository.removeHeader()
            }
        }
    }

    fun setClockVisible(visible: Boolean) {
        viewModelScope.launch {
            if (!visible && !settingsRepository.headerSettings.first().dateVisible) {
                return@launch
            }
            settingsRepository.updateHeaderSettings { settings ->
                if (!visible && !settings.dateVisible) settings else settings.copy(clockVisible = visible)
            }
        }
    }

    fun setDateVisible(visible: Boolean) {
        viewModelScope.launch {
            if (!visible && !settingsRepository.headerSettings.first().clockVisible) {
                return@launch
            }
            settingsRepository.updateHeaderSettings { settings ->
                if (!visible && !settings.clockVisible) settings else settings.copy(dateVisible = visible)
            }
        }
    }
}
