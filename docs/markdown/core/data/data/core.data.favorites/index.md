//[data](../../index.md)/[core.data.favorites](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [FavoriteRecord](-favorite-record/index.md) | [main]<br>data class [FavoriteRecord](-favorite-record/index.md)(val key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-string/index.html), val position: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-int/index.html))<br>A stored Favorite: the key of a Launchable and its place in the user's order. |
| [IFavoritesRepository](-i-favorites-repository/index.md) | [main]<br>interface [IFavoritesRepository](-i-favorites-repository/index.md) |
| [IFavoritesRepositoryImpl](-i-favorites-repository-impl/index.md) | [main]<br>class [IFavoritesRepositoryImpl](-i-favorites-repository-impl/index.md)(favoriteDao: [FavoriteDao](../core.data.room.dao/-favorite-dao/index.md), launchableRepository: [ILaunchableRepository](../core.data.launchables/-i-launchable-repository/index.md), scope: <!---  GfmCommand {"@class":"org.jetbrains.dokka.gfm.ResolveLinkGfmCommand","dri":{"packageName":"","classNames":"<Error class: unknown class>","callable":null,"target":{"@class":"org.jetbrains.dokka.links.PointingToDeclaration"},"extra":null}} --->&lt;Error class: unknown class&gt;<!--- --->) : [IFavoritesRepository](-i-favorites-repository/index.md) |
