//[widgets](../../../index.md)/[core.widgets](../index.md)/[UndoAction](index.md)

# UndoAction

sealed interface [UndoAction](index.md)

How the Widget Area or a Pop-up undoes a removal.

#### Inheritors

| |
|---|
| [RemoveHeader](-remove-header/index.md) |
| [ReplaceHeader](-replace-header/index.md) |
| [IncludeMediaApp](-include-media-app/index.md) |

## Types

| Name | Summary |
|---|---|
| [IncludeMediaApp](-include-media-app/index.md) | [main]<br>data class [IncludeMediaApp](-include-media-app/index.md)(val packageName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val appLabel: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html) = packageName) : [UndoAction](index.md) |
| [RemoveHeader](-remove-header/index.md) | [main]<br>data class [RemoveHeader](-remove-header/index.md)(val removal: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [UndoAction](index.md) |
| [ReplaceHeader](-replace-header/index.md) | [main]<br>data class [ReplaceHeader](-replace-header/index.md)(val undo: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [UndoAction](index.md) |
