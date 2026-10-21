package feature.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.prefs.HeaderSettings
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.time.IDatePatternSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import core.ui.R as CoreUiR
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_TIMEOUT_MS: Long = 5_000L
private const val TIME_SKELETON_12_HOUR: String = "hm"
private const val TIME_SKELETON_24_HOUR: String = "Hm"
private const val DATE_SKELETON: String = "EEEEMMMMd"
private const val SPOKEN_TIME_PATTERN_12_HOUR: String = "h:mm a"
private const val SPOKEN_TIME_PATTERN_24_HOUR: String = "HH:mm"
private val DAY_PERIOD_PATTERN: Regex = Regex("[\\s\\u00A0\\u202F]*[aB]+[\\s\\u00A0\\u202F]*")

sealed interface WidgetMenuState {
    data object Closed : WidgetMenuState

    data class HeaderMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class EmptyAreaMenu(val items: List<WidgetMenuItem>) : WidgetMenuState
}

sealed interface WidgetMenuItem {
    val id: WidgetMenuAction

    data class Toggle(override val id: WidgetMenuAction, val checked: Boolean, val enabled: Boolean, val supportingText: Int? = null) :
        WidgetMenuItem

    data class Action(override val id: WidgetMenuAction) : WidgetMenuItem
}

enum class WidgetMenuAction {
    Clock,
    Date,
    RemoveHeader,
    RestoreHeader,
    WidgetSettings
}

sealed interface WidgetAreaEvent {
    data class ShowUndo(val position: Int) : WidgetAreaEvent

    data object NavigateToWidgetSettings : WidgetAreaEvent
}

private enum class WidgetMenuTarget {
    Header,
    EmptyArea
}

