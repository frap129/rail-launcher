//[launcher](../../../index.md)/[feature.launcher.widgets](../index.md)/[WidgetMenuState](index.md)

# WidgetMenuState

sealed interface [WidgetMenuState](index.md)

The long-press menu currently shown on Home.

#### Inheritors

| |
|---|
| [Closed](-closed/index.md) |
| [HeaderMenu](-header-menu/index.md) |
| [EmptyAreaMenu](-empty-area-menu/index.md) |
| [AppWidgetMenu](-app-widget-menu/index.md) |
| [MediaPlayerMenu](-media-player-menu/index.md) |
| [ClockStyleChooser](-clock-style-chooser/index.md) |

## Types

| Name | Summary |
|---|---|
| [AppWidgetMenu](-app-widget-menu/index.md) | [main]<br>data class [AppWidgetMenu](-app-widget-menu/index.md)(val widgetId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val appWidgetId: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val icon: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[WidgetMenuItem](../-widget-menu-item/index.md)&gt;) : [WidgetMenuState](index.md) |
| [ClockStyleChooser](-clock-style-chooser/index.md) | [main]<br>data class [ClockStyleChooser](-clock-style-chooser/index.md)(val selected: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val previews: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;) : [WidgetMenuState](index.md) |
| [Closed](-closed/index.md) | [main]<br>data object [Closed](-closed/index.md) : [WidgetMenuState](index.md) |
| [EmptyAreaMenu](-empty-area-menu/index.md) | [main]<br>data class [EmptyAreaMenu](-empty-area-menu/index.md)(val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[WidgetMenuItem](../-widget-menu-item/index.md)&gt;) : [WidgetMenuState](index.md) |
| [HeaderMenu](-header-menu/index.md) | [main]<br>data class [HeaderMenu](-header-menu/index.md)(val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[WidgetMenuItem](../-widget-menu-item/index.md)&gt;) : [WidgetMenuState](index.md) |
| [MediaPlayerMenu](-media-player-menu/index.md) | [main]<br>data class [MediaPlayerMenu](-media-player-menu/index.md)(val icon: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[WidgetMenuItem](../-widget-menu-item/index.md)&gt;) : [WidgetMenuState](index.md) |
