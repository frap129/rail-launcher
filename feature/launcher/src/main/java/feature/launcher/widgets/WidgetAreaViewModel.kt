package feature.launcher.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import core.data.calendar.UpcomingEventsOutcome
import core.data.calendar.effectiveLookaheadHours
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.media.IMediaSessionSource
import core.data.media.MediaCommand
import core.data.media.MediaPlayerSelector
import core.data.media.MediaSessionSnapshot
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.EventSettings
import core.data.prefs.HeaderSettings
import core.data.prefs.canSetBatteryVisibility
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.BindRequest
import core.data.widgets.HeaderRemoval
import core.data.widgets.HomeHeightState
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IHeaderSettingsActions
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetLifecycleReconciler
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.PlaceDraft
import core.data.widgets.PlacedWidget
import core.data.widgets.ReplaceHeaderUndo
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetAreaEdit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.iconOr
import core.data.widgets.labelOr
import core.data.widgets.resizeBounds
import core.ui.R as CoreUiR
import core.widgets.AppWidgetModel
import core.widgets.HeaderFormatter
import core.widgets.HeaderModel
import core.widgets.IHeaderTapTargetResolver
import core.widgets.R as CoreWidgetsR
import core.widgets.WidgetModel
import core.widgets.labelResource
import feature.launcher.R
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

/** One step of the Move Widget Area adjuster. */
enum class MoveWidgetAreaStep {
    Up,
    Down
}

/** The live Move Widget Area adjuster state. */
data class MoveWidgetAreaState(val draftOffsetDp: Int, val maxOffsetDp: Int, val canMoveUp: Boolean, val canMoveDown: Boolean)

/** The long-press menu currently shown on Home. */
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

    data class MediaPlayerMenu(val icon: Icon, val label: String, val items: List<WidgetMenuItem>) : WidgetMenuState

    data class ClockStyleChooser(val selected: ClockStyle, val previews: List<HeaderModel>) : WidgetMenuState
}

sealed interface WidgetMenuItem {
    val id: WidgetMenuAction

    data class Toggle(
        override val id: WidgetMenuAction,
        val checked: Boolean,
        val enabled: Boolean,
        val supportingText: Int? = null,
        val rowOpensAction: Boolean = false
    ) : WidgetMenuItem

    data class Action(override val id: WidgetMenuAction, val labelArg: String? = null) : WidgetMenuItem

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

data class WidgetMenuChoiceOption(val key: WidgetMenuChoiceKey, @StringRes val label: Int, val enabled: Boolean = true)

enum class WidgetMenuAction {
    Clock,
    Date,
    Battery,
    UpcomingEvents,
    ClockStyle,
    AddWidget,
    MoveWidgetArea,
    ReplaceHeader,
    ExtendUnderRail,
    Resize,
    Reconfigure,
    RemoveHeader,
    RestoreHeader,
    RemoveWidget,
    RemoveFromStack,
    EditWidgets,
    WidgetSettings,
    MediaPlayer,
    OpenMediaPlayer,
    ExcludeMediaApp,
    MediaPlayerSettings
}

sealed interface UndoAction {
    data class RemoveHeader(val removal: HeaderRemoval) : UndoAction

    data class ReplaceHeader(val undo: ReplaceHeaderUndo) : UndoAction

    data class IncludeMediaApp(val packageName: String, val appLabel: String = packageName) : UndoAction
}

sealed interface WidgetAreaEvent {
    data class ShowUndo(val action: UndoAction) : WidgetAreaEvent

    data object RequestCalendarPermission : WidgetAreaEvent

    data class ShowCalendarPermissionDenied(val permanentlyDenied: Boolean) : WidgetAreaEvent

    data object NavigateToWidgetSettings : WidgetAreaEvent

    data object NavigateToMediaPlayerSettings : WidgetAreaEvent

    data object RequestNotificationAccess : WidgetAreaEvent

    data class LaunchBind(val request: BindRequest) : WidgetAreaEvent

    data class LaunchConfigure(val id: AppWidgetId) : WidgetAreaEvent

