package feature.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
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
import core.data.widgets.HeaderRemoval
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlaceDraft
import core.data.widgets.PlacedWidget
import core.data.widgets.RemoveAppWidget
import core.data.widgets.ReplaceHeaderUndo
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetLifecycleReconciler
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.iconOr
import core.data.widgets.labelOr
import core.data.widgets.resizeBounds
import core.ui.R as CoreUiR
import java.text.Collator
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
private const val PAGE_SETTLE_DEBOUNCE_MS: Long = 300L
private const val PENDING_APP_WIDGET_ID_KEY: String = "pendingAppWidgetId"
private const val PENDING_PROVIDER_KEY: String = "pendingAppWidgetProvider"
private const val PENDING_PROFILE_SERIAL_KEY: String = "pendingAppWidgetProfileSerial"
private const val PENDING_NEEDS_CONFIGURATION_KEY: String = "pendingAppWidgetNeedsConfiguration"
private const val PENDING_DEFAULT_HEIGHT_KEY: String = "pendingAppWidgetDefaultHeight"
private const val PENDING_INSERT_BELOW_PLACE_ID_KEY: String = "pendingAppWidgetInsertBelowPlaceId"
private const val PENDING_INSERT_STACK_PLACE_ID_KEY: String = "pendingAppWidgetInsertStackPlaceId"
private const val PENDING_RETURN_TO_SETTINGS_KEY: String = "pendingAppWidgetReturnToSettings"
private const val PENDING_PHASE_KEY: String = "pendingAppWidgetPhase"
private const val PENDING_PHASE_BINDING: String = "binding"
private const val PENDING_PHASE_CONFIGURING: String = "configuring"

/** Where a picked App Widget is inserted and whether the flow returns to Settings. */
data class AddWidgetOrigin(val insertAt: InsertPosition, val returnToSettings: Boolean = false)

/** The two placements offered when adding from an existing Widget. */
enum class AddWidgetPlacement {
    Below,
    IntoStack
}

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

    /** The Add below / Add to stack choice shown before the picker. */
    data class ChoosingPlacement(val origin: AddWidgetOrigin, val canStack: Boolean) : AddWidgetFlowState

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
    ReplaceHeader,
    ExtendUnderRail,
    Resize,
    Reconfigure,
    RemoveHeader,
    RestoreHeader,
    RemoveWidget,
    RemoveFromStack,
    EditWidgets,
    WidgetSettings
}

sealed interface UndoAction {
    data class RemoveHeader(val removal: HeaderRemoval) : UndoAction

    data class ReplaceHeader(val undo: ReplaceHeaderUndo) : UndoAction
}

sealed interface WidgetAreaEvent {
    data class ShowUndo(val action: UndoAction) : WidgetAreaEvent

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
    data class Header(val widgetId: Long? = null) : WidgetMenuTarget

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
    val eventSettings: EventSettings,
    val widgetAreaSettings: WidgetAreaSettings
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

    private val headerHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val widgetAreaListState: MutableStateFlow<LazyListState> = MutableStateFlow(LazyListState())
    private val pageSettleJobs: MutableMap<Long, Job> = mutableMapOf()

