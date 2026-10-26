//[data](../../../index.md)/[core.data.widgets](../index.md)/[WidgetProviderInfo](index.md)

# WidgetProviderInfo

[main]\
data class [WidgetProviderInfo](index.md)(val provider: [WidgetProvider](../-widget-provider/index.md), val profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val appPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val appLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val appIcon: [Icon](../../core.data.icons.model/-icon/index.md), val label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val description: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, val defaultSize: [WidgetSize](../-widget-size/index.md), val cellSize: [WidgetSize](../-widget-size/index.md)?, val minHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val minResizeHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val maxResizeHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, val needsConfiguration: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val isReconfigurable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val hasConfigureActivity: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val isWorkProfile: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val isVerticallyResizable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false)

An App Widget provider offered by another app, resolved for Rail's UI.

## Constructors

| | |
|---|---|
| [WidgetProviderInfo](-widget-provider-info.md) | [main]<br>constructor(provider: [WidgetProvider](../-widget-provider/index.md), profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), appPackageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), appLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), appIcon: [Icon](../../core.data.icons.model/-icon/index.md), label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), description: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, defaultSize: [WidgetSize](../-widget-size/index.md), cellSize: [WidgetSize](../-widget-size/index.md)?, minHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), minResizeHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), maxResizeHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, needsConfiguration: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), isReconfigurable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), hasConfigureActivity: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), isWorkProfile: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), isVerticallyResizable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false) |

## Properties

| Name | Summary |
|---|---|
| [appIcon](app-icon.md) | [main]<br>val [appIcon](app-icon.md): [Icon](../../core.data.icons.model/-icon/index.md) |
| [appLabel](app-label.md) | [main]<br>val [appLabel](app-label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [appPackageName](app-package-name.md) | [main]<br>val [appPackageName](app-package-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [cellSize](cell-size.md) | [main]<br>val [cellSize](cell-size.md): [WidgetSize](../-widget-size/index.md)? |
| [defaultSize](default-size.md) | [main]<br>val [defaultSize](default-size.md): [WidgetSize](../-widget-size/index.md) |
| [description](description.md) | [main]<br>val [description](description.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? |
| [hasConfigureActivity](has-configure-activity.md) | [main]<br>val [hasConfigureActivity](has-configure-activity.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isReconfigurable](is-reconfigurable.md) | [main]<br>val [isReconfigurable](is-reconfigurable.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isVerticallyResizable](is-vertically-resizable.md) | [main]<br>val [isVerticallyResizable](is-vertically-resizable.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [isWorkProfile](is-work-profile.md) | [main]<br>val [isWorkProfile](is-work-profile.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [label](label.md) | [main]<br>val [label](label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [maxResizeHeightDp](max-resize-height-dp.md) | [main]<br>val [maxResizeHeightDp](max-resize-height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? |
| [minHeightDp](min-height-dp.md) | [main]<br>val [minHeightDp](min-height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [minResizeHeightDp](min-resize-height-dp.md) | [main]<br>val [minResizeHeightDp](min-resize-height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [needsConfiguration](needs-configuration.md) | [main]<br>val [needsConfiguration](needs-configuration.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [profileSerial](profile-serial.md) | [main]<br>val [profileSerial](profile-serial.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |
| [provider](provider.md) | [main]<br>val [provider](provider.md): [WidgetProvider](../-widget-provider/index.md) |

## Functions

| Name | Summary |
|---|---|
| [iconOr](../icon-or.md) | [main]<br>fun [WidgetProviderInfo](index.md)?.[iconOr](../icon-or.md)(provider: [WidgetProvider](../-widget-provider/index.md)): [Icon](../../core.data.icons.model/-icon/index.md)<br>The provider app icon, falling back to the app package when it is no longer installed. |
| [labelOr](../label-or.md) | [main]<br>fun [WidgetProviderInfo](index.md)?.[labelOr](../label-or.md)(provider: [WidgetProvider](../-widget-provider/index.md)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)<br>The provider label, falling back to the provider class when it is no longer installed. |
