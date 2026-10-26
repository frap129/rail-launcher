//[data](../../../index.md)/[core.data.widgets](../index.md)/[ResizeBounds](index.md)

# ResizeBounds

[main]\
data class [ResizeBounds](index.md)(val minDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), val maxDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, val isResizable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = maxDp == null || maxDp &gt; minDp)

The vertical height range an App Widget can be resized to.

## Constructors

| | |
|---|---|
| [ResizeBounds](-resize-bounds.md) | [main]<br>constructor(minDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), maxDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)?, isResizable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = maxDp == null || maxDp &gt; minDp) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [isResizable](is-resizable.md) | [main]<br>val [isResizable](is-resizable.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [maxDp](max-dp.md) | [main]<br>val [maxDp](max-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)? |
| [minDp](min-dp.md) | [main]<br>val [minDp](min-dp.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) |

## Functions

| Name | Summary |
|---|---|
| [clamp](clamp.md) | [main]<br>fun [clamp](clamp.md)(heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html), homeHeightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)<br>Clamps [heightDp](clamp.md) to this range and the safe Home height. |
| [snapToStepGrid](snap-to-step-grid.md) | [main]<br>fun [snapToStepGrid](snap-to-step-grid.md)(heightDp: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)<br>Rounds [heightDp](snap-to-step-grid.md) to the 8dp resize grid; callers clamp the result to the bounds. |
