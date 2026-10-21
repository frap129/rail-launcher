package feature.widgets

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderSettings
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetPlace
import core.ui.R as CoreUiR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    data class Choice(
        override val id: WidgetMenuAction,
        @StringRes val valueLabel: Int,
        val options: List<WidgetMenuChoiceOption>,
        val selectedKey: WidgetMenuChoiceKey,
        val expanded: Boolean
    ) : WidgetMenuItem
}

@JvmInline
value class WidgetMenuChoiceKey(val value: String)

data class WidgetMenuChoiceOption(val key: WidgetMenuChoiceKey, @StringRes val label: Int)

enum class WidgetMenuAction {
    Clock,
    Date,
    Battery,
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

private data class HeaderSettingsAndBattery(val settings: HeaderSettings, val battery: BatteryStatus?)

private data class MenuContext(val target: WidgetMenuTarget?, val expandedChoice: WidgetMenuAction?)

class WidgetAreaViewModel(
    private val repository: IWidgetAreaRepository,
    private val clockSource: IClockSource,
    private val formatter: HeaderFormatter,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState,
    private val launchableRepository: ILaunchableRepository,
    batterySource: IBatterySource
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val batteryStatus: Flow<BatteryStatus?> = settingsRepository.headerSettings
        .map { settings -> settings.batteryVisibility }
        .distinctUntilChanged()
        .flatMapLatest { visibility ->
            if (visibility == BatteryVisibility.NEVER) {
                flowOf(null)
            } else {
                batterySource.status
            }
        }
        .distinctUntilChanged()

    private val settingsAndBattery: Flow<HeaderSettingsAndBattery> = combine(
        settingsRepository.headerSettings,
        batteryStatus
    ) { settings, battery -> HeaderSettingsAndBattery(settings = settings, battery = battery) }

    val widgetAreaState: StateFlow<WidgetAreaState> = combine(
        repository.places,
        clockSource.readings,
        settingsAndBattery,
        launchableRepository.launchableState
    ) { places, reading, settingsAndBattery, launchableState ->
        WidgetAreaState(
            places = places.mapNotNull { place ->
                place.toModel(reading, settingsAndBattery.settings, settingsAndBattery.battery, launchableState.launchables)
            },
            hasWidgets = places.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetAreaState()
    )

    private val menuTarget: MutableStateFlow<WidgetMenuTarget?> = MutableStateFlow(null)
    private val expandedChoice: MutableStateFlow<WidgetMenuAction?> = MutableStateFlow(null)

    private val menuContext: Flow<MenuContext> = combine(menuTarget, expandedChoice) { target, expanded ->
        MenuContext(target = target, expandedChoice = expanded)
    }

    val menuState: StateFlow<WidgetMenuState> = combine(
        menuContext,
        repository.places,
        settingsAndBattery,
        homeEditState.mode,
        clockSource.readings
    ) { context, places, settingsAndBattery, mode, reading ->
        if (mode != HomeEditMode.None) {
            WidgetMenuState.Closed
        } else {
            context.target.toMenuState(
                places = places,
                settings = settingsAndBattery.settings,
                reading = reading,
                expandedChoice = context.expandedChoice
            )
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
            settingsRepository.setClockVisible(visible)
        }
    }

    fun setDateVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDateVisible(visible)
        }
    }

    fun toggleChoice(action: WidgetMenuAction) {
        expandedChoice.value = if (expandedChoice.value == action) null else action
    }

    fun selectChoice(action: WidgetMenuAction, key: WidgetMenuChoiceKey) {
        if (action == WidgetMenuAction.Battery) {
            BatteryVisibility.entries.firstOrNull { visibility -> visibility.name == key.value }
                ?.let { visibility -> selectBatteryVisibility(visibility) }
        }
    }

    fun selectBatteryVisibility(visibility: BatteryVisibility) {
        expandedChoice.value = null
        viewModelScope.launch {
            settingsRepository.setBatteryVisibility(visibility)
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

    private fun WidgetMenuTarget?.toMenuState(
        places: List<WidgetPlace>,
        settings: HeaderSettings,
        reading: ClockReading,
        expandedChoice: WidgetMenuAction?
    ): WidgetMenuState = when (this) {
        WidgetMenuTarget.Header -> {
            if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
                WidgetMenuState.Closed
            } else {
                WidgetMenuState.HeaderMenu(items = headerMenuItems(settings = settings, expandedChoice = expandedChoice))
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

    private fun headerMenuItems(settings: HeaderSettings, expandedChoice: WidgetMenuAction?): List<WidgetMenuItem> = listOf(
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
        WidgetMenuItem.Choice(
            id = WidgetMenuAction.Battery,
            valueLabel = settings.batteryVisibility.labelResource(),
            options = BatteryVisibility.entries.map { visibility ->
                WidgetMenuChoiceOption(key = WidgetMenuChoiceKey(visibility.name), label = visibility.labelResource())
            },
            selectedKey = WidgetMenuChoiceKey(settings.batteryVisibility.name),
            expanded = expandedChoice == WidgetMenuAction.Battery
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

    private fun WidgetPlace.toModel(
        reading: ClockReading,
        settings: HeaderSettings,
        battery: BatteryStatus?,
        launchables: List<Launchable>
    ): WidgetPlaceModel? {
        val widgetModels: List<WidgetModel> = widgets.mapNotNull { widget -> widget.toModel(reading, settings, battery, launchables) }
        return if (widgetModels.isEmpty()) {
            null
        } else {
            WidgetPlaceModel(id = id, widgets = widgetModels)
        }
    }

    private fun PlacedWidget.toModel(
        reading: ClockReading,
        settings: HeaderSettings,
        battery: BatteryStatus?,
        launchables: List<Launchable>
    ): WidgetModel? = when (this) {
        is PlacedWidget.Header -> formatter.format(id = id, reading = reading, settings = settings, battery = battery).withTapTargets(
            settings = settings,
            launchables = launchables,
            atEpochMillis = reading.instant.toEpochMilli()
        )

        is PlacedWidget.AppWidget -> null
    }
}

@StringRes
fun BatteryVisibility.labelResource(): Int = when (this) {
    BatteryVisibility.NEVER -> CoreUiR.string.battery_never
    BatteryVisibility.ALWAYS -> CoreUiR.string.battery_always
    BatteryVisibility.CHARGING_OR_LOW -> CoreUiR.string.battery_charging_or_low
}
