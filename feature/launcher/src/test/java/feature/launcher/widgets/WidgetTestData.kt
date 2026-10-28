package feature.launcher.widgets

import core.data.icons.model.Icon
import core.data.widgets.InsertPosition
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize

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
