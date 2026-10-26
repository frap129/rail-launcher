//[data](../../../index.md)/[core.data.media](../index.md)/[MediaSessionSnapshot](index.md)

# MediaSessionSnapshot

[main]\
data class [MediaSessionSnapshot](index.md)(val key: [MediaSessionKey](../-media-session-key/index.md), val packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val appLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val artist: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, val artwork: [MediaArtwork](../-media-artwork/index.md)?, val state: [PlaybackStatus](../-playback-status/index.md), val actions: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[MediaCommand](../-media-command/index.md)&gt;)

What Rail shows for one active MediaSession.

## Constructors

| | |
|---|---|
| [MediaSessionSnapshot](-media-session-snapshot.md) | [main]<br>constructor(key: [MediaSessionKey](../-media-session-key/index.md), packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), appLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), title: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), artist: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)?, artwork: [MediaArtwork](../-media-artwork/index.md)?, state: [PlaybackStatus](../-playback-status/index.md), actions: [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[MediaCommand](../-media-command/index.md)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [actions](actions.md) | [main]<br>val [actions](actions.md): [Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-set/index.html)&lt;[MediaCommand](../-media-command/index.md)&gt; |
| [appLabel](app-label.md) | [main]<br>val [appLabel](app-label.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [artist](artist.md) | [main]<br>val [artist](artist.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html)? |
| [artwork](artwork.md) | [main]<br>val [artwork](artwork.md): [MediaArtwork](../-media-artwork/index.md)? |
| [key](key.md) | [main]<br>val [key](key.md): [MediaSessionKey](../-media-session-key/index.md) |
| [packageName](package-name.md) | [main]<br>val [packageName](package-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |
| [state](state.md) | [main]<br>val [state](state.md): [PlaybackStatus](../-playback-status/index.md) |
| [title](title.md) | [main]<br>val [title](title.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [isEligible](../-media-player-selector/is-eligible.md) | [main]<br>fun [MediaSessionSnapshot](index.md).[isEligible](../-media-player-selector/is-eligible.md)(showWhenPaused: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) |
