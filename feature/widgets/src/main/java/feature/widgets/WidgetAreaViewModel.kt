package feature.widgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderSettings
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import core.ui.R as CoreUiR
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

sealed interface WidgetMenuState {
    data object Closed : WidgetMenuState

    data class HeaderMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class EmptyAreaMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class ClockStyleChooser(val selected: ClockStyle, val previews: List<HeaderModel>) : WidgetMenuState
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
    ClockStyle,
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
    EmptyArea,
    ClockStyle
}

class WidgetAreaViewModel(
    private val repository: IWidgetAreaRepository,
    private val clockSource: IClockSource,
    private val formatter: HeaderFormatter,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState,
    private val launchableRepository: ILaunchableRepository
) : ViewModel() {
    val widgetAreaState: StateFlow<WidgetAreaState> = combine(
        repository.places,
        clockSource.readings,
        settingsRepository.headerSettings,
        launchableRepository.launchableState
    ) { places, reading, settings, launchableState ->
        WidgetAreaState(
            places = places.mapNotNull { place -> place.toModel(reading, settings, launchableState.launchables) },
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
        homeEditState.mode,
        clockSource.readings
    ) { target, places, settings, mode, reading ->
        if (mode != HomeEditMode.None) {
            WidgetMenuState.Closed
        } else {
            target.toMenuState(places = places, settings = settings, reading = reading)
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

    fun openClockStyleChooser() {
        menuTarget.value = WidgetMenuTarget.ClockStyle
    }

    fun selectClockStyle(style: ClockStyle) {
        menuTarget.value = null
        viewModelScope.launch {
            settingsRepository.updateHeaderSettings { settings -> settings.copy(clockStyle = style) }
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

    private fun WidgetMenuTarget?.toMenuState(places: List<WidgetPlace>, settings: HeaderSettings, reading: ClockReading): WidgetMenuState =
        when (this) {
            WidgetMenuTarget.Header -> {
                if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
                    WidgetMenuState.Closed
                } else {
                    WidgetMenuState.HeaderMenu(items = headerMenuItems(settings))
                }
            }

            WidgetMenuTarget.ClockStyle -> {
                if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
                    WidgetMenuState.Closed
                } else {
                    WidgetMenuState.ClockStyleChooser(
                        selected = settings.clockStyle,
                        previews = formatter.previews(reading = reading, settings = settings)
                    )
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
        WidgetMenuItem.Action(id = WidgetMenuAction.ClockStyle),
        WidgetMenuItem.Action(id = WidgetMenuAction.RemoveHeader),
        WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings)
    )

    private fun emptyAreaMenuItems(places: List<WidgetPlace>): List<WidgetMenuItem> = buildList {
        if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
            add(WidgetMenuItem.Action(id = WidgetMenuAction.RestoreHeader))
        }
        add(WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings))
    }

    private fun WidgetPlace.toModel(reading: ClockReading, settings: HeaderSettings, launchables: List<Launchable>): WidgetPlaceModel? {
        val widgetModels: List<WidgetModel> = widgets.mapNotNull { widget -> widget.toModel(reading, settings, launchables) }
        return if (widgetModels.isEmpty()) {
            null
        } else {
            WidgetPlaceModel(id = id, widgets = widgetModels)
        }
    }

    private fun PlacedWidget.toModel(reading: ClockReading, settings: HeaderSettings, launchables: List<Launchable>): WidgetModel? =
        when (this) {
            is PlacedWidget.Header -> formatter.format(id = id, reading = reading, settings = settings).withTapTargets(
                settings = settings,
                launchables = launchables,
                atEpochMillis = reading.instant.toEpochMilli()
            )

            is PlacedWidget.AppWidget -> null
        }
}
