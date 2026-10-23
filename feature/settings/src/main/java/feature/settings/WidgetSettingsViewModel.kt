package feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.calendar.CalendarInfo
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEventsOutcome
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.media.IMediaSessionSource
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.DateFormatPreset
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSettings
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.prefs.is24Hour
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IHeaderSettingsActions
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.MediaPlayerSettings
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSpacing
import core.data.widgets.iconOr
import core.data.widgets.labelOr
import core.ui.R as CoreUiR
import core.ui.layout.HomeHeightState
import core.widgets.HeaderFormatter
import core.widgets.HeaderModel
import core.widgets.IHeaderTapTargetResolver
import java.text.Normalizer
import java.util.Locale
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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

/** One app offered in the Media Player exclusion list. */
data class ExcludableApp(val packageName: String, val label: String, val icon: Icon, val isExcluded: Boolean)

data class MediaPlayerExclusionsState(val query: String = "", val isLoaded: Boolean = false, val apps: List<ExcludableApp> = emptyList())

/** One placed App Widget row in Settings. */
data class WidgetSettingsAppWidget(
    val widgetId: Long,
    val appWidgetId: Int,
    val label: String,
    val icon: Icon,
    val isWorkProfile: Boolean,
    val extendsUnderRail: Boolean,
    val canExtendUnderRail: Boolean
)

data class WidgetSettingsUiState(
    val isLoaded: Boolean = false,
    val headerShown: Boolean = true,
    val clockVisible: Boolean = true,
    val dateVisible: Boolean = true,
    val batteryVisibility: BatteryVisibility = BatteryVisibility.NEVER,
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
    ),
    val calendarAccess: Boolean = false,
    val eventsEnabled: Boolean = false,
    val eventLookaheadHours: Int = EventSettings.DEFAULT_LOOKAHEAD_HOURS,
    val eventMaxEvents: Int = 1,
    val includeAllDay: Boolean = true,
    val excludedCalendarIds: Set<String> = emptySet(),
    val calendars: List<CalendarInfo> = emptyList(),
    val appWidgets: List<WidgetSettingsAppWidget> = emptyList(),
    val maxStackPages: Int = WidgetAreaSettings.DEFAULT_MAX_STACK_PAGES,
    val stackIndicatorVisible: Boolean = true,
    val heightLimitEnabled: Boolean = true,
    val heightLimit: WidgetAreaHeightLimit = WidgetAreaHeightLimit.DEFAULT,
    val topOffsetDp: Int = 0,
    val widgetSpacing: WidgetSpacing = WidgetSpacing.DEFAULT,
    val homeHeightDp: Int? = null,
    val mediaPlayerEnabled: Boolean = false,
    val showWhenPaused: Boolean = true,
    val showArtwork: Boolean = true,
    val mediaPlayerPlacement: MediaPlayerPlacement = MediaPlayerPlacement.BELOW_WIDGET_AREA,
    val excludedMediaPlayerPackages: Set<String> = emptySet(),
    val notificationAccess: Boolean = false
)

private data class EventSettingsState(val settings: EventSettings, val hasPermission: Boolean, val calendars: List<CalendarInfo>)

private data class MediaSettingsState(val settings: MediaPlayerSettings, val hasAccess: Boolean)

private data class WidgetSettingsRows(
    val places: List<WidgetPlace>,
    val appWidgets: List<WidgetSettingsAppWidget>,
    val areaSettings: WidgetAreaSettings,
    val homeHeightDp: Int?,
    val mediaPlayerSettings: MediaPlayerSettings,
    val notificationAccess: Boolean
)

sealed interface WidgetSettingsEvent {
    data object RequestCalendarPermission : WidgetSettingsEvent

    data object RequestNotificationAccess : WidgetSettingsEvent

    data class ShowCalendarPermissionDenied(val permanentlyDenied: Boolean) : WidgetSettingsEvent

    data object StartAddWidget : WidgetSettingsEvent

    data class AppWidgetRemoved(val appWidgetId: AppWidgetId) : WidgetSettingsEvent

