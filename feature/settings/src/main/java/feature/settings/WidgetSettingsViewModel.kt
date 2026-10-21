package feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.prefs.is24Hour
import core.data.time.IClockSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import feature.widgets.HeaderFormatter
import feature.widgets.HeaderModel
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_TIMEOUT_MS: Long = 5_000L
private val COMBINING_MARKS_REGEX: Regex = Regex("\\p{M}+")

data class DateFormatOption(val preset: DateFormatPreset, val label: String)

enum class HeaderTapTargetPart {
    Time,
    Date
}

sealed interface TapActionLabel {
    data class Resource(@StringRes val id: Int) : TapActionLabel

    data class Launchable(val name: String) : TapActionLabel
}

data class TapActionLaunchable(val setting: HeaderTapSetting.Launchable, val name: String, val icon: Icon)

data class TapActionSummary(val setting: HeaderTapSetting, val label: TapActionLabel)

data class TapActionPickerState(
    val part: HeaderTapTargetPart,
    val query: String = "",
    val isLoaded: Boolean = false,
    val launchables: List<TapActionLaunchable> = emptyList()
)

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
    val dateFormatOptions: List<DateFormatOption> = emptyList(),
    val timeTapAction: TapActionSummary = TapActionSummary(
        setting = HeaderTapSetting.SystemDefault,
        label = TapActionLabel.Resource(R.string.tap_action_clock_default)
    ),
    val dateTapAction: TapActionSummary = TapActionSummary(
        setting = HeaderTapSetting.SystemDefault,
        label = TapActionLabel.Resource(R.string.tap_action_calendar_default)
    )
)

class WidgetSettingsViewModel(
    private val settingsRepository: IWidgetSettingsRepository,
    private val areaRepository: IWidgetAreaRepository,
    private val clockSource: IClockSource,
    private val formatter: HeaderFormatter,
    private val launchableRepository: ILaunchableRepository
) : ViewModel() {
    private val pickerPart: MutableStateFlow<HeaderTapTargetPart?> = MutableStateFlow(null)
    private val pickerQuery: MutableStateFlow<String> = MutableStateFlow("")

    val uiState: StateFlow<WidgetSettingsUiState> = combine(
        areaRepository.places,
        settingsRepository.headerSettings,
        clockSource.readings,
        launchableRepository.launchableState
    ) { places, settings, reading, launchableState ->
        val timeTapTarget: HeaderTapSetting = settings.resolvedTimeTapTarget.resolved(launchableState)
        val dateTapTarget: HeaderTapSetting = settings.resolvedDateTapTarget.resolved(launchableState)
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
            },
            timeTapAction = timeTapTarget.toSummary(
                defaultResource = R.string.tap_action_clock_default,
                launchables = launchableState.launchables
            ),
            dateTapAction = dateTapTarget.toSummary(
                defaultResource = R.string.tap_action_calendar_default,
                launchables = launchableState.launchables
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetSettingsUiState()
    )

    val tapActionPicker: StateFlow<TapActionPickerState?> = combine(
        pickerPart,
        pickerQuery,
        launchableRepository.launchableState
    ) { part, query, launchableState ->
        part?.let {
            TapActionPickerState(
                part = it,
                query = query,
                isLoaded = launchableState.isLoaded,
                launchables = filterLaunchables(launchableState.launchables, query).map { launchable ->
                    TapActionLaunchable(
                        setting = HeaderTapSetting.Launchable(key = launchable.key),
                        name = launchable.name,
                        icon = launchable.icon
                    )
                }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = null
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

    fun openTapActionPicker(part: HeaderTapTargetPart) {
        pickerQuery.value = ""
        pickerPart.value = part
    }

    fun closeTapActionPicker() {
        pickerPart.value = null
    }

    fun setTapActionQuery(query: String) {
        pickerQuery.value = query
    }

    fun setTimeTapTarget(target: HeaderTapSetting) {
        updateHeaderSettings { settings -> settings.copy(timeTapTarget = target.toStoredTapTarget()) }
        closeTapActionPicker()
    }

    fun setDateTapTarget(target: HeaderTapSetting) {
        updateHeaderSettings { settings -> settings.copy(dateTapTarget = target.toStoredTapTarget()) }
        closeTapActionPicker()
    }

    private fun updateHeaderSettings(transform: (HeaderSettings) -> HeaderSettings) {
        viewModelScope.launch {
            settingsRepository.updateHeaderSettings(transform)
        }
    }
}

/** Filters Launchables by name, ignoring case and accents. */
internal fun filterLaunchables(launchables: List<Launchable>, query: String): List<Launchable> {
    val normalizedQuery: String = query.normalizedForSearch()
    if (normalizedQuery.isEmpty()) {
        return launchables
    }
    return launchables.filter { launchable -> launchable.name.normalizedForSearch().contains(normalizedQuery) }
}

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS_REGEX, "").lowercase(Locale.ROOT)

private fun HeaderTapSetting.resolved(state: LaunchableState): HeaderTapSetting =
    if (state.isLoaded && this is HeaderTapSetting.Launchable && state.launchables.none { launchable -> launchable.key == key }) {
        HeaderTapSetting.SystemDefault
    } else {
        this
    }

private fun HeaderTapSetting.toSummary(defaultResource: Int, launchables: List<Launchable>): TapActionSummary = TapActionSummary(
    setting = this,
    label = toLabel(defaultResource = defaultResource, launchables = launchables)
)

private fun HeaderTapSetting.toLabel(defaultResource: Int, launchables: List<Launchable>): TapActionLabel = when (this) {
    HeaderTapSetting.SystemDefault -> TapActionLabel.Resource(defaultResource)

    HeaderTapSetting.Nothing -> TapActionLabel.Resource(R.string.tap_action_nothing)

    is HeaderTapSetting.Launchable -> launchables.firstOrNull { launchable -> launchable.key == key }
        ?.let { launchable -> TapActionLabel.Launchable(name = launchable.name) }
        ?: TapActionLabel.Resource(defaultResource)
}

private fun HeaderTapSetting.toStoredTapTarget(): HeaderTapSetting? = if (this == HeaderTapSetting.SystemDefault) null else this
