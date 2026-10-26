//[data](../../../index.md)/[core.data.launchables](../index.md)/[LaunchableState](index.md)

# LaunchableState

[main]\
data class [LaunchableState](index.md)(val groups: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup](../../core.data.launchables.model/-launchable-group/index.md)&gt;, val launchables: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt;, val isLoaded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html))

The Rail groups, the App List groups, every Launchable, and whether the installed apps have loaded.

## Constructors

| | |
|---|---|
| [LaunchableState](-launchable-state.md) | [main]<br>constructor(groups: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup](../../core.data.launchables.model/-launchable-group/index.md)&gt;, launchables: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt;, isLoaded: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)) |

## Properties

| Name | Summary |
|---|---|
| [groups](groups.md) | [main]<br>val [groups](groups.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup](../../core.data.launchables.model/-launchable-group/index.md)&gt; |
| [isLoaded](is-loaded.md) | [main]<br>val [isLoaded](is-loaded.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
| [launchables](launchables.md) | [main]<br>val [launchables](launchables.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[Launchable](../../core.data.launchables.model/-launchable/index.md)&gt; |
| [listedGroups](listed-groups.md) | [main]<br>val [listedGroups](listed-groups.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[LaunchableGroup.Listed](../../core.data.launchables.model/-launchable-group/-listed/index.md)&gt;<br>The groups shown in the App List, in Rail order. |
