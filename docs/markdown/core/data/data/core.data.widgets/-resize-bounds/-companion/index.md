//[data](../../../../index.md)/[core.data.widgets](../../index.md)/[ResizeBounds](../index.md)/[Companion](index.md)

# Companion

[main]\
object [Companion](index.md)

## Properties

| Name | Summary |
|---|---|
| [MIN_HEIGHT_DP](-m-i-n_-h-e-i-g-h-t_-d-p.md) | [main]<br>const val [MIN_HEIGHT_DP](-m-i-n_-h-e-i-g-h-t_-d-p.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 48 |
| [STACK_INDICATOR_HEIGHT_DP](-s-t-a-c-k_-i-n-d-i-c-a-t-o-r_-h-e-i-g-h-t_-d-p.md) | [main]<br>const val [STACK_INDICATOR_HEIGHT_DP](-s-t-a-c-k_-i-n-d-i-c-a-t-o-r_-h-e-i-g-h-t_-d-p.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 12 |
| [STEP_DP](-s-t-e-p_-d-p.md) | [main]<br>const val [STEP_DP](-s-t-e-p_-d-p.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 8 |

## Functions

| Name | Summary |
|---|---|
| [from](from.md) | [main]<br>fun [from](from.md)(info: [WidgetProviderInfo](../../-widget-provider-info/index.md)): [ResizeBounds](../index.md)<br>Builds the bounds from the provider info; a non-vertical or inverted range is not resizable. |
| [stackBounds](stack-bounds.md) | [main]<br>fun [stackBounds](stack-bounds.md)(pages: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[ResizeBounds](../index.md)&gt;): [ResizeBounds](../index.md)<br>The intersection of every page's bounds; an empty intersection is non-resizable at the highest minimum. |