    data class LaunchReconfigure(val id: AppWidgetId) : WidgetAreaEvent

    data class RequestQuietModeOff(val profileSerial: Long) : WidgetAreaEvent

    data class ShowMessage(@StringRes val message: Int, val formatArg: String? = null) : WidgetAreaEvent

    data class RemoveHostView(val appWidgetId: AppWidgetId) : WidgetAreaEvent

    data class ReportWidgetSize(val appWidgetIds: List<AppWidgetId>, val widthDp: Int, val heightDp: Int) : WidgetAreaEvent

    data object ReturnToSettings : WidgetAreaEvent
}

private sealed interface WidgetMenuTarget {
    data class Header(val widgetId: Long? = null) : WidgetMenuTarget

    data object EmptyArea : WidgetMenuTarget

    data object ClockStyle : WidgetMenuTarget

    data class AppWidget(val widgetId: Long) : WidgetMenuTarget

    data object MediaPlayer : WidgetMenuTarget
}

private data class HeaderSettingsAndBattery(val settings: HeaderSettings, val battery: BatteryStatus?)

private data class EventSettingsState(val settings: EventSettings, val hasPermission: Boolean)

private data class EventData(val events: List<UpcomingEvent>, val settings: EventSettings)

private data class MenuSettings(
    val header: HeaderSettingsAndBattery,
    val events: EventSettingsState,
    val mediaPlayer: MediaPlayerSettings,
    val notificationAccess: Boolean
)

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
    batterySource: IBatterySource,
    private val removeAppWidget: IRemoveAppWidget,
    private val addFlowController: IWidgetAddController,
    private val widgetLifecycleReconciler: IWidgetLifecycleReconciler,
    private val homeHeightState: HomeHeightState,
    private val mediaSessionSource: IMediaSessionSource,
    private val headerSettingsActions: IHeaderSettingsActions,
    private val tapTargetResolver: IHeaderTapTargetResolver,
    private val resizeController: IWidgetResizeController
) : ViewModel() {
    val addFlowState: StateFlow<AddWidgetFlowState> = addFlowController.state

    private val eventChannel: Channel<WidgetAreaEvent> = Channel(Channel.BUFFERED)
    val events: Flow<WidgetAreaEvent> = eventChannel.receiveAsFlow()

    private val editDraft: MutableStateFlow<WidgetEditDraft?> = MutableStateFlow(null)
    val editState: StateFlow<WidgetEditDraft?> = editDraft.asStateFlow()
    private val editMutex: Mutex = Mutex()

    private val headerHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val widgetAreaListState: MutableStateFlow<LazyListState> = MutableStateFlow(LazyListState())
    private val pageSettleJobs: MutableMap<Long, Job> = mutableMapOf()

    val resizeState: StateFlow<ResizeState?> = resizeController.resizeState

    private val moveDraft: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val animateMoveOffset: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val moveState: StateFlow<MoveWidgetAreaState?> = combine(moveDraft, resizeController.homeHeightDp) { draft, homeHeightDp ->
        draft?.let { offsetDp -> toMoveState(offsetDp = offsetDp, homeHeightDp = homeHeightDp) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = null
    )

    init {
        viewModelScope.launch {
            addFlowController.events.collect { event -> eventChannel.send(event) }
        }
        viewModelScope.launch {
            addFlowController.restore()
        }
        viewModelScope.launch {
            appWidgetHost.providers.collect { providerList ->
                addFlowController.updateProviders(providers = providerList)
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
                resizeController.onPlacesChanged(places = places)
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
                calendarEventSource.events(lookaheadHours = state.settings.effectiveLookaheadHours())
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

    private data class HomePresentationState(
        val editDraft: WidgetEditDraft?,
        val headerHeightDp: Int?,
        val listState: LazyListState,
        val moveDraft: Int?,
        val animateMoveOffset: Boolean
    )

    private val homePresentationState: Flow<HomePresentationState> = combine(
        editDraft,
        headerHeightDp,
        widgetAreaListState,
        moveDraft,
        animateMoveOffset
    ) { draft, headerHeight, listState, move, animate ->
        HomePresentationState(
            editDraft = draft,
            headerHeightDp = headerHeight,
            listState = listState,
            moveDraft = move,
            animateMoveOffset = animate
        )
    }

    val widgetAreaState: StateFlow<WidgetAreaState> = combine(
        widgetAreaContent,
        resizeController.homeHeightDp,
        homePresentationState,
        settingsRepository.mediaPlayerSettings
    ) { content, homeHeightDp, presentation, mediaPlayerSettings ->
        val draft: WidgetEditDraft? = presentation.editDraft
        val orderedPlaces: List<WidgetPlace> = draft?.toPlaces(content.places) ?: content.places
        val requestedOffsetDp: Int = presentation.moveDraft ?: content.widgetAreaSettings.topOffsetDp
        val topOffsetDp: Int = clampOffset(offsetDp = requestedOffsetDp, homeHeightDp = homeHeightDp)
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
                    headerHeightDp = presentation.headerHeightDp
                )
            },
            hasWidgets = orderedPlaces.isNotEmpty(),
            isEditing = draft != null,
            stackIndicatorVisible = content.widgetAreaSettings.stackIndicatorVisible,
            maxStackPages = content.widgetAreaSettings.maxStackPages,
            layout = content.widgetAreaSettings.toLayout(),
            topOffsetDp = topOffsetDp,
            widgetSpacingDp = content.widgetAreaSettings.widgetSpacing.dp,
            mediaPlayerPlacement = mediaPlayerSettings.placement,
            animateOffset = presentation.animateMoveOffset,
            listState = presentation.listState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetAreaState()
    )

    private val menuTarget: MutableStateFlow<WidgetMenuTarget?> = MutableStateFlow(null)
    private val expandedChoice: MutableStateFlow<WidgetMenuAction?> = MutableStateFlow(null)

    private val selectedMediaSession: Flow<MediaSessionSnapshot?> = combine(
        settingsRepository.mediaPlayerSettings,
        mediaSessionSource.sessions,
        mediaSessionSource.hasAccess
    ) { settings, sessions, hasAccess ->
        if (hasAccess) MediaPlayerSelector.selectSession(sessions = sessions, settings = settings) else null
    }

    val mediaPlayerState: StateFlow<MediaPlayerUiState> = combine(
        selectedMediaSession,
        settingsRepository.mediaPlayerSettings,
        homeEditState.mode
    ) { session, settings, mode ->
        if (session == null || mode != HomeEditMode.None) {
            MediaPlayerUiState.Hidden
        } else {
            MediaPlayerUiState.Visible(session = session, placement = settings.placement, showArtwork = settings.showArtwork)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = MediaPlayerUiState.Hidden
    )

    private val menuContext: Flow<MenuContext> = combine(menuTarget, expandedChoice) { target, expanded ->
        MenuContext(target = target, expandedChoice = expanded)
    }

    private val menuSettings: Flow<MenuSettings> = combine(
        settingsAndBattery,
        eventSettingsState,
        settingsRepository.mediaPlayerSettings,
        mediaSessionSource.hasAccess
    ) { header, events, mediaPlayer, hasAccess ->
        MenuSettings(header = header, events = events, mediaPlayer = mediaPlayer, notificationAccess = hasAccess)
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
        menuPlaceState,
        selectedMediaSession
    ) { context, menuSettings, placeState, mediaSession ->
        if (placeState.mode != HomeEditMode.None) {
            WidgetMenuState.Closed
        } else {
            context.target.toMenuState(
                places = placeState.places,
                menuSettings = menuSettings,
                reading = placeState.reading,
                expandedChoice = context.expandedChoice,
                headerHeightDp = placeState.headerHeightDp,
                mediaSession = mediaSession
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
        initialValue = WidgetMenuState.Closed
    )

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
            headerSettingsActions.setClockStyle(style = style)
        }
    }

    fun setClockVisible(visible: Boolean) {
        viewModelScope.launch {
            headerSettingsActions.setClockVisible(visible = visible)
        }
    }

    fun setDateVisible(visible: Boolean) {
        viewModelScope.launch {
            headerSettingsActions.setDateVisible(visible = visible)
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
            headerSettingsActions.setBatteryVisibility(visibility = visibility)
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
            resizeController.clampStackHeight(placeId = undo.headerPlaceId, headerHeightDp = headerHeightDp.value)
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

                is UndoAction.IncludeMediaApp -> settingsRepository.setMediaPlayerPackageExcluded(
                    packageName = action.packageName,
                    excluded = false
                )
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

    fun openMediaPlayerMenu() {
        if (homeEditState.mode.value == HomeEditMode.None) {
            menuTarget.value = WidgetMenuTarget.MediaPlayer
        }
    }

    fun openMediaPlayer() {
        val session: MediaSessionSnapshot = (mediaPlayerState.value as? MediaPlayerUiState.Visible)?.session ?: return
        menuTarget.value = null
        if (!mediaSessionSource.open(key = session.key)) {
            viewModelScope.launch {
                eventChannel.send(WidgetAreaEvent.ShowMessage(message = R.string.couldnt_open_app, formatArg = session.appLabel))
            }
        }
    }

    fun openMediaPlayerSettings() {
        menuTarget.value = null
        viewModelScope.launch {
            eventChannel.send(WidgetAreaEvent.NavigateToMediaPlayerSettings)
        }
    }

    fun excludeMediaApp() {
        val session: MediaSessionSnapshot = (mediaPlayerState.value as? MediaPlayerUiState.Visible)?.session ?: return
        menuTarget.value = null
        viewModelScope.launch {
            settingsRepository.setMediaPlayerPackageExcluded(packageName = session.packageName, excluded = true)
            eventChannel.send(
                WidgetAreaEvent.ShowUndo(
                    action = UndoAction.IncludeMediaApp(packageName = session.packageName, appLabel = session.appLabel)
                )
            )
        }
    }

    fun onMediaCommand(command: MediaCommand) {
        val session: MediaSessionSnapshot = (mediaPlayerState.value as? MediaPlayerUiState.Visible)?.session ?: return
        mediaSessionSource.execute(key = session.key, command = command)
    }

    fun setMediaPlayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayerEnabled(enabled)
            if (enabled && !mediaSessionSource.hasAccess.value) {
                eventChannel.send(WidgetAreaEvent.RequestNotificationAccess)
            }
        }
    }

    fun showNotificationAccess() {
        viewModelScope.launch {
            eventChannel.send(WidgetAreaEvent.RequestNotificationAccess)
        }
    }

    fun refreshMediaAccess() {
        mediaSessionSource.refreshAccess()
    }

    fun setUpcomingEventsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            handleUpcomingEventsOutcome(outcome = headerSettingsActions.setUpcomingEventsEnabled(enabled = enabled))
        }
    }

    fun onCalendarPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        viewModelScope.launch {
            handleUpcomingEventsOutcome(
                outcome = headerSettingsActions.onCalendarPermissionResult(granted = granted, canAskAgain = canAskAgain)
            )
        }
    }

    private suspend fun handleUpcomingEventsOutcome(outcome: UpcomingEventsOutcome) {
        when (outcome) {
            UpcomingEventsOutcome.NoAction -> Unit

            UpcomingEventsOutcome.RequestPermission -> eventChannel.send(WidgetAreaEvent.RequestCalendarPermission)

            is UpcomingEventsOutcome.PermissionDenied -> eventChannel.send(
                WidgetAreaEvent.ShowCalendarPermissionDenied(permanentlyDenied = outcome.permanentlyDenied)
            )
        }
    }

    fun refreshCalendarPermission() {
        headerSettingsActions.refreshCalendarPermission()
    }

    fun setHomeHeight(heightDp: Int) {
        homeHeightState.setHeight(heightDp = heightDp)
        resizeController.setHomeHeight(heightDp = heightDp)
    }

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

    fun resetResize() = resizeController.resetResize()

    fun finishResize() {
        finishResizeIfResizing()
    }

    private fun finishResizeIfResizing(): Boolean {
        val finish: ResizeFinish = resizeController.finishResize() ?: return false
        viewModelScope.launch {
            val report: WidgetSizeReport = resizeController.persistResize(finish = finish) ?: return@launch
            eventChannel.send(report.toEvent())
        }
        return true
    }

    /** Opens the Move Widget Area adjuster without saving until it is finished. */
    fun startMoveWidgetArea() {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = null
        viewModelScope.launch {
            val settings: WidgetAreaSettings = settingsRepository.widgetAreaSettings.first()
            if (!homeEditState.tryEnter(HomeEditMode.MovingWidgetArea)) {
                return@launch
            }
            animateMoveOffset.value = false
            moveDraft.value = clampOffset(offsetDp = settings.topOffsetDp, homeHeightDp = resizeController.homeHeightDp.value)
        }
    }

    fun setDraftOffset(offsetDp: Int) = updateDraftOffset(offsetDp = offsetDp, animate = false)

    fun stepOffset(step: MoveWidgetAreaStep) {
        val current: Int = moveDraft.value ?: return
        val delta: Int = if (step == MoveWidgetAreaStep.Up) -WidgetAreaSettings.OFFSET_STEP_DP else WidgetAreaSettings.OFFSET_STEP_DP
        updateDraftOffset(offsetDp = current + delta, animate = true)
    }

    fun resetOffset() = updateDraftOffset(offsetDp = 0, animate = true)

    fun finishMoveWidgetArea() {
        val draft: Int = moveDraft.value ?: return
        moveDraft.value = null
        animateMoveOffset.value = false
        homeEditState.exit(HomeEditMode.MovingWidgetArea)
        val offsetDp: Int = clampOffset(offsetDp = draft, homeHeightDp = resizeController.homeHeightDp.value)
        viewModelScope.launch {
            settingsRepository.updateWidgetAreaSettings { settings -> settings.copy(topOffsetDp = offsetDp) }
        }
    }

    private fun updateDraftOffset(offsetDp: Int, animate: Boolean) {
        if (moveDraft.value == null) {
            return
        }
        animateMoveOffset.value = animate
        moveDraft.value = clampOffset(
            offsetDp = WidgetAreaSettings.snapOffsetToStep(offsetDp = offsetDp),
            homeHeightDp = resizeController.homeHeightDp.value
        )
    }

    private fun toMoveState(offsetDp: Int, homeHeightDp: Int?): MoveWidgetAreaState {
        val maxOffsetDp: Int = maxOffsetDp(homeHeightDp = homeHeightDp)
        val clamped: Int = offsetDp.coerceIn(0, maxOffsetDp)
        return MoveWidgetAreaState(
            draftOffsetDp = clamped,
            maxOffsetDp = maxOffsetDp,
            canMoveUp = clamped > 0,
            canMoveDown = clamped < maxOffsetDp
        )
    }

    private fun maxOffsetDp(homeHeightDp: Int?): Int {
        val heightDp: Int = homeHeightDp ?: return 0
        return WidgetAreaSettings.maxOffsetDp(homeHeightDp = heightDp)
    }

    private fun clampOffset(offsetDp: Int, homeHeightDp: Int?): Int = if (homeHeightDp == null) {
        offsetDp.coerceAtLeast(0)
    } else {
        offsetDp.coerceIn(0, maxOffsetDp(homeHeightDp = homeHeightDp))
    }

    fun onBack(): Boolean = finishEditingOrMove(fallback = ::finishResizeIfResizing)

    fun onHomeIntent(): Boolean {
        widgetAreaListState.value = LazyListState()
        return finishEditingOrMove(fallback = ::finishResizeIfResizing)
    }

    private fun finishEditingOrMove(fallback: () -> Boolean): Boolean {
        if (editDraft.value != null) {
            finishEditingWidgets()
            return true
        }
        if (moveDraft.value != null) {
            finishMoveWidgetArea()
            return true
        }
        return fallback()
    }

    fun openAppWidgetMenu(widgetId: Long) {
        if (homeEditState.mode.value == HomeEditMode.None) {
            menuTarget.value = WidgetMenuTarget.AppWidget(widgetId = widgetId)
        }
    }

    fun onMenuAction(action: WidgetMenuAction) {
        when (action) {
            WidgetMenuAction.AddWidget -> startAddFromMenu()

            WidgetMenuAction.MoveWidgetArea -> startMoveWidgetArea()

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

            WidgetMenuAction.MediaPlayer -> setMediaPlayerEnabled(enabled = true)

            WidgetMenuAction.OpenMediaPlayer -> openMediaPlayer()

            WidgetMenuAction.ExcludeMediaApp -> excludeMediaApp()

            WidgetMenuAction.MediaPlayerSettings -> openMediaPlayerSettings()

            WidgetMenuAction.Clock,
            WidgetMenuAction.Date,
            WidgetMenuAction.Battery,
            WidgetMenuAction.UpcomingEvents,
            WidgetMenuAction.ExtendUnderRail -> Unit
        }
    }

    fun startAdd(origin: AddWidgetOrigin): Boolean {
        val started: Boolean = addFlowController.start(origin = origin)
        if (started) {
            menuTarget.value = null
        }
        return started
    }

    fun choosePlacement(placement: AddWidgetPlacement) {
        if (addFlowController.choosePlacement(placement = placement)) {
            menuTarget.value = null
        }
    }

    fun onPageSettled(placeId: Long, page: Int) {
        pageSettleJobs[placeId]?.cancel()
        pageSettleJobs[placeId] = viewModelScope.launch {
            delay(PAGE_SETTLE_DEBOUNCE_MS)
            repository.setCurrentPage(placeId = placeId, page = page)
            pageSettleJobs.remove(placeId)
        }
    }

    fun setPickerQuery(query: String) = addFlowController.setQuery(query = query)

    fun pickProvider(provider: WidgetProviderInfo) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.pick(provider = provider))
        }
    }

    fun cancelAdd() {
        viewModelScope.launch {
            addFlowController.cancel()
        }
    }

    fun onBindResult(granted: Boolean) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.onBindResult(granted = granted))
        }
    }

    fun onConfigureResult(ok: Boolean) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.onConfigureResult(ok = ok))
        }
    }

    private suspend fun handleStackGrown(placeId: Long?) {
        if (placeId == null) {
            return
        }
        val report: WidgetSizeReport = resizeController.onStackGrown(placeId = placeId, headerHeightDp = headerHeightDp.value)
            ?: return
        eventChannel.send(report.toEvent())
    }

    private fun WidgetSizeReport.toEvent(): WidgetAreaEvent.ReportWidgetSize = WidgetAreaEvent.ReportWidgetSize(
        appWidgetIds = appWidgetIds,
        widthDp = widthDp,
        heightDp = heightDp
    )

    fun removeWidget(widgetId: Long) {
        viewModelScope.launch {
            val appWidgetId: AppWidgetId = removeAppWidget(widgetId = widgetId) ?: return@launch
            menuTarget.value = null
            eventChannel.send(WidgetAreaEvent.RemoveHostView(appWidgetId = appWidgetId))
            eventChannel.send(WidgetAreaEvent.ShowMessage(message = CoreWidgetsR.string.widget_removed))
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
        appWidgetHost.providerInfo(id = AppWidgetId(widget.appWidgetId))

    private suspend fun findAppWidget(widgetId: Long): PlacedWidget.AppWidget? = repository.places.first()
        .asSequence()
        .flatMap { place -> place.widgets.asSequence() }
        .filterIsInstance<PlacedWidget.AppWidget>()
        .firstOrNull { widget -> widget.id == widgetId }

    fun startAddFromMenu() {
        val target: WidgetMenuTarget = menuTarget.value ?: return
        viewModelScope.launch {
            if (addFlowController.startFromMenu(target = target.toAddTarget())) {
                menuTarget.value = null
            }
        }
    }

    private fun WidgetMenuTarget.toAddTarget(): AddWidgetTarget = when (this) {
        is WidgetMenuTarget.Header -> AddWidgetTarget.Header(widgetId = widgetId)

        is WidgetMenuTarget.AppWidget -> AddWidgetTarget.Widget(widgetId = widgetId)

        WidgetMenuTarget.EmptyArea,
        WidgetMenuTarget.ClockStyle,
        WidgetMenuTarget.MediaPlayer -> AddWidgetTarget.Empty
    }

    private fun WidgetMenuTarget?.toMenuState(
        places: List<WidgetPlace>,
        menuSettings: MenuSettings,
        reading: ClockReading,
        expandedChoice: WidgetMenuAction?,
        headerHeightDp: Int?,
        mediaSession: MediaSessionSnapshot?
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
                        mediaPlayer = menuSettings.mediaPlayer,
                        notificationAccess = menuSettings.notificationAccess,
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

        WidgetMenuTarget.MediaPlayer -> {
            if (mediaSession == null) {
                WidgetMenuState.Closed
            } else {
                WidgetMenuState.MediaPlayerMenu(
                    icon = Icon.ApplicationIcon(packageName = mediaSession.packageName),
                    label = mediaSession.appLabel,
                    items = listOf(
                        WidgetMenuItem.Action(id = WidgetMenuAction.OpenMediaPlayer, labelArg = mediaSession.appLabel),
                        WidgetMenuItem.Action(id = WidgetMenuAction.ExcludeMediaApp, labelArg = mediaSession.appLabel),
                        WidgetMenuItem.Action(id = WidgetMenuAction.MediaPlayerSettings)
                    )
                )
            }
        }

        is WidgetMenuTarget.AppWidget -> {
            val place: WidgetPlace? = places.firstOrNull { candidate -> candidate.widgets.any { widget -> widget.id == widgetId } }
            val widget: PlacedWidget.AppWidget? = place?.widgets
                ?.filterIsInstance<PlacedWidget.AppWidget>()
                ?.firstOrNull { candidate -> candidate.id == widgetId }
            if (place == null || widget == null) {
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
                                    CoreWidgetsR.string.not_available_in_stack
                                } else {
                                    CoreWidgetsR.string.extend_under_rail_supporting_text
                                }
                            )
                        )
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.MoveWidgetArea))
                        add(WidgetMenuItem.Action(id = WidgetMenuAction.EditWidgets))
                        add(
                            WidgetMenuItem.Action(
                                id = if (place.isStack) WidgetMenuAction.RemoveFromStack else WidgetMenuAction.RemoveWidget
                            )
                        )
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
        mediaPlayer: MediaPlayerSettings,
        notificationAccess: Boolean,
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
                WidgetMenuChoiceOption(
                    key = WidgetMenuChoiceKey(visibility.name),
                    label = visibility.labelResource(),
                    enabled = settings.canSetBatteryVisibility(visibility)
                )
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
        WidgetMenuItem.Action(id = WidgetMenuAction.MoveWidgetArea),
        WidgetMenuItem.Action(id = WidgetMenuAction.EditWidgets),
        WidgetMenuItem.Toggle(
            id = WidgetMenuAction.MediaPlayer,
            checked = mediaPlayer.enabled,
            enabled = true,
            supportingText = if (mediaPlayer.enabled && !notificationAccess) R.string.needs_notification_access else null,
            rowOpensAction = mediaPlayer.enabled && !notificationAccess
        ),
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
        add(WidgetMenuItem.Action(id = WidgetMenuAction.MoveWidgetArea))
        add(WidgetMenuItem.Action(id = WidgetMenuAction.WidgetSettings))
    }

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
        is PlacedWidget.Header -> tapTargetResolver.applyTo(
            header = formatter.format(
                id = id,
                reading = reading,
                settings = settings,
                battery = battery,
                events = events,
                eventSettings = eventSettings
            ),
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
