//[settings](../../../index.md)/[feature.settings](../index.md)/[WidgetSettingsEvent](index.md)

# WidgetSettingsEvent

sealed interface [WidgetSettingsEvent](index.md)

#### Inheritors

| |
|---|
| [RequestCalendarPermission](-request-calendar-permission/index.md) |
| [RequestNotificationAccess](-request-notification-access/index.md) |
| [ShowCalendarPermissionDenied](-show-calendar-permission-denied/index.md) |
| [StartAddWidget](-start-add-widget/index.md) |
| [AppWidgetRemoved](-app-widget-removed/index.md) |
| [ShowMessage](-show-message/index.md) |

## Types

| Name | Summary |
|---|---|
| [AppWidgetRemoved](-app-widget-removed/index.md) | [main]<br>data class [AppWidgetRemoved](-app-widget-removed/index.md)(val appWidgetId: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [WidgetSettingsEvent](index.md) |
| [RequestCalendarPermission](-request-calendar-permission/index.md) | [main]<br>data object [RequestCalendarPermission](-request-calendar-permission/index.md) : [WidgetSettingsEvent](index.md) |
| [RequestNotificationAccess](-request-notification-access/index.md) | [main]<br>data object [RequestNotificationAccess](-request-notification-access/index.md) : [WidgetSettingsEvent](index.md) |
| [ShowCalendarPermissionDenied](-show-calendar-permission-denied/index.md) | [main]<br>data class [ShowCalendarPermissionDenied](-show-calendar-permission-denied/index.md)(val permanentlyDenied: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [WidgetSettingsEvent](index.md) |
| [ShowMessage](-show-message/index.md) | [main]<br>data class [ShowMessage](-show-message/index.md)(val message: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) : [WidgetSettingsEvent](index.md) |
| [StartAddWidget](-start-add-widget/index.md) | [main]<br>data object [StartAddWidget](-start-add-widget/index.md) : [WidgetSettingsEvent](index.md) |
