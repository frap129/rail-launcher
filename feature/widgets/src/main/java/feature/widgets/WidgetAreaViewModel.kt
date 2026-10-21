package feature.widgets

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import core.data.calendar.ICalendarEventSource
import core.data.calendar.MAX_EVENT_LOOKAHEAD_HOURS
import core.data.calendar.UpcomingEvent
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AddAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.BindRequest
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.RemoveAppWidget
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetLifecycleReconciler
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.iconOr
import core.data.widgets.labelOr
import core.ui.R as CoreUiR
import java.text.Collator
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val STATE_TIMEOUT_MS: Long = 5_000L
private const val PENDING_APP_WIDGET_ID_KEY: String = "pendingAppWidgetId"
private const val PENDING_PROVIDER_KEY: String = "pendingAppWidgetProvider"
private const val PENDING_PROFILE_SERIAL_KEY: String = "pendingAppWidgetProfileSerial"
private const val PENDING_NEEDS_CONFIGURATION_KEY: String = "pendingAppWidgetNeedsConfiguration"
private const val PENDING_DEFAULT_HEIGHT_KEY: String = "pendingAppWidgetDefaultHeight"
private const val PENDING_INSERT_BELOW_PLACE_ID_KEY: String = "pendingAppWidgetInsertBelowPlaceId"
private const val PENDING_RETURN_TO_SETTINGS_KEY: String = "pendingAppWidgetReturnToSettings"
private const val PENDING_PHASE_KEY: String = "pendingAppWidgetPhase"
private const val PENDING_PHASE_BINDING: String = "binding"
private const val PENDING_PHASE_CONFIGURING: String = "configuring"

/** Where a picked App Widget is inserted and whether the flow returns to Settings. */
data class AddWidgetOrigin(val insertAt: InsertPosition, val returnToSettings: Boolean = false)

/** The pending order and removals of one Edit Widgets session. */
data class WidgetEditDraft(val placeIds: List<Long>, val removedPlaceIds: Set<Long> = emptySet())

/** One app's App Widget providers in the picker. */
data class PickerGroup(
    val appPackageName: String,
    val appLabel: String,
    val appIcon: Icon,
    val isWorkProfile: Boolean,
    val widgets: List<WidgetProviderInfo>
)

/** The allocated id and destination of an App Widget in the add flow. */
data class PendingWidget(
    val id: AppWidgetId,
    val provider: WidgetProvider,
    val profileSerial: Long,
    val needsConfiguration: Boolean,
    val defaultHeightDp: Int,
    val origin: AddWidgetOrigin
)

sealed interface AddWidgetFlowState {
    data object Idle : AddWidgetFlowState

    data class Picking(val origin: AddWidgetOrigin, val query: String, val groups: List<PickerGroup>) : AddWidgetFlowState

    data class Binding(val widget: PendingWidget) : AddWidgetFlowState

    data class Configuring(val widget: PendingWidget) : AddWidgetFlowState
}

sealed interface WidgetMenuState {
    data object Closed : WidgetMenuState

    data class HeaderMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class EmptyAreaMenu(val items: List<WidgetMenuItem>) : WidgetMenuState

    data class AppWidgetMenu(
        val widgetId: Long,
        val appWidgetId: AppWidgetId,
        val icon: Icon,
        val label: String,
        val items: List<WidgetMenuItem>
    ) : WidgetMenuState

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
    UpcomingEvents,
    ClockStyle,
    AddWidget,
    Resize,
    Reconfigure,
    RemoveHeader,
    RestoreHeader,
    RemoveWidget,
    EditWidgets,
    WidgetSettings
}

sealed interface WidgetAreaEvent {
    data class ShowUndo(val position: Int) : WidgetAreaEvent

    data object RequestCalendarPermission : WidgetAreaEvent

    data class ShowCalendarPermissionDenied(val permanentlyDenied: Boolean) : WidgetAreaEvent

    data object NavigateToWidgetSettings : WidgetAreaEvent

    data class LaunchBind(val request: BindRequest) : WidgetAreaEvent

    data class LaunchConfigure(val id: AppWidgetId) : WidgetAreaEvent

    data class LaunchReconfigure(val id: AppWidgetId) : WidgetAreaEvent

    data class RequestQuietModeOff(val profileSerial: Long) : WidgetAreaEvent

    data class ShowMessage(@StringRes val message: Int) : WidgetAreaEvent

    data class RemoveHostView(val appWidgetId: AppWidgetId) : WidgetAreaEvent

    data class ReportWidgetSize(val appWidgetIds: List<AppWidgetId>, val widthDp: Int, val heightDp: Int) : WidgetAreaEvent

