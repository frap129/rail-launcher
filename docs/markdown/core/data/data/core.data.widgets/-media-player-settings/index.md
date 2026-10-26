//[data](../../../index.md)/[core.data.widgets](../index.md)/[MediaPlayerSettings](index.md)

# MediaPlayerSettings

[main]\
data class [MediaPlayerSettings](index.md)(val id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, val enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, val showWhenPaused: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val showArtwork: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, val placement: [MediaPlayerPlacement](../-media-player-placement/index.md) = MediaPlayerPlacement.BELOW_WIDGET_AREA, val excludedPackages: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; = emptySet())

Persisted preferences for the built-in Media Player.

## Constructors

| | |
|---|---|
| [MediaPlayerSettings](-media-player-settings.md) | [main]<br>constructor(id: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0, enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false, showWhenPaused: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, showArtwork: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true, placement: [MediaPlayerPlacement](../-media-player-placement/index.md) = MediaPlayerPlacement.BELOW_WIDGET_AREA, excludedPackages: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; = emptySet()) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |

## Properties

| Name | Summary |
|---|---|
| [enabled](enabled.md) | [main]<br>val [enabled](enabled.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false |
| [excludedPackages](excluded-packages.md) | [main]<br>val [excludedPackages](excluded-packages.md): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)&gt; |
| [id](id.md) | [main]<br>val [id](id.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = 0 |
| [placement](placement.md) | [main]<br>val [placement](placement.md): [MediaPlayerPlacement](../-media-player-placement/index.md) |
| [showArtwork](show-artwork.md) | [main]<br>val [showArtwork](show-artwork.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
| [showWhenPaused](show-when-paused.md) | [main]<br>val [showWhenPaused](show-when-paused.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = true |
