//[popups](../../../../index.md)/[core.popups](../../index.md)/[PopupUiState](../index.md)/[Open](index.md)

# Open

[main]\
data class [Open](index.md)(val owner: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val anchor: [PopupAnchor](../../-popup-anchor/index.md)?, val content: [PopupContent](../../-popup-content/index.md), val widget: [PopupWidgetState](../../-popup-widget-state/index.md)? = null, val nowMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) : [PopupUiState](../index.md)

## Constructors

| | |
|---|---|
| [Open](-open.md) | [main]<br>constructor(owner: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, anchor: [PopupAnchor](../../-popup-anchor/index.md)?, content: [PopupContent](../../-popup-content/index.md), widget: [PopupWidgetState](../../-popup-widget-state/index.md)? = null, nowMillis: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [anchor](anchor.md) | [main]<br>val [anchor](anchor.md): [PopupAnchor](../../-popup-anchor/index.md)?<br>The row the card is anchored to; null while a reopened card waits for its row to lay out. |
| [content](content.md) | [main]<br>val [content](content.md): [PopupContent](../../-popup-content/index.md) |
| [nowMillis](now-millis.md) | [main]<br>val [nowMillis](now-millis.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)<br>The clock reading notification ages are measured against. |
| [owner](owner.md) | [main]<br>val [owner](owner.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |
| [widget](widget.md) | [main]<br>val [widget](widget.md): [PopupWidgetState](../../-popup-widget-state/index.md)? = null |
