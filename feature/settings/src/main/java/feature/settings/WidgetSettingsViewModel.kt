package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.TimeFormat
import core.data.prefs.is24Hour
import core.data.time.IClockSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import feature.widgets.HeaderFormatter
import feature.widgets.HeaderModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_TIMEOUT_MS: Long = 5_000L

data class DateFormatOption(val preset: DateFormatPreset, val label: String)

data class WidgetSettingsUiState(
    val headerShown: Boolean = true,
    val clockVisible: Boolean = true,
    val dateVisible: Boolean = true,
    val canHideClock: Boolean = true,
    val canHideDate: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.CLASSIC,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showAmPm: Boolean = false,
    val canShowAmPm: Boolean = true,
    val dateFormat: DateFormatPreset = DateFormatPreset.FULL,
    val alignment: HeaderAlignment = HeaderAlignment.START,
    val size: HeaderSize = HeaderSize.DEFAULT,
    val clockStylePreviews: List<HeaderModel> = emptyList(),
    val dateFormatOptions: List<DateFormatOption> = emptyList()
)

class WidgetSettingsViewModel(
    private val settingsRepository: IWidgetSettingsRepository,
    private val areaRepository: IWidgetAreaRepository,
    private val clockSource: IClockSource,
    private val formatter: HeaderFormatter
) : ViewModel() {
    val uiState: StateFlow<WidgetSettingsUiState> = combine(
        areaRepository.places,
        settingsRepository.headerSettings,
        clockSource.readings
    ) { places, settings, reading ->
        WidgetSettingsUiState(
            headerShown = places.any { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } },
            clockVisible = settings.clockVisible,
            dateVisible = settings.dateVisible,
            canHideClock = settings.canHideClock,
            canHideDate = settings.canHideDate,
            clockStyle = settings.clockStyle,
            timeFormat = settings.timeFormat,
            showAmPm = settings.showAmPm,
            canShowAmPm = !settings.timeFormat.is24Hour(reading.isSystem24Hour),
            dateFormat = settings.dateFormat,
            alignment = settings.alignment,
            size = settings.size,
            clockStylePreviews = formatter.previews(reading = reading, settings = settings),
            dateFormatOptions = DateFormatPreset.entries.map { preset ->
                DateFormatOption(preset = preset, label = formatter.formatDate(reading, preset))
            }
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

    fun setClockStyle(style: ClockStyle) {
        updateHeaderSettings { settings -> settings.copy(clockStyle = style) }
    }

    fun setTimeFormat(format: TimeFormat) {
        updateHeaderSettings { settings -> settings.copy(timeFormat = format) }
    }

    fun setShowAmPm(show: Boolean) {
        updateHeaderSettings { settings -> settings.copy(showAmPm = show) }
    }

    fun setDateFormat(format: DateFormatPreset) {
        updateHeaderSettings { settings -> settings.copy(dateFormat = format) }
    }

    fun setAlignment(alignment: HeaderAlignment) {
        updateHeaderSettings { settings -> settings.copy(alignment = alignment) }
    }

    fun setSize(size: HeaderSize) {
        updateHeaderSettings { settings -> settings.copy(size = size) }
    }

    private fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        viewModelScope.launch {
            settingsRepository.updateHeaderSettings(transform)
        }
    }
}
