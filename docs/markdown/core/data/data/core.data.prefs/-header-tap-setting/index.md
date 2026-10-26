//[data](../../../index.md)/[core.data.prefs](../index.md)/[HeaderTapSetting](index.md)

# HeaderTapSetting

sealed interface [HeaderTapSetting](index.md)

What a Header line opens when tapped. A null database value stands for [SystemDefault](-system-default/index.md).

#### Inheritors

| |
|---|
| [SystemDefault](-system-default/index.md) |
| [Nothing](-nothing/index.md) |
| [Launchable](-launchable/index.md) |

## Types

| Name | Summary |
|---|---|
| [Launchable](-launchable/index.md) | [main]<br>data class [Launchable](-launchable/index.md)(val key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)) : [HeaderTapSetting](index.md) |
| [Nothing](-nothing/index.md) | [main]<br>data object [Nothing](-nothing/index.md) : [HeaderTapSetting](index.md) |
| [SystemDefault](-system-default/index.md) | [main]<br>data object [SystemDefault](-system-default/index.md) : [HeaderTapSetting](index.md) |

## Functions

| Name | Summary |
|---|---|
| [isStale](../is-stale.md) | [main]<br>fun [HeaderTapSetting](index.md)?.[isStale](../is-stale.md)(validKeys: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt;): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)<br>Reports whether this Tap action points at a Launchable that is not among [validKeys](../is-stale.md). |
