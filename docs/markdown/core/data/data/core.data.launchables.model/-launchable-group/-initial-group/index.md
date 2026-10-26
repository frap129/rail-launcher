//[data](../../../../index.md)/[core.data.launchables.model](../../index.md)/[LaunchableGroup](../index.md)/[InitialGroup](index.md)

# InitialGroup

[main]\
data class [InitialGroup](index.md)(val label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../-launchable/index.md)&gt;) : [LaunchableGroup.Listed](../-listed/index.md)

Launchables sharing a first character; digit-prefixed names share &quot;#&quot;.

## Constructors

| | |
|---|---|
| [InitialGroup](-initial-group.md) | [main]<br>constructor(label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../-launchable/index.md)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [items](items.md) | [main]<br>open override val [items](items.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../-launchable/index.md)&gt; |
| [label](label.md) | [main]<br>open override val [label](label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [appListKey](../../app-list-key.md) | [main]<br>fun [LaunchableGroup.Listed](../-listed/index.md).[appListKey](../../app-list-key.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)<br>A stable identity for the group in the App List. A name starting with a Rail label (e.g. &quot;⌂&quot; or &quot;◇&quot;) forms an Initial group that shares that label, so the key includes the group kind; the Folders key is stable rather than glyph-based. |
| [withItems](with-items.md) | [main]<br>open override fun [withItems](with-items.md)(items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../-launchable/index.md)&gt;): [LaunchableGroup.Listed](../-listed/index.md)<br>A copy of this group holding [items](with-items.md). |