    private val resizeController: WidgetResizeController = WidgetResizeController(
        repository = repository,
        appWidgetHost = appWidgetHost,
        settingsRepository = settingsRepository,
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
                editDraft.update { draft -> draft?.reconciledWith(places) }
            }
        }
        viewModelScope.launch {
            settingsRepository.widgetAreaSettings
                .map { settings -> settings.heightLimitEnabled }
                .distinctUntilChanged()
                .drop(1)
                .collect { widgetAreaListState.value = LazyListState() }
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

    private data class WidgetAreaContentBase(
        val places: List<WidgetPlace>,
        val reading: ClockReading,
        val settingsAndBattery: HeaderSettingsAndBattery,
        val launchables: List<Launchable>,
        val events: EventData
    )

    private val widgetAreaContentBase: Flow<WidgetAreaContentBase> = combine(
        repository.places,
        clockSource.readings,
        settingsAndBattery,
        launchableRepository.launchableState,
        eventData
    ) { places, reading, settingsAndBattery, launchableState, events ->
        WidgetAreaContentBase(
            places = places,
            reading = reading,
            settingsAndBattery = settingsAndBattery,
            launchables = launchableState.launchables,
            events = events
        )
    }

    private val widgetAreaContent: Flow<WidgetAreaContent> = combine(
        widgetAreaContentBase,
        settingsRepository.widgetAreaSettings
    ) { base, widgetAreaSettings ->
        WidgetAreaContent(
            places = base.places,
            reading = base.reading,
            settings = base.settingsAndBattery.settings,
            battery = base.settingsAndBattery.battery,
            launchables = base.launchables,
            events = base.events.events,
            eventSettings = base.events.settings,
            widgetAreaSettings = widgetAreaSettings
        )
    }

    val widgetAreaState: StateFlow<WidgetAreaState> = combine(
        widgetAreaContent,
        resizeController.homeHeightDp,
        editDraft,
        headerHeightDp,
        widgetAreaListState
    ) { content, homeHeightDp, draft, headerHeight, listState ->
        val orderedPlaces: List<WidgetPlace> = draft?.toPlaces(content.places) ?: content.places
        WidgetAreaState(
            places = orderedPlaces.mapNotNull { place ->
                place.toModel(
                    reading = content.reading,
                    settings = content.settings,
                    battery = content.battery,
                    launchables = content.launchables,
                    events = content.events,
                    eventSettings = content.eventSettings,
                    homeHeightDp = homeHeightDp,
                    headerHeightDp = headerHeight
                )
            },
            hasWidgets = orderedPlaces.isNotEmpty(),
            isEditing = draft != null,
            stackIndicatorVisible = content.widgetAreaSettings.stackIndicatorVisible,
            maxStackPages = content.widgetAreaSettings.maxStackPages,
            layout = content.widgetAreaSettings.toLayout(),
            listState = listState
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

    private data class MenuPlaceState(
        val places: List<WidgetPlace>,
        val mode: HomeEditMode,
        val reading: ClockReading,
        val headerHeightDp: Int?
    )

    private val menuPlaceState: Flow<MenuPlaceState> = combine(
        repository.places,
        homeEditState.mode,
        clockSource.readings,
        headerHeightDp
    ) { places, mode, reading, headerHeight ->
        MenuPlaceState(places = places, mode = mode, reading = reading, headerHeightDp = headerHeight)
    }

    val menuState: StateFlow<WidgetMenuState> = combine(
        menuContext,
        menuSettings,
        menuPlaceState
    ) { context, menuSettings, placeState ->
        if (placeState.mode != HomeEditMode.None) {
            WidgetMenuState.Closed
        } else {
            context.target.toMenuState(
                places = placeState.places,
                menuSettings = menuSettings,
                reading = placeState.reading,
                expandedChoice = context.expandedChoice,
                headerHeightDp = placeState.headerHeightDp
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetMenuState.Closed
    )

    private val eventChannel: Channel<WidgetAreaEvent> = Channel(Channel.BUFFERED)
    val events: Flow<WidgetAreaEvent> = eventChannel.receiveAsFlow()

    fun openHeaderMenu(widgetId: Long? = null) {
        if (homeEditState.mode.value == HomeEditMode.None) {
            menuTarget.value = WidgetMenuTarget.Header(widgetId = widgetId)
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

    fun setHeaderHeight(heightDp: Int) {
        if (heightDp > 0) {
            headerHeightDp.value = heightDp
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

    fun removeHeader(widgetId: Long? = null) {
        viewModelScope.launch {
            val removal: HeaderRemoval = if (widgetId == null) {
                repository.removeHeader()
            } else {
                repository.removeHeaderPage(widgetId = widgetId)
            } ?: return@launch
            menuTarget.value = null
            eventChannel.send(WidgetAreaEvent.ShowUndo(action = UndoAction.RemoveHeader(removal = removal)))
        }
    }

    fun restoreHeader() {
        viewModelScope.launch {
            repository.restoreHeader(position = 0)
            menuTarget.value = null
        }
    }

    fun replaceHeader(widgetId: Long) {
        viewModelScope.launch {
            val undo: ReplaceHeaderUndo = repository.replaceHeader(widgetId = widgetId) ?: return@launch
            clampStackHeight(placeId = undo.headerPlaceId)
            menuTarget.value = null
            eventChannel.send(WidgetAreaEvent.ShowUndo(action = UndoAction.ReplaceHeader(undo = undo)))
        }
    }

    fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean) {
        viewModelScope.launch {
            repository.setExtendsUnderRail(widgetId = widgetId, extendsUnderRail = extendsUnderRail)
        }
    }

    fun undo(action: UndoAction) {
        viewModelScope.launch {
            when (action) {
                is UndoAction.RemoveHeader -> repository.restoreHeader(
                    position = action.removal.position,
                    placeId = action.removal.placeId,
                    pagePosition = action.removal.pagePosition
                )

                is UndoAction.ReplaceHeader -> repository.undoReplaceHeader(undo = action.undo)
            }
        }
    }

    fun editWidgets() {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = null
        viewModelScope.launch {
            val places: List<WidgetPlace> = repository.places.first()
            if (homeEditState.tryEnter(HomeEditMode.Widgets)) {
                editDraft.value = WidgetEditDraft(
                    places = places.map { place ->
                        PlaceDraft(placeId = place.id, widgetIds = place.widgets.map { widget -> widget.id })
                    }
                )
            }
        }
    }

    fun moveWidgetPlace(fromIndex: Int, toIndex: Int) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        if (fromIndex !in draft.places.indices || toIndex !in draft.places.indices) {
            Timber.w("Ignoring Widget place move from %d to %d outside %d places", fromIndex, toIndex, draft.places.size)
            return
        }
        if (fromIndex == toIndex) {
            return
        }
        editDraft.value = draft.copy(
            places = draft.places.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        )
    }

    fun removeWidgetPlaceInEdit(placeId: Long) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        val place: PlaceDraft = draft.places.firstOrNull { candidate -> candidate.placeId == placeId } ?: return
        editDraft.value = draft.copy(
            places = draft.places - place,
            removedWidgetIds = draft.removedWidgetIds + place.widgetIds,
            expandedPlaceIds = draft.expandedPlaceIds - placeId
        )
    }

    fun toggleStackExpanded(placeId: Long) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        val place: PlaceDraft = draft.places.firstOrNull { candidate -> candidate.placeId == placeId } ?: return
        if (place.widgetIds.size < 2) {
            return
        }
        editDraft.value = draft.copy(
            expandedPlaceIds = if (placeId in draft.expandedPlaceIds) {
                draft.expandedPlaceIds - placeId
            } else {
                draft.expandedPlaceIds + placeId
            }
        )
    }

    fun movePage(placeId: Long, fromIndex: Int, toIndex: Int) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        val place: PlaceDraft = draft.places.firstOrNull { candidate -> candidate.placeId == placeId } ?: return
        if (fromIndex !in place.widgetIds.indices || toIndex !in place.widgetIds.indices || fromIndex == toIndex) {
            return
        }
        val reordered: List<Long> = place.widgetIds.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        editDraft.value = draft.copy(
            places = draft.places.map { candidate ->
                if (candidate.placeId == placeId) candidate.copy(widgetIds = reordered) else candidate
            }
        )
    }

    fun removePageInEdit(placeId: Long, widgetId: Long) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        val place: PlaceDraft = draft.places.firstOrNull { candidate -> candidate.placeId == placeId } ?: return
        if (widgetId !in place.widgetIds) {
            return
        }
        val remaining: List<Long> = place.widgetIds - widgetId
        val places: List<PlaceDraft> = if (remaining.isEmpty()) {
            draft.places - place
        } else {
            draft.places.map { candidate ->
                if (candidate.placeId == placeId) candidate.copy(widgetIds = remaining) else candidate
            }
        }
        editDraft.value = draft.copy(
            places = places,
            removedWidgetIds = draft.removedWidgetIds + widgetId,
            expandedPlaceIds = if (remaining.size < 2) draft.expandedPlaceIds - placeId else draft.expandedPlaceIds
        )
    }

    fun mergePlace(sourcePlaceId: Long, targetPlaceId: Long) {
        if (sourcePlaceId == targetPlaceId) {
            return
        }
        viewModelScope.launch {
            val maxStackPages: Int = settingsRepository.widgetAreaSettings.first().maxStackPages
            val draft: WidgetEditDraft = editDraft.value ?: return@launch
            val source: PlaceDraft = draft.places.firstOrNull { place -> place.placeId == sourcePlaceId } ?: return@launch
            val target: PlaceDraft = draft.places.firstOrNull { place -> place.placeId == targetPlaceId } ?: return@launch
            if (source.widgetIds.size != 1 || target.widgetIds.size + source.widgetIds.size > maxStackPages) {
                eventChannel.send(WidgetAreaEvent.ShowMessage(message = R.string.stack_full))
                return@launch
            }
            editDraft.value = draft.copy(
                places = draft.places
                    .map { place ->
                        if (place.placeId == targetPlaceId) {
                            place.copy(widgetIds = place.widgetIds + source.widgetIds)
                        } else {
                            place
                        }
                    }
                    .filterNot { place -> place.placeId == sourcePlaceId },
                expandedPlaceIds = draft.expandedPlaceIds + targetPlaceId
            )
        }
    }

    fun splitPage(placeId: Long, widgetId: Long, toIndex: Int) {
        val draft: WidgetEditDraft = editDraft.value ?: return
        val place: PlaceDraft = draft.places.firstOrNull { candidate -> candidate.placeId == placeId } ?: return
        if (widgetId !in place.widgetIds) {
            return
        }
        val remaining: PlaceDraft = place.copy(widgetIds = place.widgetIds - widgetId)
        val places: MutableList<PlaceDraft> = draft.places.toMutableList()
        val sourceIndex: Int = places.indexOf(place)
        if (sourceIndex < 0) {
            return
        }
        places[sourceIndex] = remaining
        val insertionIndex: Int = toIndex.coerceIn(0, places.size)
        places.add(insertionIndex, PlaceDraft(placeId = null, widgetIds = listOf(widgetId)))
        editDraft.value = draft.copy(
            places = places.filter { candidate -> candidate.widgetIds.isNotEmpty() },
            expandedPlaceIds = draft.expandedPlaceIds - placeId
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
                    edit = WidgetAreaEdit(
                        places = draft.places.filter { place -> place.widgetIds.isNotEmpty() },
                        removedWidgetIds = draft.removedWidgetIds
                    )
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
            if (resizeController.startResize(placeId = placeId, headerHeightDp = headerHeightDp.value)) {
                menuTarget.value = null
            }
        }
    }

    private fun startResizeForWidget(widgetId: Long) {
        viewModelScope.launch {
            if (resizeController.startResizeForWidget(widgetId = widgetId, headerHeightDp = headerHeightDp.value)) {
                menuTarget.value = null
            }
        }
    }

    fun dragResize(deltaDp: Float) = resizeController.dragResize(deltaDp = deltaDp)

    fun stepResize(direction: ResizeStep) = resizeController.stepResize(direction = direction)

    fun resetResize() = resizeController.resetResize()

    fun finishResize() = resizeController.finishResize()

    fun onBack(): Boolean = finishEditingOrResize(fallback = resizeController::onBack)

    fun onHomeIntent(): Boolean {
        widgetAreaListState.value = LazyListState()
        return finishEditingOrResize(fallback = resizeController::onHomeIntent)
    }

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

            WidgetMenuAction.ReplaceHeader -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                replaceHeader(widgetId = target.widgetId)
            }

            WidgetMenuAction.Resize -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                startResizeForWidget(widgetId = target.widgetId)
            }

            WidgetMenuAction.Reconfigure -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                reconfigure(widgetId = target.widgetId)
            }

            WidgetMenuAction.RemoveWidget -> (menuTarget.value as? WidgetMenuTarget.AppWidget)?.let { target ->
                removeWidget(widgetId = target.widgetId)
            }

            WidgetMenuAction.RemoveFromStack -> when (val target = menuTarget.value) {
                is WidgetMenuTarget.AppWidget -> removeWidget(widgetId = target.widgetId)
                is WidgetMenuTarget.Header -> removeHeader(widgetId = target.widgetId)
                else -> Unit
            }

            WidgetMenuAction.ClockStyle -> openClockStyleChooser()

            WidgetMenuAction.RemoveHeader -> removeHeader()

            WidgetMenuAction.RestoreHeader -> restoreHeader()

            WidgetMenuAction.EditWidgets -> editWidgets()

            WidgetMenuAction.WidgetSettings -> openWidgetSettings()

            WidgetMenuAction.Clock,
            WidgetMenuAction.Date,
            WidgetMenuAction.Battery,
            WidgetMenuAction.UpcomingEvents,
            WidgetMenuAction.ExtendUnderRail -> Unit
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

    fun choosePlacement(placement: AddWidgetPlacement) {
        val choosing: AddWidgetFlowState.ChoosingPlacement = addFlow.value as? AddWidgetFlowState.ChoosingPlacement ?: return
        val origin: AddWidgetOrigin = when (placement) {
            AddWidgetPlacement.Below -> choosing.origin

            AddWidgetPlacement.IntoStack -> {
                val placeId: Long? = (choosing.origin.insertAt as? InsertPosition.Below)?.placeId
                if (choosing.canStack && placeId != null) {
                    choosing.origin.copy(insertAt = InsertPosition.IntoStack(placeId = placeId))
                } else {
                    choosing.origin
                }
            }
        }
        startAdd(origin = origin)
    }

    fun onPageSettled(placeId: Long, page: Int) {
        pageSettleJobs[placeId]?.cancel()
        pageSettleJobs[placeId] = viewModelScope.launch {
            delay(PAGE_SETTLE_DEBOUNCE_MS)
            repository.setCurrentPage(placeId = placeId, page = page)
            pageSettleJobs.remove(placeId)
        }
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
            is AddWidgetFlowState.ChoosingPlacement -> addFlow.value = AddWidgetFlowState.Idle
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
            val place: WidgetPlace? = when (target) {
                is WidgetMenuTarget.Header -> places.headerPlace(widgetId = target.widgetId)

                is WidgetMenuTarget.AppWidget -> places.firstOrNull { candidate ->
                    candidate.widgets.any { widget -> widget.id == target.widgetId }
                }

                WidgetMenuTarget.EmptyArea,
                WidgetMenuTarget.ClockStyle -> null
            }
            if (place == null) {
                startAdd(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
                return@launch
            }
            val origin: AddWidgetOrigin = AddWidgetOrigin(insertAt = InsertPosition.Below(placeId = place.id))
            val maxStackPages: Int = settingsRepository.widgetAreaSettings.first().maxStackPages
            if (place.widgets.size < maxStackPages) {
                addFlow.value = AddWidgetFlowState.ChoosingPlacement(origin = origin, canStack = true)
            } else {
                startAdd(origin = origin)
            }
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
            val stackPlaceId: Long? = (pending.origin.insertAt as? InsertPosition.IntoStack)?.placeId
            if (stackPlaceId != null) {
                clampStackHeight(placeId = stackPlaceId)
                reportStackSize(placeId = stackPlaceId)
            }
            clearPending()
            addFlow.value = AddWidgetFlowState.Idle
            if (pending.origin.returnToSettings) {
                eventChannel.send(WidgetAreaEvent.ReturnToSettings)
            }
        }
    }

    private suspend fun clampStackHeight(placeId: Long) {
        val place: WidgetPlace = repository.places.first().firstOrNull { candidate -> candidate.id == placeId } ?: return
        val bounds: ResizeBounds = ResizeBounds.stackBounds(
            place.widgets.map { widget ->
                widget.resizeBounds(headerHeightDp.value, ::providerInfoFor)
            }
        )
        val storedHeight: Int = place.heightDp ?: return
        val homeHeight: Int = resizeController.homeHeightDp.value ?: Int.MAX_VALUE
        val clamped: Int = bounds.clamp(heightDp = storedHeight, homeHeightDp = homeHeight)
        if (clamped != storedHeight) {
            repository.setPlaceHeight(placeId = placeId, heightDp = clamped)
        }
    }

    private suspend fun reportStackSize(placeId: Long) {
        val place: WidgetPlace = repository.places.first().firstOrNull { candidate -> candidate.id == placeId } ?: return
        val appWidgetIds: List<AppWidgetId> = place.widgets
            .filterIsInstance<PlacedWidget.AppWidget>()
            .map { widget -> AppWidgetId(widget.appWidgetId) }
        val defaults: List<Int> = place.widgets.mapNotNull { widget ->
            (widget as? PlacedWidget.AppWidget)?.let { appWidget ->
                providerInfoFor(appWidget)?.minHeightDp ?: appWidget.defaultHeightDp
            }
        }
        val heightDp: Int = place.heightDp ?: defaults.maxOrNull() ?: return
        val widthDp: Int = resizeController.widgetAreaWidth
        if (appWidgetIds.isEmpty() || widthDp <= 0) {
            return
        }
        val indicatorVisible: Boolean = place.isStack &&
            settingsRepository.widgetAreaSettings.first().stackIndicatorVisible
        val pageHeightDp: Int = (heightDp - if (indicatorVisible) ResizeBounds.STACK_INDICATOR_HEIGHT_DP else 0).coerceAtLeast(1)
        eventChannel.send(
            WidgetAreaEvent.ReportWidgetSize(
                appWidgetIds = appWidgetIds,
                widthDp = widthDp,
                heightDp = pageHeightDp
            )
        )
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
        val stackPlaceId: Long? = savedStateHandle.get<Long>(PENDING_INSERT_STACK_PLACE_ID_KEY)
        val belowPlaceId: Long? = savedStateHandle.get<Long>(PENDING_INSERT_BELOW_PLACE_ID_KEY)
        val insertAt: InsertPosition = when {
            stackPlaceId != null -> InsertPosition.IntoStack(placeId = stackPlaceId)
            belowPlaceId != null -> InsertPosition.Below(placeId = belowPlaceId)
            else -> InsertPosition.End
        }
        val pending: PendingWidget = PendingWidget(
            id = AppWidgetId(pendingId),
            provider = provider,
            profileSerial = profileSerial,
            needsConfiguration = savedStateHandle.get<Boolean>(PENDING_NEEDS_CONFIGURATION_KEY) ?: false,
            defaultHeightDp = savedStateHandle.get<Int>(PENDING_DEFAULT_HEIGHT_KEY) ?: 0,
            origin = AddWidgetOrigin(
                insertAt = insertAt,
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
        savedStateHandle[PENDING_INSERT_STACK_PLACE_ID_KEY] = (pending.origin.insertAt as? InsertPosition.IntoStack)?.placeId
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
        savedStateHandle.remove<Long>(PENDING_INSERT_STACK_PLACE_ID_KEY)
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
        expandedChoice: WidgetMenuAction?,
        headerHeightDp: Int?
    ): WidgetMenuState = when (this) {
        is WidgetMenuTarget.Header -> {
            val place: WidgetPlace? = places.headerPlace(widgetId = widgetId)
            if (place == null) {
                WidgetMenuState.Closed
            } else {
                WidgetMenuState.HeaderMenu(
                    items = headerMenuItems(
                        settings = menuSettings.header.settings,
                        eventSettings = menuSettings.events,
                        expandedChoice = expandedChoice,
                        isInStack = place.isStack
                    )
                )
            }
        }

        WidgetMenuTarget.ClockStyle -> {
            if (!places.hasHeader()) {
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
            val place: WidgetPlace? = places.firstOrNull { candidate -> candidate.widgets.any { widget -> widget.id == widgetId } }
            val widget: PlacedWidget.AppWidget? = place?.widgets
                ?.filterIsInstance<PlacedWidget.AppWidget>()
                ?.firstOrNull { candidate -> candidate.id == widgetId }
            if (widget == null || place == null) {
                WidgetMenuState.Closed
            } else {
                val info: WidgetProviderInfo? = providerInfoFor(widget)
                val resizeBounds: ResizeBounds? = if (place.isStack) {
                    ResizeBounds.stackBounds(place.widgets.map { page -> page.resizeBounds(headerHeightDp, ::providerInfoFor) })
                } else {
                    info?.let(ResizeBounds::from)
                }
                val headerPlace: WidgetPlace? = places.headerPlace()
                WidgetMenuState.AppWidgetMenu(
                    widgetId = widget.id,
                    appWidgetId = AppWidgetId(widget.appWidgetId),
                    icon = info.iconOr(provider = widget.provider),
                    label = info.labelOr(provider = widget.provider),
                    items = buildList {
                        if (widget.availability == WidgetAvailability.Available && resizeBounds?.isResizable == true) {
                            add(WidgetMenuItem.Action(id = WidgetMenuAction.Resize))
                        }
                        val isReconfigurable: Boolean = info != null && info.isReconfigurable && info.hasConfigureActivity
                        if (widget.availability == WidgetAvailability.Available && isReconfigurable) {
                            add(WidgetMenuItem.Action(id = WidgetMenuAction.Reconfigure))
                        }
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget))
                        if (headerPlace != null && headerPlace.id != place.id) {
                            add(WidgetMenuItem.Action(id = WidgetMenuAction.ReplaceHeader))
                        }
                        add(
                            WidgetMenuItem.Toggle(
                                id = WidgetMenuAction.ExtendUnderRail,
                                checked = widget.extendsUnderRail,
                                enabled = !place.isStack,
                                supportingText = if (place.isStack) {
                                    R.string.not_available_in_stack
                                } else {
                                    R.string.extend_under_rail_supporting_text
                                }
                            )
                        )
                        add(
                            WidgetMenuItem.Action(
                                id = if (place.isStack) WidgetMenuAction.RemoveFromStack else WidgetMenuAction.RemoveWidget
                            )
                        )
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
        expandedChoice: WidgetMenuAction?,
        isInStack: Boolean
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
        WidgetMenuItem.Action(
            id = if (isInStack) WidgetMenuAction.RemoveFromStack else WidgetMenuAction.RemoveHeader
        ),
        WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings)
    )

    private fun emptyAreaMenuItems(places: List<WidgetPlace>): List<WidgetMenuItem> = buildList {
        add(WidgetMenuItem.Action(id = WidgetMenuAction.AddWidget))
        if (!places.hasHeader()) {
            add(WidgetMenuItem.Action(id = WidgetMenuAction.RestoreHeader))
        }
        add(WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings))
    }

    private fun List<WidgetPlace>.headerPlace(widgetId: Long? = null): WidgetPlace? = firstOrNull { place ->
        place.widgets.any { widget -> widget is PlacedWidget.Header && (widgetId == null || widget.id == widgetId) }
    }

    private fun List<WidgetPlace>.hasHeader(): Boolean = headerPlace() != null

    private fun WidgetPlace.toModel(
        reading: ClockReading,
        settings: HeaderSettings,
        battery: BatteryStatus?,
        launchables: List<Launchable>,
        events: List<UpcomingEvent>,
        eventSettings: EventSettings,
        homeHeightDp: Int?,
        headerHeightDp: Int?
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
        if (widgetModels.isEmpty()) {
            return null
        }
        val pageBounds: List<ResizeBounds> = widgets.map { widget -> widget.resizeBounds(headerHeightDp, ::providerInfoFor) }
        val bounds: ResizeBounds? = if (isStack) {
            ResizeBounds.stackBounds(pageBounds)
        } else {
            pageBounds.firstOrNull()
        }
        val defaults: List<Int> = widgets.mapNotNull { widget ->
            (widget as? PlacedWidget.AppWidget)?.let { appWidget ->
                providerInfoFor(appWidget)?.minHeightDp ?: appWidget.defaultHeightDp
            }
        }
        val stackDefaultHeight: Int = defaults.maxOrNull() ?: bounds?.minDp ?: ResizeBounds.MIN_HEIGHT_DP
        val storedHeightDp: Int? = heightDp
        val resolvedHeightDp: Int? = when {
            !isStack && widgets.single() is PlacedWidget.Header -> storedHeightDp

            isStack -> {
                val base: Int = storedHeightDp ?: stackDefaultHeight
                if (homeHeightDp != null) bounds?.clamp(heightDp = base, homeHeightDp = homeHeightDp) else base
            }

            storedHeightDp != null && bounds != null && bounds.isResizable && homeHeightDp != null ->
                bounds.clamp(heightDp = storedHeightDp, homeHeightDp = homeHeightDp)

            else -> storedHeightDp
        }
        return WidgetPlaceModel(
            id = id,
            heightDp = resolvedHeightDp,
            currentPage = currentPage.coerceIn(0, widgetModels.lastIndex),
            widgets = widgetModels,
            widthMode = widthMode
        )
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
