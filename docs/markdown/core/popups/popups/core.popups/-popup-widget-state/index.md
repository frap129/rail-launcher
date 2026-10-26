//[popups](../../../index.md)/[core.popups](../index.md)/[PopupWidgetState](index.md)

# PopupWidgetState

[main]\
data class [PopupWidgetState](index.md)(val placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val pages: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PopupWidgetPage](../-popup-widget-page/index.md)&gt;, val currentPage: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val bounds: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val isResizable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), val indicatorVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))

The App Widget place at the top of a Pop-up, holding one widget or a Widget Stack.

## Constructors

| | |
|---|---|
| [PopupWidgetState](-popup-widget-state.md) | [main]<br>constructor(placeId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), pages: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PopupWidgetPage](../-popup-widget-page/index.md)&gt;, currentPage: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), bounds: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, isResizable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html), indicatorVisible: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [bounds](bounds.md) | [main]<br>val [bounds](bounds.md): <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> |
| [currentPage](current-page.md) | [main]<br>val [currentPage](current-page.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [heightDp](height-dp.md) | [main]<br>val [heightDp](height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [indicatorVisible](indicator-visible.md) | [main]<br>val [indicatorVisible](indicator-visible.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isResizable](is-resizable.md) | [main]<br>val [isResizable](is-resizable.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [isStack](is-stack.md) | [main]<br>val [isStack](is-stack.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>True when the place pages sideways as a Widget Stack. |
| [ownerKey](owner-key.md) | [main]<br>val [ownerKey](owner-key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [pages](pages.md) | [main]<br>val [pages](pages.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PopupWidgetPage](../-popup-widget-page/index.md)&gt; |
| [placeId](place-id.md) | [main]<br>val [placeId](place-id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |

## Functions

| Name | Summary |
|---|---|
| [currentPageOrNull](current-page-or-null.md) | [main]<br>fun [currentPageOrNull](current-page-or-null.md)(): [PopupWidgetPage](../-popup-widget-page/index.md)?<br>The Stack page the pager settled on. |
| [pageOf](page-of.md) | [main]<br>fun [pageOf](page-of.md)(widgetId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)): [PopupWidgetPage](../-popup-widget-page/index.md)?<br>The Stack page holding [widgetId](page-of.md), if any. |
