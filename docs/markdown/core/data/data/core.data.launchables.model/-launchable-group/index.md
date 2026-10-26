//[data](../../../index.md)/[core.data.launchables.model](../index.md)/[LaunchableGroup](index.md)

# LaunchableGroup

sealed interface [LaunchableGroup](index.md)

An entry on the Rail.

#### Inheritors

| |
|---|
| [Star](-star/index.md) |
| [Listed](-listed/index.md) |

## Types

| Name | Summary |
|---|---|
| [Builtin](-builtin/index.md) | [main]<br>data class [Builtin](-builtin/index.md)(val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../-launchable/index.md)&gt;) : [LaunchableGroup.Listed](-listed/index.md)<br>Rail's own Launchables, last on the Rail. |
| [Folders](-folders/index.md) | [main]<br>data class [Folders](-folders/index.md)(val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../-launchable/index.md)&gt;) : [LaunchableGroup.Listed](-listed/index.md)<br>Folders the user chose to show in the App List, shown as a real section. |
| [InitialGroup](-initial-group/index.md) | [main]<br>data class [InitialGroup](-initial-group/index.md)(val label: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val items: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../-launchable/index.md)&gt;) : [LaunchableGroup.Listed](-listed/index.md)<br>Launchables sharing a first character; digit-prefixed names share &quot;#&quot;. |
| [Listed](-listed/index.md) | [main]<br>sealed interface [Listed](-listed/index.md) : [LaunchableGroup](index.md)<br>A group shown in the App List. |
| [Star](-star/index.md) | [main]<br>data object [Star](-star/index.md) : [LaunchableGroup](index.md)<br>The Star: first on the Rail, stands for Home. Rail-only, holds no Launchables. |

## Properties

| Name | Summary |
|---|---|
| [label](label.md) | [main]<br>abstract val [label](label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
