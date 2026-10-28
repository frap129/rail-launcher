package feature.launcher.widgets

import androidx.lifecycle.SavedStateHandle
import core.data.battery.BatteryStatus
import core.data.battery.IBatterySource
import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEvent
import core.data.icons.model.Icon
import core.data.media.IMediaSessionSource
import core.data.media.MediaSessionSnapshot
import core.data.testing.AppWidgetHostState
import core.data.testing.TEST_APP_WIDGET_ID
import core.data.testing.WidgetAreaRepositoryState
import core.data.testing.createAddFlowPendingIdsMock
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createBatterySourceMock
import core.data.testing.createCalendarEventSourceMock
import core.data.testing.createClockSourceMock
import core.data.testing.createMediaSessionSourceMock
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.testing.headerPlace
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize
import core.ui.layout.HomeEditState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

fun appWidgetProviderInfo(
    provider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
    appPackageName: String = provider.packageName,
    appLabel: String = "Example",
    label: String = "Example Widget",
    needsConfiguration: Boolean = false,
    isReconfigurable: Boolean = false,
    hasConfigureActivity: Boolean = true,
    minHeightDp: Int = 48,
    minResizeHeightDp: Int = 48,
    maxResizeHeightDp: Int? = null,
    isVerticallyResizable: Boolean = false
): WidgetProviderInfo = WidgetProviderInfo(
    provider = provider,
    profileSerial = 0L,
    appPackageName = appPackageName,
    appLabel = appLabel,
    appIcon = Icon.ApplicationIcon(packageName = appPackageName),
    label = label,
    description = null,
    defaultSize = WidgetSize(widthDp = 110, heightDp = minHeightDp),
    cellSize = null,
    minHeightDp = minHeightDp,
    minResizeHeightDp = minResizeHeightDp,
    maxResizeHeightDp = maxResizeHeightDp,
    needsConfiguration = needsConfiguration,
    isReconfigurable = isReconfigurable,
    hasConfigureActivity = hasConfigureActivity,
    isWorkProfile = false,
    isVerticallyResizable = isVerticallyResizable
)

fun settingsAddOrigin(): AddWidgetOrigin = AddWidgetOrigin(insertAt = InsertPosition.End, returnToSettings = true)

fun createRepository(places: List<WidgetPlace> = listOf(headerPlace)): IWidgetAreaRepository =
    createWidgetAreaRepositoryMock(WidgetAreaRepositoryState(places))

class HostFixture(val state: AppWidgetHostState, val mockHost: IAppWidgetHostSource)

fun createHostFixture(
    providers: List<WidgetProviderInfo> = emptyList(),
    bindResults: List<Boolean> = emptyList(),
    providerInfos: Map<Int, WidgetProviderInfo> = emptyMap()
): HostFixture {
    val state: AppWidgetHostState = AppWidgetHostState(providers = providers, bindResults = bindResults)
    providerInfos.forEach { (appWidgetId, info) -> state.providerInfoById[AppWidgetId(appWidgetId)] = info }
    return HostFixture(state = state, mockHost = createAppWidgetHostMock(state))
}

fun createHost(
    providers: List<WidgetProviderInfo> = emptyList(),
    bindResults: List<Boolean> = emptyList(),
    providerInfos: Map<Int, WidgetProviderInfo> = emptyMap()
): IAppWidgetHostSource = createHostFixture(providers = providers, bindResults = bindResults, providerInfos = providerInfos).mockHost

fun createHost(
    info: WidgetProviderInfo,
    providerInfos: Map<Int, WidgetProviderInfo> = emptyMap(),
    bindResults: List<Boolean> = emptyList()
): IAppWidgetHostSource = createHost(
    providers = listOf(info),
    bindResults = bindResults,
    providerInfos = mapOf(TEST_APP_WIDGET_ID to info) + providerInfos
)

fun createClock(initialReading: ClockReading): IClockSource = createClockSourceMock(MutableStateFlow(initialReading))

fun createBattery(initialStatus: BatteryStatus): IBatterySource = createBatterySourceMock(MutableStateFlow(initialStatus))

fun createCalendar(
    hasPermission: Boolean = true,
    events: Flow<List<UpcomingEvent>> = emptyFlow(),
    queriedLookaheadHours: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
): ICalendarEventSource = createCalendarEventSourceMock(
    permission = MutableStateFlow(hasPermission),
    events = events,
    queriedLookaheadHours = queriedLookaheadHours
)

fun createMediaSession(initialSessions: List<MediaSessionSnapshot> = emptyList(), hasAccess: Boolean = true): IMediaSessionSource =
    createMediaSessionSourceMock(
        sessions = MutableStateFlow(initialSessions),
        hasAccess = hasAccess
    )

fun createAddController(
    mockRepository: IWidgetAreaRepository = createRepository(),
    mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock(),
    mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(),
    homeEditState: HomeEditState = HomeEditState(),
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
    mockPendingIds: IAddFlowPendingIds = createAddFlowPendingIdsMock()
): IWidgetAddController = IWidgetAddControllerImpl(
    repository = mockRepository,
    appWidgetHost = mockAppWidgetHost,
    settingsRepository = mockSettingsRepository,
    homeEditState = homeEditState,
    savedStateHandle = savedStateHandle,
    pendingIds = mockPendingIds
)