    data class ShowMessage(@StringRes val message: Int) : WidgetSettingsEvent
}

class WidgetSettingsViewModel(
    private val settingsRepository: IWidgetSettingsRepository,
    private val areaRepository: IWidgetAreaRepository,
    private val clockSource: IClockSource,
    private val formatter: HeaderFormatter,
    private val launchableRepository: ILaunchableRepository,
    private val calendarEventSource: ICalendarEventSource,
    private val appWidgetHost: IAppWidgetHostSource,
    private val removeAppWidget: IRemoveAppWidget,
    private val homeHeightState: HomeHeightState,
    private val mediaSessionSource: IMediaSessionSource,
    private val headerSettingsActions: IHeaderSettingsActions,
    private val tapTargetResolver: IHeaderTapTargetResolver
) : ViewModel() {
    private val pickerPart: MutableStateFlow<HeaderTapTargetPart?> = MutableStateFlow(null)
    private val pickerQuery: MutableStateFlow<String> = MutableStateFlow("")
    private val exclusionsOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val exclusionQuery: MutableStateFlow<String> = MutableStateFlow("")
    private val eventChannel: Channel<WidgetSettingsEvent> = Channel(Channel.BUFFERED)

    val events: Flow<WidgetSettingsEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val calendars: Flow<List<CalendarInfo>> = settingsRepository.eventSettings
        .map { settings -> settings.enabled }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) {
                calendarEventSource.calendars
            } else {
                flowOf(emptyList())
            }
        }
        .onEach { loadedCalendars ->
            if (loadedCalendars.isNotEmpty()) {
                val availableCalendarIds: Set<String> = loadedCalendars.mapTo(mutableSetOf()) { calendar -> calendar.calendarKey }
                settingsRepository.pruneExcludedCalendarIds(availableCalendarIds)
            }
        }

    private val eventSettingsState: Flow<EventSettingsState> = combine(
        settingsRepository.eventSettings,
        calendarEventSource.hasPermission,
        calendars
    ) { settings, hasPermission, calendars ->
        EventSettingsState(settings = settings, hasPermission = hasPermission, calendars = calendars)
    }

    private val widgetRows: Flow<WidgetSettingsRows> = combine(
        areaRepository.places,
        appWidgetHost.providers,
        settingsRepository.widgetAreaSettings,
        homeHeightState.heightDp,
        combine(settingsRepository.mediaPlayerSettings, mediaSessionSource.hasAccess) { settings, access ->
            MediaSettingsState(settings = settings, hasAccess = access)
        }
    ) { places, providers, areaSettings, homeHeightDp, media ->
        WidgetSettingsRows(
            places = places,
            areaSettings = areaSettings,
            homeHeightDp = homeHeightDp,
            mediaPlayerSettings = media.settings,
            notificationAccess = media.hasAccess,
            appWidgets = places.flatMap { place ->
                place.widgets.filterIsInstance<PlacedWidget.AppWidget>().map { widget ->
                    val info: WidgetProviderInfo? = providers.firstOrNull { provider ->
                        provider.provider == widget.provider && provider.profileSerial == widget.profileSerial
                    }
                    WidgetSettingsAppWidget(
                        widgetId = widget.id,
                        appWidgetId = widget.appWidgetId,
                        label = info.labelOr(provider = widget.provider),
                        icon = info.iconOr(provider = widget.provider),
                        isWorkProfile = info?.isWorkProfile ?: false,
                        extendsUnderRail = widget.extendsUnderRail,
                        canExtendUnderRail = !place.isStack
                    )
                }
            }
        )
    }

    val uiState: StateFlow<WidgetSettingsUiState> = combine(
        widgetRows,
        settingsRepository.headerSettings,
        clockSource.readings,
        launchableRepository.launchableState,
        eventSettingsState
    ) { rows, settings, reading, launchableState, eventSettingsState ->
        val places: List<WidgetPlace> = rows.places
        val timeTapTarget: HeaderTapSetting = tapTargetResolver.resolveSetting(
            setting = settings.resolvedTimeTapTarget,
            launchables = launchableState.launchables,
            isLoaded = launchableState.isLoaded
        )
        val dateTapTarget: HeaderTapSetting = tapTargetResolver.resolveSetting(
            setting = settings.resolvedDateTapTarget,
            launchables = launchableState.launchables,
            isLoaded = launchableState.isLoaded
        )
        WidgetSettingsUiState(
            isLoaded = true,
            headerShown = places.any { place -> place.widgets.any { widget -> widget is PlacedWidget.Header } },
            clockVisible = settings.clockVisible,
            dateVisible = settings.dateVisible,
            batteryVisibility = settings.batteryVisibility,
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
            ),
            calendarAccess = eventSettingsState.hasPermission,
            eventsEnabled = eventSettingsState.settings.enabled,
            eventLookaheadHours = eventSettingsState.settings.lookaheadHours,
            eventMaxEvents = eventSettingsState.settings.resolvedMaxEvents,
            includeAllDay = eventSettingsState.settings.includeAllDay,
            excludedCalendarIds = eventSettingsState.settings.excludedCalendarIds,
            calendars = eventSettingsState.calendars,
            appWidgets = rows.appWidgets,
            maxStackPages = rows.areaSettings.maxStackPages,
            stackIndicatorVisible = rows.areaSettings.stackIndicatorVisible,
            heightLimitEnabled = rows.areaSettings.heightLimitEnabled,
            heightLimit = rows.areaSettings.heightLimit,
            topOffsetDp = rows.areaSettings.topOffsetDp,
            widgetSpacing = rows.areaSettings.widgetSpacing,
            homeHeightDp = rows.homeHeightDp,
            mediaPlayerEnabled = rows.mediaPlayerSettings.enabled,
            showWhenPaused = rows.mediaPlayerSettings.showWhenPaused,
            showArtwork = rows.mediaPlayerSettings.showArtwork,
            mediaPlayerPlacement = rows.mediaPlayerSettings.placement,
            excludedMediaPlayerPackages = rows.mediaPlayerSettings.excludedPackages,
            notificationAccess = rows.notificationAccess
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

    val mediaPlayerExclusions: StateFlow<MediaPlayerExclusionsState?> = combine(
        exclusionsOpen,
        exclusionQuery,
        launchableRepository.launchableState,
        settingsRepository.mediaPlayerSettings
    ) { open, query, launchableState, settings ->
        if (!open) {
            null
        } else {
            MediaPlayerExclusionsState(
                query = query,
                isLoaded = launchableState.isLoaded,
                apps = excludableApps(
                    launchables = launchableState.launchables,
                    excludedPackages = settings.excludedPackages,
                    query = query
                )
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
            headerSettingsActions.setClockVisible(visible = visible)
        }
    }

    fun setDateVisible(visible: Boolean) {
        viewModelScope.launch {
            headerSettingsActions.setDateVisible(visible = visible)
        }
    }

    fun setBatteryVisibility(visibility: BatteryVisibility) {
        viewModelScope.launch {
            headerSettingsActions.setBatteryVisibility(visibility = visibility)
        }
    }

    fun setClockStyle(style: ClockStyle) {
        viewModelScope.launch {
            headerSettingsActions.setClockStyle(style = style)
        }
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

    fun setEventsEnabled(enabled: Boolean) {
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

            UpcomingEventsOutcome.RequestPermission -> eventChannel.send(WidgetSettingsEvent.RequestCalendarPermission)

            is UpcomingEventsOutcome.PermissionDenied -> eventChannel.send(
                WidgetSettingsEvent.ShowCalendarPermissionDenied(permanentlyDenied = outcome.permanentlyDenied)
            )
        }
    }

    fun refreshCalendarPermission() {
        headerSettingsActions.refreshCalendarPermission()
    }

    fun refreshMediaAccess() {
        mediaSessionSource.refreshAccess()
    }

    fun setMediaPlayerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayerEnabled(enabled)
            if (enabled && !mediaSessionSource.hasAccess.value) {
                eventChannel.send(WidgetSettingsEvent.RequestNotificationAccess)
            }
        }
    }

    fun setShowWhenPaused(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayerShowWhenPaused(show)
        }
    }

    fun setShowArtwork(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayerShowArtwork(show)
        }
    }

    fun setPlacement(placement: MediaPlayerPlacement) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayerPlacement(placement)
        }
    }

    fun openExcludedApps() {
        exclusionQuery.value = ""
        exclusionsOpen.value = true
    }

    fun closeExcludedApps() {
        exclusionsOpen.value = false
    }

    fun setExclusionQuery(query: String) {
        exclusionQuery.value = query
    }

    fun setExcluded(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMediaPlayerPackageExcluded(packageName = packageName, excluded = excluded)
        }
    }

    fun openAddWidget() {
        viewModelScope.launch {
            eventChannel.send(WidgetSettingsEvent.StartAddWidget)
        }
    }

    fun setMaxStackPages(maxStackPages: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxStackPages(maxStackPages)
        }
    }

    fun setStackIndicatorVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStackIndicatorVisible(visible)
        }
    }

    fun setHeightLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHeightLimitEnabled(enabled)
        }
    }

    fun setHeightLimit(limit: WidgetAreaHeightLimit) {
        viewModelScope.launch {
            settingsRepository.setHeightLimit(limit)
        }
    }

    fun setTopOffsetDp(offsetDp: Int) {
        viewModelScope.launch {
            settingsRepository.setTopOffsetDp(offsetDp)
        }
    }

    fun setWidgetSpacing(spacing: WidgetSpacing) {
        viewModelScope.launch {
            settingsRepository.setWidgetSpacing(spacing)
        }
    }

    fun setExtendsUnderRail(widgetId: Long, extendsUnderRail: Boolean) {
        viewModelScope.launch {
            areaRepository.setExtendsUnderRail(widgetId = widgetId, extendsUnderRail = extendsUnderRail)
        }
    }

    fun removeWidget(widgetId: Long) {
        viewModelScope.launch {
            val appWidgetId: AppWidgetId = removeAppWidget(widgetId = widgetId) ?: return@launch
            eventChannel.send(WidgetSettingsEvent.AppWidgetRemoved(appWidgetId = appWidgetId))
            eventChannel.send(WidgetSettingsEvent.ShowMessage(message = CoreUiR.string.widget_removed))
        }
    }

    fun setEventLookaheadHours(hours: Int) {
        viewModelScope.launch {
            settingsRepository.setEventLookaheadHours(hours)
        }
    }

    fun setEventMaxEvents(maxEvents: Int) {
        viewModelScope.launch {
            settingsRepository.setEventMaxEvents(maxEvents)
        }
    }

    fun setEventIncludeAllDay(includeAllDay: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEventIncludeAllDay(includeAllDay)
        }
    }

    fun setExcludedCalendarIds(calendarIds: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setExcludedCalendarIds(calendarIds)
        }
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

/** The installed apps offered in the Media Player exclusion list, excluded apps first. */
internal fun excludableApps(launchables: List<Launchable>, excludedPackages: Set<String>, query: String): List<ExcludableApp> =
    filterExcludableApps(
        apps = launchables.filterIsInstance<App>()
            .distinctBy { app -> app.packageName }
            .map { app ->
                ExcludableApp(
                    packageName = app.packageName,
                    label = app.name,
                    icon = app.icon,
                    isExcluded = app.packageName in excludedPackages
                )
            },
        query = query
    )

/** Sorts excluded apps first and filters by label or package name. */
internal fun filterExcludableApps(apps: List<ExcludableApp>, query: String): List<ExcludableApp> {
    val normalizedQuery: String = query.normalizedForSearch()
    return apps
        .filter { app ->
            normalizedQuery.isEmpty() ||
                app.label.normalizedForSearch().contains(normalizedQuery) ||
                app.packageName.normalizedForSearch().contains(normalizedQuery)
        }
        .sortedWith(
            compareByDescending<ExcludableApp> { app -> app.isExcluded }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { app -> app.label }
        )
}

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS_REGEX, "").lowercase(Locale.ROOT)

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
