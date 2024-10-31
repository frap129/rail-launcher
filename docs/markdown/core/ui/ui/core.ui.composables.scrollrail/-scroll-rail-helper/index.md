//[ui](../../../index.md)/[core.ui.composables.scrollrail](../index.md)/[ScrollRailHelper](index.md)

# ScrollRailHelper

[androidJvm]\
interface [ScrollRailHelper](index.md)

A helper for using [core.ui.composables.scrollrail.ScrollRail](../-scroll-rail.md). This interface provides callbacks for handling scrolling.

## Properties

| Name | Summary |
|---|---|
| [railItems](rail-items.md) | [androidJvm]<br>abstract val [railItems](rail-items.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Char](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-char/index.html)&gt;<br>the list of items on the [core.ui.composables.scrollrail.ScrollRail](../-scroll-rail.md) |

## Functions

| Name | Summary |
|---|---|
| [onScroll](on-scroll.md) | [androidJvm]<br>abstract suspend fun [onScroll](on-scroll.md)(currIndex: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), prevIndex: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Called when a user scrolls along the [core.ui.composables.scrollrail.ScrollRail](../-scroll-rail.md) |
| [onScrollEnded](on-scroll-ended.md) | [androidJvm]<br>abstract suspend fun [onScrollEnded](on-scroll-ended.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Called when scrolling on the [core.ui.composables.scrollrail.ScrollRail](../-scroll-rail.md) ends, either because the user has stopped touching the rail, or the input has been interrupted. |
| [onScrollStarted](on-scroll-started.md) | [androidJvm]<br>abstract suspend fun [onScrollStarted](on-scroll-started.md)(index: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html))<br>Called when a user initially touches the [core.ui.composables.scrollrail.ScrollRail](../-scroll-rail.md) |
