//[widgets](../../../index.md)/[core.widgets](../index.md)/[WidgetEvent](index.md)

# WidgetEvent

sealed interface [WidgetEvent](index.md)

An event the Widget Area or a Pop-up asks the Activity to run.

#### Inheritors

| |
|---|
| [ShowUndo](-show-undo/index.md) |
| [RequestCalendarPermission](-request-calendar-permission/index.md) |
| [ShowCalendarPermissionDenied](-show-calendar-permission-denied/index.md) |
| [NavigateToWidgetSettings](-navigate-to-widget-settings/index.md) |
| [NavigateToMediaPlayerSettings](-navigate-to-media-player-settings/index.md) |
| [EditFavorites](-edit-favorites/index.md) |
| [RequestNotificationAccess](-request-notification-access/index.md) |
| [LaunchBind](-launch-bind/index.md) |
| [LaunchConfigure](-launch-configure/index.md) |
| [LaunchReconfigure](-launch-reconfigure/index.md) |
| [RequestQuietModeOff](-request-quiet-mode-off/index.md) |
| [ShowMessage](-show-message/index.md) |
| [RemoveHostView](-remove-host-view/index.md) |
| [ReportWidgetSize](-report-widget-size/index.md) |
| [ScrollToPlace](-scroll-to-place/index.md) |
| [ReturnToSettings](-return-to-settings/index.md) |

## Types

| Name | Summary |
|---|---|
| [EditFavorites](-edit-favorites/index.md) | [main]<br>data object [EditFavorites](-edit-favorites/index.md) : [WidgetEvent](index.md) |
| [LaunchBind](-launch-bind/index.md) | [main]<br>data class [LaunchBind](-launch-bind/index.md)(val request: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [WidgetEvent](index.md) |
| [LaunchConfigure](-launch-configure/index.md) | [main]<br>data class [LaunchConfigure](-launch-configure/index.md)(val id: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [WidgetEvent](index.md) |
| [LaunchReconfigure](-launch-reconfigure/index.md) | [main]<br>data class [LaunchReconfigure](-launch-reconfigure/index.md)(val id: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [WidgetEvent](index.md) |
| [NavigateToMediaPlayerSettings](-navigate-to-media-player-settings/index.md) | [main]<br>data object [NavigateToMediaPlayerSettings](-navigate-to-media-player-settings/index.md) : [WidgetEvent](index.md) |
| [NavigateToWidgetSettings](-navigate-to-widget-settings/index.md) | [main]<br>data object [NavigateToWidgetSettings](-navigate-to-widget-settings/index.md) : [WidgetEvent](index.md) |
| [RemoveHostView](-remove-host-view/index.md) | [main]<br>data class [RemoveHostView](-remove-host-view/index.md)(val appWidgetId: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [WidgetEvent](index.md) |
| [ReportWidgetSize](-report-widget-size/index.md) | [main]<br>data class [ReportWidgetSize](-report-widget-size/index.md)(val appWidgetIds: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, val widthDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) : [WidgetEvent](index.md) |
| [RequestCalendarPermission](-request-calendar-permission/index.md) | [main]<br>data object [RequestCalendarPermission](-request-calendar-permission/index.md) : [WidgetEvent](index.md) |
| [RequestNotificationAccess](-request-notification-access/index.md) | [main]<br>data object [RequestNotificationAccess](-request-notification-access/index.md) : [WidgetEvent](index.md) |
| [RequestQuietModeOff](-request-quiet-mode-off/index.md) | [main]<br>data class [RequestQuietModeOff](-request-quiet-mode-off/index.md)(val profileSerial: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [WidgetEvent](index.md) |
| [ReturnToSettings](-return-to-settings/index.md) | [main]<br>data object [ReturnToSettings](-return-to-settings/index.md) : [WidgetEvent](index.md) |
| [ScrollToPlace](-scroll-to-place/index.md) | [main]<br>data class [ScrollToPlace](-scroll-to-place/index.md)(val placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [WidgetEvent](index.md) |
| [ShowCalendarPermissionDenied](-show-calendar-permission-denied/index.md) | [main]<br>data class [ShowCalendarPermissionDenied](-show-calendar-permission-denied/index.md)(val permanentlyDenied: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) : [WidgetEvent](index.md) |
| [ShowMessage](-show-message/index.md) | [main]<br>data class [ShowMessage](-show-message/index.md)(val message: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val formatArg: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null) : [WidgetEvent](index.md) |
| [ShowUndo](-show-undo/index.md) | [main]<br>data class [ShowUndo](-show-undo/index.md)(val action: [UndoAction](../-undo-action/index.md)) : [WidgetEvent](index.md) |
