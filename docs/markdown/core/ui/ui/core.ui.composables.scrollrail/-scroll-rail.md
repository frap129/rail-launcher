//[ui](../../index.md)/[core.ui.composables.scrollrail](index.md)/[ScrollRail](-scroll-rail.md)

# ScrollRail

[androidJvm]\

@[Composable](https://developer.android.com/reference/kotlin/androidx/compose/runtime/Composable.html)

fun [ScrollRail](-scroll-rail.md)(modifier: [Modifier](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier.html) = Modifier, items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;, onScrollStarted: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html), onScroll: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html), onScrollEnded: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))

A composable that creates a column of single character items that behaves like a scrollbar. It animates bending horizontally as the user scrolls on it to ensure that it remains visible while being touched

#### Parameters

androidJvm

| | |
|---|---|
| modifier | the modifier applied to the column |
| scrollRailHelper | an object of  core.ui.composables.scrollrail.ScrollRailHelper |