class WidgetAreaViewModel(
    private val repository: IWidgetAreaRepository,
    clockSource: IClockSource,
    private val datePatternSource: IDatePatternSource,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState
) : ViewModel() {
    val widgetAreaState: StateFlow<WidgetAreaState> = combine(
        repository.places,
        clockSource.readings,
        settingsRepository.headerSettings
    ) { places, reading, settings ->
        WidgetAreaState(
            places = places.mapNotNull { place -> place.toModel(reading, settings) },
            hasWidgets = places.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetAreaState()
    )

    private val menuTarget: MutableStateFlow<WidgetMenuTarget?> = MutableStateFlow(null)

    val menuState: StateFlow<WidgetMenuState> = combine(
        menuTarget,
        repository.places,
        settingsRepository.headerSettings,
        homeEditState.mode
    ) { target, places, settings, mode ->
        if (mode != HomeEditMode.None) {
            WidgetMenuState.Closed
        } else {
            target.toMenuState(places = places, settings = settings)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetMenuState.Closed
    )

    private val eventChannel: Channel<WidgetAreaEvent> = Channel(Channel.BUFFERED)
    val events: Flow<WidgetAreaEvent> = eventChannel.receiveAsFlow()

    fun openHeaderMenu() {
        if (homeEditState.mode.value == HomeEditMode.None) {
            menuTarget.value = WidgetMenuTarget.Header
        }
    }

    fun openEmptyAreaMenu() {
        if (homeEditState.mode.value == HomeEditMode.None) {
            menuTarget.value = WidgetMenuTarget.EmptyArea
        }
    }

    fun closeMenu() {
        menuTarget.value = null
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

    fun removeHeader() {
        viewModelScope.launch {
            val position: Int = repository.removeHeader() ?: return@launch
            menuTarget.value = null
            eventChannel.send(WidgetAreaEvent.ShowUndo(position = position))
        }
    }

    fun restoreHeader() {
        viewModelScope.launch {
            repository.restoreHeader(position = 0)
            menuTarget.value = null
        }
    }

    fun undoRemoveHeader(position: Int) {
        viewModelScope.launch {
            repository.restoreHeader(position = position)
        }
    }

    fun openWidgetSettings() {
        menuTarget.value = null
        viewModelScope.launch {
            eventChannel.send(WidgetAreaEvent.NavigateToWidgetSettings)
        }
    }

    private fun WidgetMenuTarget?.toMenuState(places: List<WidgetPlace>, settings: HeaderSettings): WidgetMenuState = when (this) {
        WidgetMenuTarget.Header -> {
            if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
                WidgetMenuState.Closed
            } else {
                WidgetMenuState.HeaderMenu(items = headerMenuItems(settings))
            }
        }

        WidgetMenuTarget.EmptyArea -> WidgetMenuState.EmptyAreaMenu(items = emptyAreaMenuItems(places))

        null -> WidgetMenuState.Closed
    }

    private fun headerMenuItems(settings: HeaderSettings): List<WidgetMenuItem> = listOf(
        WidgetMenuItem.Toggle(
            id = WidgetMenuAction.Clock,
            checked = settings.clockVisible,
            enabled = settings.canHideClock,
            supportingText = if (settings.canHideClock) null else CoreUiR.string.header_last_part_supporting_text
        ),
        WidgetMenuItem.Toggle(
            id = WidgetMenuAction.Date,
            checked = settings.dateVisible,
            enabled = settings.canHideDate,
            supportingText = if (settings.canHideDate) null else CoreUiR.string.header_last_part_supporting_text
        ),
        WidgetMenuItem.Action(id = WidgetMenuAction.RemoveHeader),
        WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings)
    )

    private fun emptyAreaMenuItems(places: List<WidgetPlace>): List<WidgetMenuItem> = buildList {
        if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
            add(WidgetMenuItem.Action(id = WidgetMenuAction.RestoreHeader))
        }
        add(WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings))
    }

    private fun WidgetPlace.toModel(reading: ClockReading, settings: HeaderSettings): WidgetPlaceModel? {
        val widgetModels: List<WidgetModel> = widgets.mapNotNull { widget -> widget.toModel(reading, settings) }
        return if (widgetModels.isEmpty()) {
            null
        } else {
            WidgetPlaceModel(id = id, widgets = widgetModels)
        }
    }

    private fun PlacedWidget.toModel(reading: ClockReading, settings: HeaderSettings): WidgetModel? = when (this) {
        is PlacedWidget.Header -> toHeaderModel(reading, settings)
        is PlacedWidget.AppWidget -> null
    }

    private fun PlacedWidget.Header.toHeaderModel(reading: ClockReading, settings: HeaderSettings): HeaderModel {
        val zonedDateTime: ZonedDateTime = reading.instant.atZone(reading.zoneId)
        return HeaderModel(
            id = id,
            time = if (settings.clockVisible) timeLine(zonedDateTime, reading) else null,
            date = if (settings.dateVisible) dateLine(zonedDateTime, reading) else null
        )
    }

    private fun timeLine(zonedDateTime: ZonedDateTime, reading: ClockReading): HeaderLineModel {
        val timePattern: String = datePatternSource
            .bestPattern(
                skeleton = if (reading.isSystem24Hour) TIME_SKELETON_24_HOUR else TIME_SKELETON_12_HOUR,
                locale = reading.locale
            )
            .replace(DAY_PERIOD_PATTERN, "")
        val spokenTimePattern: String = if (reading.isSystem24Hour) {
            SPOKEN_TIME_PATTERN_24_HOUR
        } else {
            SPOKEN_TIME_PATTERN_12_HOUR
        }
        return HeaderLineModel(
            text = format(zonedDateTime, timePattern, reading.locale),
            spokenText = format(zonedDateTime, spokenTimePattern, reading.locale),
            tapTarget = HeaderTapTarget.SystemAlarms,
            clickLabel = R.string.open_alarms
        )
    }

    private fun dateLine(zonedDateTime: ZonedDateTime, reading: ClockReading): HeaderLineModel {
        val datePattern: String = datePatternSource.bestPattern(DATE_SKELETON, reading.locale)
        return HeaderLineModel(
            text = format(zonedDateTime, datePattern, reading.locale),
            spokenText = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(reading.locale)
                .format(zonedDateTime),
            tapTarget = HeaderTapTarget.SystemCalendar(atEpochMillis = reading.instant.toEpochMilli()),
            clickLabel = R.string.open_calendar
        )
    }

    private fun format(zonedDateTime: ZonedDateTime, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern, locale).format(zonedDateTime)
}
