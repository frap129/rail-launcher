//[data](../../../index.md)/[core.data.shortcuts](../index.md)/[ShortcutsResult](index.md)

# ShortcutsResult

sealed interface [ShortcutsResult](index.md)

What an app's shortcuts currently look like.

#### Inheritors

| |
|---|
| [Available](-available/index.md) |
| [Unavailable](-unavailable/index.md) |
| [ProfilePaused](-profile-paused/index.md) |

## Types

| Name | Summary |
|---|---|
| [Available](-available/index.md) | [main]<br>data class [Available](-available/index.md)(val shortcuts: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;[ShortcutLaunchable](../-shortcut-launchable/index.md)&gt;) : [ShortcutsResult](index.md)<br>The app's enabled shortcuts in display order. |
| [ProfilePaused](-profile-paused/index.md) | [main]<br>data object [ProfilePaused](-profile-paused/index.md) : [ShortcutsResult](index.md)<br>The app's work profile is paused. |
| [Unavailable](-unavailable/index.md) | [main]<br>data object [Unavailable](-unavailable/index.md) : [ShortcutsResult](index.md)<br>Rail is not the default launcher, so the system does not expose shortcuts. |
