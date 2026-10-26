//[data](../../../index.md)/[core.data.media](../index.md)/[MediaArtworkStore](index.md)

# MediaArtworkStore

[main]\
class [MediaArtworkStore](index.md)(maxEntries: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = MAX_ENTRIES)

Holds the most recent MediaSession artwork in memory so the UI never receives a Bitmap through state. Bounded because albums change often.

## Constructors

| | |
|---|---|
| [MediaArtworkStore](-media-artwork-store.md) | [main]<br>constructor(maxEntries: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html) = MAX_ENTRIES) |

## Types

| Name | Summary |
|---|---|
| [Companion](-companion/index.md) | [main]<br>object [Companion](-companion/index.md) |
| [Entry](-entry/index.md) | [main]<br>class [Entry](-entry/index.md)(val bitmap: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->?, val uri: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->?) |

## Functions

| Name | Summary |
|---|---|
| [get](get.md) | [main]<br>fun [get](get.md)(artwork: [MediaArtwork](../-media-artwork/index.md)): [MediaArtworkStore.Entry](-entry/index.md)? |
| [put](put.md) | [main]<br>fun [put](put.md)(key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), bitmap: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->?, uri: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->?): [MediaArtwork](../-media-artwork/index.md)? |
