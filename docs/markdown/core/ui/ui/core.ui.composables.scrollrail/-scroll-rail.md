//[ui](../../index.md)/[core.ui.composables.scrollrail](index.md)/[ScrollRail](-scroll-rail.md)

# ScrollRail

[main]\
fun [ScrollRail](-scroll-rail.md)(modifier: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- ---> = Modifier, items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;, onScroll: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), onScrollEnded: ([Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html)) -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-unit/index.html), enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true)

A column of single character items along the screen edge that the user can scrub through. It animates bending horizontally as the user drags on it to ensure that it remains visible while being touched.

Pointer positions are only read during layout, so scrubbing does not recompose the Rail. A single animated pointer position drives the bend of every item, and an animated strength bends the Rail in and out when a scrub starts and ends.

#### Parameters

main

| | |
|---|---|
| modifier | the modifier applied to the row |
| items | the labels shown on the Rail, top to bottom |
| onScroll | invoked when a scroll starts or the pointer crosses into another item |
| onScrollEnded | invoked with the item under the pointer when the scroll ends |
