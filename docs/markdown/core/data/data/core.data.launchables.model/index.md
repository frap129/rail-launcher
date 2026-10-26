//[data](../../index.md)/[core.data.launchables.model](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [Launchable](-launchable/index.md) | [main]<br>abstract class [Launchable](-launchable/index.md) |
| [LaunchableAppearance](-launchable-appearance/index.md) | [main]<br>data class [LaunchableAppearance](-launchable-appearance/index.md)(val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val icon: [Icon](../core.data.icons.model/-icon/index.md))<br>The resolved name and icon shown for a Launchable. |
| [LaunchableGroup](-launchable-group/index.md) | [main]<br>sealed interface [LaunchableGroup](-launchable-group/index.md)<br>An entry on the Rail. |
| [RailLaunchable](-rail-launchable/index.md) | [main]<br>data class [RailLaunchable](-rail-launchable/index.md)(val defaultName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val defaultIcon: [Icon](../core.data.icons.model/-icon/index.md), deeplink: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = defaultName, val icon: [Icon](../core.data.icons.model/-icon/index.md) = defaultIcon) : [Launchable](-launchable/index.md) |

## Functions

| Name | Summary |
|---|---|
| [appListKey](app-list-key.md) | [main]<br>fun [LaunchableGroup.Listed](-launchable-group/-listed/index.md).[appListKey](app-list-key.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)<br>A stable identity for the group in the App List. A name starting with a Rail label (e.g. &quot;⌂&quot; or &quot;◇&quot;) forms an Initial group that shares that label, so the key includes the group kind; the Folders key is stable rather than glyph-based. |