    data object ReturnToSettings : WidgetAreaEvent
}

private sealed interface WidgetMenuTarget {
    data object Header : WidgetMenuTarget

    data object EmptyArea : WidgetMenuTarget

    data object ClockStyle : WidgetMenuTarget

    data class AppWidget(val widgetId: Long) : WidgetMenuTarget
}

private data class HeaderSettingsAndBattery(val settings: HeaderSettings, val battery: BatteryStatus?)

private data class EventSettingsState(val settings: EventSettings, val hasPermission: Boolean)

private data class EventData(val events: List<UpcomingEvent>, val settings: EventSettings)

private data class MenuSettings(val header: HeaderSettingsAndBattery, val events: EventSettingsState)

private data class MenuContext(val target: WidgetMenuTarget?, val expandedChoice: WidgetMenuAction?)

private data class WidgetAreaContent(
    val places: List<WidgetPlace>,
    val reading: ClockReading,
    val settings: HeaderSettings,
    val battery: BatteryStatus?,
    val launchables: List<Launchable>,
    val events: List<UpcomingEvent>,
    val eventSettings: EventSettings
)

class WidgetAreaViewModel(
    private val repository: IWidgetAreaRepository,
    private val clockSource: IClockSource,
    private val formatter: HeaderFormatter,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState,
    private val launchableRepository: ILaunchableRepository,
    private val calendarEventSource: ICalendarEventSource,
    private val appWidgetHost: IAppWidgetHostSource,
    private val savedStateHandle: SavedStateHandle,
    batterySource: IBatterySource,
    private val removeAppWidget: RemoveAppWidget,
    private val addFlowPendingIds: IAddFlowPendingIds,
    private val widgetLifecycleReconciler: WidgetLifecycleReconciler
) : ViewModel() {
    private val providers: StateFlow<List<WidgetProviderInfo>> = appWidgetHost.providers
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val addFlow: MutableStateFlow<AddWidgetFlowState> = MutableStateFlow(AddWidgetFlowState.Idle)
    val addFlowState: StateFlow<AddWidgetFlowState> = addFlow.asStateFlow()

    private val editDraft: MutableStateFlow<WidgetEditDraft?> = MutableStateFlow(null)
    val editState: StateFlow<WidgetEditDraft?> = editDraft.asStateFlow()
    private val editMutex: Mutex = Mutex()

    private val resizeController: WidgetResizeController = WidgetResizeController(
        repository = repository,
        appWidgetHost = appWidgetHost,
        homeEditState = homeEditState,
        scope = viewModelScope,
        onReportSize = { appWidgetIds, widthDp, heightDp ->
            viewModelScope.launch {
                eventChannel.send(
                    WidgetAreaEvent.ReportWidgetSize(
                        appWidgetIds = appWidgetIds,
                        widthDp = widthDp,
                        heightDp = heightDp
                    )
                )
            }
        }
    )
    val resizeState: StateFlow<ResizeState?> = resizeController.resizeState

    init {
        restorePendingWidget()
        viewModelScope.launch {
            providers.collect { providerList ->
                val picking: AddWidgetFlowState.Picking = addFlow.value as? AddWidgetFlowState.Picking ?: return@collect
                addFlow.value = picking.copy(groups = pickerGroups(providers = providerList, query = picking.query))
            }
        }
        viewModelScope.launch {
            widgetLifecycleReconciler.removedIds.collect { id ->
                eventChannel.send(WidgetAreaEvent.RemoveHostView(appWidgetId = id))
            }
        }
        viewModelScope.launch {
            repository.places.collect { places ->
                editDraft.update { draft ->
                    if (draft == null) {
                        null
                    } else {
                        val sourceIds: Set<Long> = places.mapTo(HashSet()) { place -> place.id }
                        val keptIds: List<Long> = draft.placeIds.filter { placeId -> placeId in sourceIds }
                        val appendedIds: List<Long> = places.map { place -> place.id }.filterNot { placeId -> placeId in keptIds }
                        draft.copy(placeIds = keptIds + appendedIds)
                    }
                }
            }
        }
    }

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

    private val eventSettingsState: Flow<EventSettingsState> = combine(
        settingsRepository.eventSettings,
        calendarEventSource.hasPermission
    ) { settings, hasPermission -> EventSettingsState(settings = settings, hasPermission = hasPermission) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventData: Flow<EventData> = eventSettingsState
        .flatMapLatest { state ->
            if (state.settings.enabled && state.hasPermission) {
                val nowMillis: Long = clockSource.readings.first().instant.toEpochMilli()
                calendarEventSource.events(
                    windowEndMillis = nowMillis + Duration.ofHours(MAX_EVENT_LOOKAHEAD_HOURS.toLong()).toMillis()
                )
            } else {
                flowOf(emptyList())
            }
        }
        .combine(settingsRepository.eventSettings) { events, settings -> EventData(events = events, settings = settings) }

    private val widgetAreaContent: Flow<WidgetAreaContent> = combine(
        repository.places,
        clockSource.readings,
        settingsAndBattery,
        launchableRepository.launchableState,
        eventData
    ) { places, reading, settingsAndBattery, launchableState, events ->
        WidgetAreaContent(
            places = places,
            reading = reading,
            settings = settingsAndBattery.settings,
            battery = settingsAndBattery.battery,
            launchables = launchableState.launchables,
            events = events.events,
            eventSettings = events.settings
        )
    }

    val widgetAreaState: StateFlow<WidgetAreaState> = combine(
        widgetAreaContent,
        resizeController.homeHeightDp,
        editDraft
    ) { content, homeHeightDp, draft ->
        val orderedPlaces: List<WidgetPlace> = if (draft == null) {
            content.places
        } else {
            val placeById: Map<Long, WidgetPlace> = content.places.associateBy { place -> place.id }
            val drafted: List<WidgetPlace> = draft.placeIds.mapNotNull { placeId ->
                placeById[placeId]?.takeUnless { place -> place.id in draft.removedPlaceIds }
            }
            val added: List<WidgetPlace> = content.places.filter { place ->
                place.id !in draft.placeIds && place.id !in draft.removedPlaceIds
            }
            drafted + added
        }
        WidgetAreaState(
            places = orderedPlaces.mapNotNull { place ->
                place.toModel(
                    reading = content.reading,
                    settings = content.settings,
                    battery = content.battery,
                    launchables = content.launchables,
                    events = content.events,
                    eventSettings = content.eventSettings,
                    homeHeightDp = homeHeightDp
                )
            },
            hasWidgets = orderedPlaces.isNotEmpty(),
            isEditing = draft != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetAreaState()
    )

    private val menuTarget: MutableStateFlow<WidgetMenuTarget?> = MutableStateFlow(null)
    private val expandedChoice: MutableStateFlow<WidgetMenuAction?> = MutableStateFlow(null)
    private val calendarPermissionGate: CalendarPermissionGate = CalendarPermissionGate(calendarEventSource)

    private val menuContext: Flow<MenuContext> = combine(menuTarget, expandedChoice) { target, expanded ->
        MenuContext(target = target, expandedChoice = expanded)
    }

    private val menuSettings: Flow<MenuSettings> = combine(settingsAndBattery, eventSettingsState) { header, events ->
        MenuSettings(header = header, events = events)
    }

    val menuState: StateFlow<WidgetMenuState> = combine(
        menuContext,
        repository.places,
        menuSettings,
        homeEditState.mode,
        clockSource.readings
    ) { context, places, menuSettings, mode, reading ->
        if (mode != HomeEditMode.None) {
            WidgetMenuState.Closed
        } else {
            context.target.toMenuState(
                places = places,
                menuSettings = menuSettings,
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

    fun editWidgets() {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = null
        viewModelScope.launch {
            val placeIds: List<Long> = repository.places.first().map { place -> place.id }
            if (homeEditState.tryEnter(HomeEditMode.Widgets)) {
                editDraft.value = WidgetEditDraft(placeIds = placeIds)
            }
        }
    }

    fun moveWidgetPlace(fromIndex: Int, toIndex: Int) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        if (fromIndex !in draft.placeIds.indices || toIndex !in draft.placeIds.indices) {
            Timber.w("Ignoring Widget place move from %d to %d outside %d places", fromIndex, toIndex, draft.placeIds.size)
            return
        }
        if (fromIndex == toIndex) {
            return
        }
        editDraft.value = draft.copy(
            placeIds = draft.placeIds.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        )
    }

    fun removeWidgetPlaceInEdit(placeId: Long) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        if (placeId !in draft.placeIds) {
            return
        }
        editDraft.value = draft.copy(
            placeIds = draft.placeIds - placeId,
            removedPlaceIds = draft.removedPlaceIds + placeId
        )
    }

    fun finishEditingWidgets() {
        if (editDraft.value == null) {
            return
        }
        viewModelScope.launch {
            editMutex.withLock {
                val draft: WidgetEditDraft = editDraft.value ?: return@withLock
                val freedIds: List<AppWidgetId> = repository.commitEdit(
                    order = draft.placeIds,
                    removed = draft.removedPlaceIds
                )
                freedIds.forEach { id ->
                    appWidgetHost.deleteId(id = id)
                    eventChannel.send(WidgetAreaEvent.RemoveHostView(appWidgetId = id))
                }
                editDraft.value = null
                homeEditState.exit(HomeEditMode.Widgets)
            }
        }
    }

    fun openWidgetSettings() {
        menuTarget.value = null
        viewModelScope.launch {
            eventChannel.send(WidgetAreaEvent.NavigateToWidgetSettings)
        }
    }

    fun setUpcomingEventsEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                settingsRepository.setEventsEnabled(false)
            }
            return
        }
        when (val decision: CalendarPermissionDecision = calendarPermissionGate.decideEnable()) {
            CalendarPermissionDecision.Enable -> viewModelScope.launch {
                settingsRepository.setEventsEnabled(true)
            }

            CalendarPermissionDecision.Request -> viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.RequestCalendarPermission)
            }

            is CalendarPermissionDecision.Denied -> viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.ShowCalendarPermissionDenied(permanentlyDenied = decision.permanentlyDenied))
            }
        }
    }

    fun onCalendarPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        when (val decision: CalendarPermissionDecision = calendarPermissionGate.onPermissionResult(granted, canAskAgain)) {
            CalendarPermissionDecision.Enable -> viewModelScope.launch {
                settingsRepository.setEventsEnabled(true)
            }

            CalendarPermissionDecision.Request -> Unit

            is CalendarPermissionDecision.Denied -> viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.ShowCalendarPermissionDenied(permanentlyDenied = decision.permanentlyDenied))
            }
        }
    }

    fun refreshCalendarPermission() {
        calendarPermissionGate.refreshPermission()
    }

    fun setHomeHeight(heightDp: Int) = resizeController.setHomeHeight(heightDp = heightDp)

    fun setWidgetAreaWidth(widthDp: Int) = resizeController.setWidgetAreaWidth(widthDp = widthDp)

    fun startResize(placeId: Long) {
        viewModelScope.launch {
            if (resizeController.startResize(placeId = placeId)) {
                menuTarget.value = null
            }
        }
    }

    private fun startResizeForWidget(widgetId: Long) {
        viewModelScope.launch {
            if (resizeController.startResizeForWidget(widgetId = widgetId)) {
                menuTarget.value = null
            }
        }
    }

    fun dragResize(deltaDp: Float) = resizeController.dragResize(deltaDp = deltaDp)

    fun stepResize(direction: ResizeStep) = resizeController.stepResize(direction = direction)

    fun resetResize() = resizeController.resetResize()

    fun finishResize() = resizeController.finishResize()

    fun onBack(): Boolean = finishEditingOrResize(fallback = resizeController::onBack)

    fun onHomeIntent(): Boolean = finishEditingOrResize(fallback = resizeController::onHomeIntent)

    private fun finishEditingOrResize(fallback: () -> Boolean): Boolean {
        if (editDraft.value == null) {
            return fallback()
        }
        finishEditingWidgets()
        return true
    }

    fun openAppWidgetMenu(widgetId: Long) {
        if (homeEditState.mode.value == HomeEditMode.None) {
            menuTarget.value = WidgetMenuTarget.AppWidget(widgetId = widgetId)
        }
    }

    fun onMenuAction(action: WidgetMenuAction) {
        when (action) {
            WidgetMenuAction.AddWidget -> startAddFromMenu()

            WidgetMenuAction.Resize -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                startResizeForWidget(widgetId = target.widgetId)
            }

            WidgetMenuAction.Reconfigure -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                reconfigure(widgetId = target.widgetId)
            }

            WidgetMenuAction.RemoveWidget -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                removeWidget(widgetId = target.widgetId)
            }

            WidgetMenuAction.ClockStyle -> openClockStyleChooser()

            WidgetMenuAction.RemoveHeader -> removeHeader()

            WidgetMenuAction.RestoreHeader -> restoreHeader()

            WidgetMenuAction.EditWidgets -> editWidgets()

            WidgetMenuAction.WidgetSettings -> openWidgetSettings()

            WidgetMenuAction.Clock,
            WidgetMenuAction.Date,
            WidgetMenuAction.Battery,
            WidgetMenuAction.UpcomingEvents -> Unit
        }
    }

    fun startAdd(origin: AddWidgetOrigin) {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = null
        addFlow.value = AddWidgetFlowState.Picking(
            origin = origin,
            query = "",
            groups = pickerGroups(providers = providers.value, query = "")
        )
    }

    fun setPickerQuery(query: String) {
        val picking: AddWidgetFlowState.Picking = addFlow.value as? AddWidgetFlowState.Picking ?: return
        addFlow.value = picking.copy(
            query = query,
            groups = pickerGroups(providers = providers.value, query = query)
        )
    }

    fun pickProvider(provider: WidgetProviderInfo) {
        val picking: AddWidgetFlowState.Picking = addFlow.value as? AddWidgetFlowState.Picking ?: return
        val isStillOffered: Boolean = providers.value.any { available ->
            available.provider == provider.provider && available.profileSerial == provider.profileSerial
        }
        if (!isStillOffered) {
            addFlow.value = AddWidgetFlowState.Idle
            viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.ShowMessage(message = R.string.couldnt_add_widget))
            }
            return
        }
        val id: AppWidgetId = appWidgetHost.allocateId()
        val pending: PendingWidget = PendingWidget(
            id = id,
            provider = provider.provider,
            profileSerial = provider.profileSerial,
            needsConfiguration = provider.needsConfiguration,
            defaultHeightDp = provider.minHeightDp,
            origin = picking.origin
        )
        addFlow.value = AddWidgetFlowState.Binding(widget = pending)
        persistPending(pending = pending, phase = PENDING_PHASE_BINDING)
        val request: BindRequest = BindRequest(id = id, provider = provider.provider, profileSerial = provider.profileSerial)
        if (appWidgetHost.bindIfAllowed(request = request)) {
            continueAfterBind(pending = pending)
        } else {
            viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.LaunchBind(request = request))
            }
        }
    }

    fun cancelAdd() {
        val state: AddWidgetFlowState = addFlow.value
        when (state) {
            AddWidgetFlowState.Idle -> Unit
            is AddWidgetFlowState.Picking -> addFlow.value = AddWidgetFlowState.Idle
            is AddWidgetFlowState.Binding -> failAdd(id = state.widget.id)
            is AddWidgetFlowState.Configuring -> failAdd(id = state.widget.id)
        }
    }

    fun onBindResult(granted: Boolean) {
        val binding: AddWidgetFlowState.Binding = addFlow.value as? AddWidgetFlowState.Binding ?: return
        if (granted) {
            continueAfterBind(pending = binding.widget)
        } else {
            failAdd(id = binding.widget.id)
        }
    }

    fun onConfigureResult(ok: Boolean) {
        val configuring: AddWidgetFlowState.Configuring = addFlow.value as? AddWidgetFlowState.Configuring ?: return
        if (ok) {
            placeWidget(pending = configuring.widget)
        } else {
            failAdd(id = configuring.widget.id)
        }
    }

    fun removeWidget(widgetId: Long) {
        viewModelScope.launch {
            val appWidgetId: AppWidgetId = removeAppWidget(widgetId = widgetId) ?: return@launch
            menuTarget.value = null
            eventChannel.send(WidgetAreaEvent.RemoveHostView(appWidgetId = appWidgetId))
            eventChannel.send(WidgetAreaEvent.ShowMessage(message = R.string.widget_removed))
        }
    }

    fun reconfigure(widgetId: Long) {
        viewModelScope.launch {
            val widget: PlacedWidget.AppWidget = findAppWidget(widgetId = widgetId) ?: return@launch
            if (widget.availability != WidgetAvailability.Available) {
                return@launch
            }
            menuTarget.value = null
            eventChannel.send(WidgetAreaEvent.LaunchReconfigure(id = AppWidgetId(widget.appWidgetId)))
        }
    }

    fun onReconfigureResult(ok: Boolean) {
        if (!ok) {
            viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.ShowMessage(message = R.string.couldnt_open_widget_settings))
            }
        }
    }

    fun unpauseWorkProfile(widgetId: Long) {
        viewModelScope.launch {
            val widget: PlacedWidget.AppWidget = findAppWidget(widgetId = widgetId) ?: return@launch
            eventChannel.send(WidgetAreaEvent.RequestQuietModeOff(profileSerial = widget.profileSerial))
        }
    }

    private fun providerInfoFor(widget: PlacedWidget.AppWidget): WidgetProviderInfo? =
        appWidgetHost.providerInfo(provider = widget.provider, profileSerial = widget.profileSerial)

    private suspend fun findAppWidget(widgetId: Long): PlacedWidget.AppWidget? = repository.places.first()
        .asSequence()
        .flatMap { place -> place.widgets.asSequence() }
        .filterIsInstance<PlacedWidget.AppWidget>()
        .firstOrNull { widget -> widget.id == widgetId }

    fun startAddFromMenu() {
        val target: WidgetMenuTarget = menuTarget.value ?: return
        viewModelScope.launch {
            val places: List<WidgetPlace> = repository.places.first()
            val origin: AddWidgetOrigin = when (target) {
                WidgetMenuTarget.Header -> places.firstOrNull { place ->
                    place.widgets.any { widget -> widget is PlacedWidget.Header }
                }?.toAddOrigin() ?: AddWidgetOrigin(insertAt = InsertPosition.End)

                is WidgetMenuTarget.AppWidget -> places.firstOrNull { place ->
                    place.widgets.any { widget -> widget.id == target.widgetId }
                }?.toAddOrigin() ?: AddWidgetOrigin(insertAt = InsertPosition.End)

                WidgetMenuTarget.EmptyArea,
                WidgetMenuTarget.ClockStyle -> AddWidgetOrigin(insertAt = InsertPosition.End)
            }
            startAdd(origin = origin)
        }
    }

    private fun continueAfterBind(pending: PendingWidget) {
        if (pending.needsConfiguration) {
            addFlow.value = AddWidgetFlowState.Configuring(widget = pending)
            persistPending(pending = pending, phase = PENDING_PHASE_CONFIGURING)
            viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.LaunchConfigure(id = pending.id))
            }
        } else {
            placeWidget(pending = pending)
        }
    }

    private fun placeWidget(pending: PendingWidget) {
        viewModelScope.launch {
            val placed: Result<Long> = runCatching {
                repository.addAppWidget(
                    request = AddAppWidget(
                        appWidgetId = pending.id,
                        provider = pending.provider,
                        profileSerial = pending.profileSerial,
                        insertAt = pending.origin.insertAt,
                        defaultHeightDp = pending.defaultHeightDp
                    )
                )
            }
            if (placed.isFailure) {
                failAdd(id = pending.id)
                return@launch
            }
            clearPending()
            addFlow.value = AddWidgetFlowState.Idle
            if (pending.origin.returnToSettings) {
                eventChannel.send(WidgetAreaEvent.ReturnToSettings)
            }
        }
    }

    private fun WidgetPlace.toAddOrigin(): AddWidgetOrigin = AddWidgetOrigin(insertAt = InsertPosition.Below(placeId = id))

    private fun failAdd(id: AppWidgetId) {
        appWidgetHost.deleteId(id = id)
        clearPending()
        addFlow.value = AddWidgetFlowState.Idle
        viewModelScope.launch {
            eventChannel.send(WidgetAreaEvent.ShowMessage(message = R.string.couldnt_add_widget))
        }
    }

    private fun restorePendingWidget() {
        val pendingId: Int = savedStateHandle.get<Int>(PENDING_APP_WIDGET_ID_KEY) ?: return
        val providerKey: String? = savedStateHandle.get<String>(PENDING_PROVIDER_KEY)
        val provider: WidgetProvider? = providerKey?.let(WidgetProvider::fromKey)
        val profileSerial: Long? = savedStateHandle.get<Long>(PENDING_PROFILE_SERIAL_KEY)
        if (provider == null || profileSerial == null) {
            failAdd(id = AppWidgetId(pendingId))
            return
        }
        val pending: PendingWidget = PendingWidget(
            id = AppWidgetId(pendingId),
            provider = provider,
            profileSerial = profileSerial,
            needsConfiguration = savedStateHandle.get<Boolean>(PENDING_NEEDS_CONFIGURATION_KEY) ?: false,
            defaultHeightDp = savedStateHandle.get<Int>(PENDING_DEFAULT_HEIGHT_KEY) ?: 0,
            origin = AddWidgetOrigin(
                insertAt = savedStateHandle.get<Long>(PENDING_INSERT_BELOW_PLACE_ID_KEY)
                    ?.let { placeId -> InsertPosition.Below(placeId = placeId) }
                    ?: InsertPosition.End,
                returnToSettings = savedStateHandle.get<Boolean>(PENDING_RETURN_TO_SETTINGS_KEY) ?: false
            )
        )
        addFlowPendingIds.set(setOf(pending.id))
        addFlow.value = if (savedStateHandle.get<String>(PENDING_PHASE_KEY) == PENDING_PHASE_CONFIGURING) {
            AddWidgetFlowState.Configuring(widget = pending)
        } else {
            AddWidgetFlowState.Binding(widget = pending)
        }
    }

    private fun persistPending(pending: PendingWidget, phase: String) {
        savedStateHandle[PENDING_APP_WIDGET_ID_KEY] = pending.id.value
        savedStateHandle[PENDING_PROVIDER_KEY] = pending.provider.key
        savedStateHandle[PENDING_PROFILE_SERIAL_KEY] = pending.profileSerial
        savedStateHandle[PENDING_NEEDS_CONFIGURATION_KEY] = pending.needsConfiguration
        savedStateHandle[PENDING_DEFAULT_HEIGHT_KEY] = pending.defaultHeightDp
        savedStateHandle[PENDING_INSERT_BELOW_PLACE_ID_KEY] = (pending.origin.insertAt as? InsertPosition.Below)?.placeId
        savedStateHandle[PENDING_RETURN_TO_SETTINGS_KEY] = pending.origin.returnToSettings
        savedStateHandle[PENDING_PHASE_KEY] = phase
        addFlowPendingIds.set(setOf(pending.id))
    }

    private fun clearPending() {
        savedStateHandle.remove<Int>(PENDING_APP_WIDGET_ID_KEY)
        savedStateHandle.remove<String>(PENDING_PROVIDER_KEY)
        savedStateHandle.remove<Long>(PENDING_PROFILE_SERIAL_KEY)
        savedStateHandle.remove<Boolean>(PENDING_NEEDS_CONFIGURATION_KEY)
        savedStateHandle.remove<Int>(PENDING_DEFAULT_HEIGHT_KEY)
        savedStateHandle.remove<Long>(PENDING_INSERT_BELOW_PLACE_ID_KEY)
        savedStateHandle.remove<Boolean>(PENDING_RETURN_TO_SETTINGS_KEY)
        savedStateHandle.remove<String>(PENDING_PHASE_KEY)
        addFlowPendingIds.set(emptySet())
    }

    private fun pickerGroups(providers: List<WidgetProviderInfo>, query: String): List<PickerGroup> {
        val filter: String = query.trim()
        val collator: Collator = Collator.getInstance()
        return providers
            .filter { provider ->
                filter.isEmpty() ||
                    provider.appLabel.contains(filter, ignoreCase = true) ||
                    provider.label.contains(filter, ignoreCase = true)
            }
            .groupBy { provider -> provider.appPackageName to provider.profileSerial }
            .map { (_, widgets) ->
                val first: WidgetProviderInfo = widgets.first()
                PickerGroup(
                    appPackageName = first.appPackageName,
                    appLabel = first.appLabel,
                    appIcon = first.appIcon,
                    isWorkProfile = first.isWorkProfile,
                    widgets = widgets.sortedWith(compareBy(collator) { widget -> widget.label })
                )
            }
            .sortedWith(compareBy(collator) { group -> group.appLabel })
    }

    private fun WidgetMenuTarget?.toMenuState(
        places: List<WidgetPlace>,
        menuSettings: MenuSettings,
        reading: ClockReading,
        expandedChoice: WidgetMenuAction?
    ): WidgetMenuState = when (this) {
        WidgetMenuTarget.Header -> {
            if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
                WidgetMenuState.Closed
            } else {
                WidgetMenuState.HeaderMenu(
                    items = headerMenuItems(
                        settings = menuSettings.header.settings,
                        eventSettings = menuSettings.events,
                        expandedChoice = expandedChoice
                    )
                )
            }
        }

        WidgetMenuTarget.ClockStyle -> {
            if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
                WidgetMenuState.Closed
            } else {
                WidgetMenuState.ClockStyleChooser(
                    selected = menuSettings.header.settings.clockStyle,
                    previews = formatter.previews(reading = reading, settings = menuSettings.header.settings)
                )
            }
        }

        WidgetMenuTarget.EmptyArea -> WidgetMenuState.EmptyAreaMenu(items = emptyAreaMenuItems(places))

        is WidgetMenuTarget.AppWidget -> {
            val place: WidgetPlace? = places.firstOrNull { place -> place.widgets.any { widget -> widget.id == widgetId } }
            val widget: PlacedWidget.AppWidget? = place?.widgets
                ?.filterIsInstance<PlacedWidget.AppWidget>()
                ?.firstOrNull { widget -> widget.id == widgetId }
            if (widget == null) {
                WidgetMenuState.Closed
            } else {
                val info: WidgetProviderInfo? = providerInfoFor(widget)
                WidgetMenuState.AppWidgetMenu(
                    widgetId = widget.id,
                    appWidgetId = AppWidgetId(widget.appWidgetId),
                    icon = info.iconOr(provider = widget.provider),
                    label = info.labelOr(provider = widget.provider),
                    items = buildList {
                        val resizeBounds: ResizeBounds? = info?.let(ResizeBounds::from)
                        if (widget.availability == WidgetAvailability.Available && resizeBounds?.isResizable == true) {
                            add(WidgetMenuItem.Action(id = WidgetMenuAction.Resize))
                        }
                        val isReconfigurable: Boolean = info != null && info.isReconfigurable && info.hasConfigureActivity
                        if (widget.availability == WidgetAvailability.Available && isReconfigurable) {
                            add(WidgetMenuItem.Action(id = WidgetMenuAction.Reconfigure))
                        }
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget))
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.RemoveWidget))
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.EditWidgets))
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings))
                    }
                )
            }
        }

        null -> WidgetMenuState.Closed
    }

    private fun headerMenuItems(
        settings: HeaderSettings,
        eventSettings: EventSettingsState,
        expandedChoice: WidgetMenuAction?
    ): List<WidgetMenuItem> = listOf(
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
        WidgetMenuItem.Toggle(
            id = WidgetMenuAction.UpcomingEvents,
            checked = eventSettings.settings.enabled && eventSettings.hasPermission,
            enabled = true
        ),
        WidgetMenuItem.Action(id = WidgetMenuAction.ClockStyle),
        WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget),
        WidgetMenuItem.Action(id = WidgetMenuAction.EditWidgets),
        WidgetMenuItem.Action(id = WidgetMenuAction.RemoveHeader),
        WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings)
    )

    private fun emptyAreaMenuItems(places: List<WidgetPlace>): List<WidgetMenuItem> = buildList {
        add(WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget))
        if (places.none { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } }) {
            add(WidgetMenuItem.Action(id = WidgetMenuAction.RestoreHeader))
        }
        add(WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings))
    }

    private fun WidgetPlace.toModel(
        reading: ClockReading,
        settings: HeaderSettings,
        battery: BatteryStatus?,
        launchables: List<Launchable>,
        events: List<UpcomingEvent>,
        eventSettings: EventSettings,
        homeHeightDp: Int?
    ): WidgetPlaceModel? {
        val widgetModels: List<WidgetModel> = widgets.mapNotNull { widget ->
            widget.toModel(
                reading = reading,
                settings = settings,
                battery = battery,
                launchables = launchables,
                events = events,
                eventSettings = eventSettings
            )
        }
        val appWidget: PlacedWidget.AppWidget? = widgets.filterIsInstance<PlacedWidget.AppWidget>().firstOrNull()
        val info: WidgetProviderInfo? = appWidget?.let(::providerInfoFor)
        val resizeBounds: ResizeBounds? = info?.let(ResizeBounds::from)
        val storedHeightDp: Int? = heightDp
        val resolvedHeightDp: Int? = if (
            storedHeightDp != null &&
            resizeBounds != null &&
            resizeBounds.isResizable &&
            homeHeightDp != null
        ) {
            resizeBounds.clamp(heightDp = storedHeightDp, homeHeightDp = homeHeightDp)
        } else {
            storedHeightDp
        }
        return if (widgetModels.isEmpty()) {
            null
        } else {
            WidgetPlaceModel(id = id, heightDp = resolvedHeightDp, widgets = widgetModels)
        }
    }

    private fun PlacedWidget.toModel(
        reading: ClockReading,
        settings: HeaderSettings,
        battery: BatteryStatus?,
        launchables: List<Launchable>,
        events: List<UpcomingEvent>,
        eventSettings: EventSettings
    ): WidgetModel? = when (this) {
        is PlacedWidget.Header -> formatter.format(
            id = id,
            reading = reading,
            settings = settings,
            battery = battery,
            events = events,
            eventSettings = eventSettings
        ).withTapTargets(
            settings = settings,
            launchables = launchables,
            atEpochMillis = reading.instant.toEpochMilli()
        )

        is PlacedWidget.AppWidget -> {
            val info: WidgetProviderInfo? = providerInfoFor(this)
            AppWidgetModel(
                id = id,
                appWidgetId = appWidgetId,
                provider = provider.key,
                label = info.labelOr(provider = provider),
                icon = info.iconOr(provider = provider),
                minHeightDp = info?.minHeightDp ?: defaultHeightDp ?: 0,
                isWorkProfile = info?.isWorkProfile ?: false,
                availability = availability
            )
        }
    }
}

@StringRes
fun BatteryVisibility.labelResource(): Int = when (this) {
    BatteryVisibility.NEVER -> CoreUiR.string.battery_never
    BatteryVisibility.ALWAYS -> CoreUiR.string.battery_always
    BatteryVisibility.CHARGING_OR_LOW -> CoreUiR.string.battery_charging_or_low
}
