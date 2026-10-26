//[launcher](../../../index.md)/[feature.launcher](../index.md)/[LauncherUiState](index.md)

# LauncherUiState

sealed class [LauncherUiState](index.md)

#### Inheritors

| |
|---|
| [Loading](-loading/index.md) |
| [Home](-home/index.md) |
| [AppList](-app-list/index.md) |

## Types

| Name | Summary |
|---|---|
| [AppList](-app-list/index.md) | [main]<br>sealed class [AppList](-app-list/index.md) : [LauncherUiState](index.md) |
| [Home](-home/index.md) | [main]<br>data class [Home](-home/index.md)(val lazyListState: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->, val favorites: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin.collections/-list/index.html)&lt;<!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->&gt;, val isEditingFavorites: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-boolean/index.html) = false) : [LauncherUiState](index.md) |
| [Loading](-loading/index.md) | [main]<br>data object [Loading](-loading/index.md) : [LauncherUiState](index.md) |
