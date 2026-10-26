//[popups](../../../index.md)/[core.popups](../index.md)/[PopupUiState](index.md)

# PopupUiState

sealed interface [PopupUiState](index.md)

Whether a Pop-up is showing and, when it is, its owner and content.

#### Inheritors

| |
|---|
| [Closed](-closed/index.md) |
| [Open](-open/index.md) |

## Types

| Name | Summary |
|---|---|
| [Closed](-closed/index.md) | [main]<br>data object [Closed](-closed/index.md) : [PopupUiState](index.md) |
| [Open](-open/index.md) | [main]<br>data class [Open](-open/index.md)(val owner: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val anchor: [PopupAnchor](../-popup-anchor/index.md)?, val content: [PopupContent](../-popup-content/index.md), val widget: [PopupWidgetState](../-popup-widget-state/index.md)? = null, val nowMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [PopupUiState](index.md) |
