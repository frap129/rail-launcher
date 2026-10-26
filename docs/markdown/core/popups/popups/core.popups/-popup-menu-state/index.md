//[popups](../../../index.md)/[core.popups](../index.md)/[PopupMenuState](index.md)

# PopupMenuState

sealed interface [PopupMenuState](index.md)

Whether a Pop-up menu is showing and, when it is, its target and actions.

#### Inheritors

| |
|---|
| [Closed](-closed/index.md) |
| [Open](-open/index.md) |

## Types

| Name | Summary |
|---|---|
| [Closed](-closed/index.md) | [main]<br>data object [Closed](-closed/index.md) : [PopupMenuState](index.md) |
| [Open](-open/index.md) | [main]<br>data class [Open](-open/index.md)(val target: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PopupMenuItem](../-popup-menu-item/index.md)&gt;, val widgetPage: [PopupWidgetPage](../-popup-widget-page/index.md)? = null) : [PopupMenuState](index.md) |
