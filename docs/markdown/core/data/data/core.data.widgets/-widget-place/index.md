//[data](../../../index.md)/[core.data.widgets](../index.md)/[WidgetPlace](index.md)

# WidgetPlace

[main]\
data class [WidgetPlace](index.md)(val id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, val currentPage: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val widgets: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PlacedWidget](../-placed-widget/index.md)&gt;, val ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null)

An ordered slot in the Widget Area or a Pop-up. A place holds one Widget or a Widget Stack.

## Constructors

| | |
|---|---|
| [WidgetPlace](-widget-place.md) | [main]<br>constructor(id: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, currentPage: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), widgets: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PlacedWidget](../-placed-widget/index.md)&gt;, ownerKey: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null) |

## Properties

| Name | Summary |
|---|---|
| [currentPage](current-page.md) | [main]<br>val [currentPage](current-page.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |
| [heightDp](height-dp.md) | [main]<br>val [heightDp](height-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? |
| [id](id.md) | [main]<br>val [id](id.md): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html) |
| [isStack](is-stack.md) | [main]<br>val [isStack](is-stack.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>True when this place pages sideways as a Widget Stack. |
| [ownerKey](owner-key.md) | [main]<br>val [ownerKey](owner-key.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? = null<br>The Pop-up owning this place, or null when it belongs to the Widget Area. |
| [widgets](widgets.md) | [main]<br>val [widgets](widgets.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[PlacedWidget](../-placed-widget/index.md)&gt; |
| [widthMode](width-mode.md) | [main]<br>val [widthMode](width-mode.md): [WidgetWidth](../-widget-width/index.md)<br>How wide this place is laid out; only a lone App Widget can extend under the Rail. |

## Functions

| Name | Summary |
|---|---|
| [canGrowStack](../can-grow-stack.md) | [main]<br>fun [WidgetPlace](index.md).[canGrowStack](../can-grow-stack.md)(maxStackPages: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), additionalPages: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 1): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Whether the place can take [additionalPages](../can-grow-stack.md) more Stack pages within [maxStackPages](../can-grow-stack.md). |
