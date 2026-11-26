package core.data.testing

import core.data.icons.model.Icon
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize

private const val DEFAULT_PROFILE_SERIAL: Long = 0L
private const val DEFAULT_MIN_HEIGHT_DP: Int = 48
private const val DEFAULT_WIDGET_WIDTH_DP: Int = 110

fun appWidgetProviderInfo(
    provider: WidgetProvider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
    appPackageName: String = provider.packageName,
    appLabel: String = "Example",
    label: String = "Example Widget",
    needsConfiguration: Boolean = false,
    isReconfigurable: Boolean = false,
    hasConfigureActivity: Boolean = true,
    minHeightDp: Int = DEFAULT_MIN_HEIGHT_DP,
    minResizeHeightDp: Int = DEFAULT_MIN_HEIGHT_DP,
    maxResizeHeightDp: Int? = null,
    isVerticallyResizable: Boolean = false
): WidgetProviderInfo = WidgetProviderInfo(
    provider = provider,
    profileSerial = DEFAULT_PROFILE_SERIAL,
    appPackageName = appPackageName,
    appLabel = appLabel,
    appIcon = Icon.ApplicationIcon(packageName = appPackageName),
    label = label,
    description = null,
    defaultSize = WidgetSize(widthDp = DEFAULT_WIDGET_WIDTH_DP, heightDp = minHeightDp),
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
