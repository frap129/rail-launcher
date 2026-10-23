package feature.launcher.widgets

import androidx.lifecycle.SavedStateHandle
import core.data.icons.model.Icon
import core.data.testing.FakeAddFlowPendingIds
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize

internal val headerPlace: WidgetPlace = WidgetPlace(
    id = 1L,
    heightDp = null,
    currentPage = 0,
    widgets = listOf(PlacedWidget.Header(id = 1L))
)

internal fun appWidgetPlace(
    placeId: Long,
    widgetId: Long,
    appWidgetId: Int = 9,
    profileSerial: Long = 0L,
    availability: WidgetAvailability = WidgetAvailability.Available,
    defaultHeightDp: Int? = null,
    heightDp: Int? = null
): WidgetPlace = WidgetPlace(
    id = placeId,
    heightDp = heightDp,
    currentPage = 0,
    widgets = listOf(
        PlacedWidget.AppWidget(
            id = widgetId,
            appWidgetId = appWidgetId,
            provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
            profileSerial = profileSerial,
            extendsUnderRail = false,
            availability = availability,
            defaultHeightDp = defaultHeightDp
        )
    )
)

internal fun stackPlace(): WidgetPlace = WidgetPlace(
    id = 1L,
    heightDp = null,
    currentPage = 0,
    widgets = listOf(
        PlacedWidget.Header(id = 1L),
        appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single()
    )
)

internal fun appWidgetProviderInfo(
    provider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
    appPackageName: String = provider.packageName,
    appLabel: String = "Example",
    label: String = "Example Widget",
    needsConfiguration: Boolean = false,
    profileSerial: Long = 0L,
    isReconfigurable: Boolean = false,
    hasConfigureActivity: Boolean = true,
    minHeightDp: Int = 48,
    minResizeHeightDp: Int = 48,
    maxResizeHeightDp: Int? = null,
    isVerticallyResizable: Boolean = false
): WidgetProviderInfo = WidgetProviderInfo(
    provider = provider,
    profileSerial = profileSerial,
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

internal fun settingsAddOrigin(): AddWidgetOrigin = AddWidgetOrigin(insertAt = InsertPosition.End, returnToSettings = true)

internal fun createAddController(
    repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace)),
    appWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(),
    settingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(),
    homeEditState: HomeEditState = HomeEditState(),
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
    pendingIds: FakeAddFlowPendingIds = FakeAddFlowPendingIds()
): IWidgetAddController = IWidgetAddControllerImpl(
    repository = repository,
    appWidgetHost = appWidgetHost,
    settingsRepository = settingsRepository,
    homeEditState = homeEditState,
    savedStateHandle = savedStateHandle,
    pendingIds = pendingIds
)
