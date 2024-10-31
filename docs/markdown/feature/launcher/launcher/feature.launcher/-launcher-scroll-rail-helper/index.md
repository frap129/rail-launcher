//[launcher](../../../index.md)/[feature.launcher](../index.md)/[LauncherScrollRailHelper](index.md)

# LauncherScrollRailHelper

[androidJvm]\
class [LauncherScrollRailHelper](index.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), val railItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html)&gt;, listState: [LazyListState](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState.html), visibilities: [MutableMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html), [MutableState](https://developer.android.com/reference/kotlin/androidx/compose/runtime/MutableState.html)&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt;&gt;) : [ScrollRailHelper](../../../../../core/ui/ui/core.ui.composables.scrollrail/-scroll-rail-helper/index.md)

## Constructors

| | |
|---|---|
| [LauncherScrollRailHelper](-launcher-scroll-rail-helper.md) | [androidJvm]<br>constructor(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), railItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html)&gt;, listState: [LazyListState](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState.html), visibilities: [MutableMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html), [MutableState](https://developer.android.com/reference/kotlin/androidx/compose/runtime/MutableState.html)&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt;&gt;) |

## Properties

| Name | Summary |
|---|---|
| [railItems](rail-items.md) | [androidJvm]<br>open override val [railItems](rail-items.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html)&gt; |

## Functions

| Name | Summary |
|---|---|
| [onScroll](on-scroll.md) | [androidJvm]<br>open suspend override fun [onScroll](on-scroll.md)(currIndex: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), prevIndex: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [onScrollEnded](on-scroll-ended.md) | [androidJvm]<br>open suspend override fun [onScrollEnded](on-scroll-ended.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
| [onScrollStarted](on-scroll-started.md) | [androidJvm]<br>open suspend override fun [onScrollStarted](on-scroll-started.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) |
